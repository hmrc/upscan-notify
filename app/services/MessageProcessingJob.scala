/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package services

import java.time.{Clock, Duration}
import java.util.concurrent.TimeUnit

import cats.MonadError
import cats.data.EitherT
import cats.implicits._
import com.kenshoo.play.metrics.Metrics
import config.ServiceConfiguration
import javax.inject.Inject
import model._
import play.api.{Logger, LoggerLike}
import uk.gov.hmrc.http.logging.LoggingDetails
import util.logging.LoggingDetails
import util.logging.WithLoggingDetails.withLoggingDetails

case class MessageContext(ld: LoggingDetails)

case class ExceptionWithContext(e: Exception, context: Option[MessageContext])

trait MessageProcessingJob[F[_]] extends PollingJob[F] {

  implicit def monadError: MonadError[F, Throwable]

  def consumer: QueueConsumer[F]

  def processMessage(message: Message): EitherT[F, ExceptionWithContext, MessageContext]

  def run(): F[Unit] = {
    val outcomes = for {
      messages        <- consumer.poll()
      messageOutcomes <- messages.toList.traverse(handleMessage)
    } yield messageOutcomes

    outcomes.map(_ => ())
  }

  private def handleMessage(message: Message): F[Unit] = {
    val outcome: EitherT[F, ExceptionWithContext, Unit] = for {
      context <- processMessage(message)
      _       <- toEitherT(consumer.confirm(message), context = Some(context))
    } yield ()

    outcome.value.map {
      case Left(ExceptionWithContext(exception, Some(context))) =>
        withLoggingDetails(context.ld) {
          Logger.error(
            s"Failed to process message '${message.id}' for file '${context.ld.mdcData
              .getOrElse("file-reference", "???")}', cause ${exception.getMessage}",
            exception
          )
        }
      case Left(ExceptionWithContext(exception, None)) =>
        Logger.error(s"Failed to process message '${message.id}', cause ${exception.getMessage}", exception)
      case Right(_) =>
        ()
    }
  }

  def toEitherT[T](f: F[T], context: Option[MessageContext] = None): EitherT[F, ExceptionWithContext, T] = {
    val fEither: F[Either[ExceptionWithContext, T]] =
      monadError.handleError[Either[ExceptionWithContext, T]](f.map(Right(_))) {
        case error: Exception => Left(ExceptionWithContext(error, context))
      }
    EitherT(fEither)
  }
}

trait SuccessfulQueueConsumer[F[_]] extends QueueConsumer[F]
trait QuarantineQueueConsumer[F[_]] extends QueueConsumer[F]

class NotifyOnSuccessfulFileUploadMessageProcessingJob[F[_]] @Inject()(
  override val consumer: SuccessfulQueueConsumer[F],
  parser: MessageParser[F],
  fileRetriever: FileNotificationDetailsRetriever[F],
  notificationService: NotificationService[F],
  metrics: Metrics,
  clock: Clock,
  upscanAuditingService: UpscanAuditingService,
  serviceConfiguration: ServiceConfiguration
)(implicit val monadError: MonadError[F, Throwable])
    extends MessageProcessingJob[F] {

  private val timingsLogger = Logger(classOf[NotifyOnSuccessfulFileUploadMessageProcessingJob[F]])

  override def processMessage(message: Message): EitherT[F, ExceptionWithContext, MessageContext] =
    for {
      parsedMessage <- toEitherT(parser.parse(message))
      context = MessageContext(LoggingDetails.fromS3ObjectLocation(parsedMessage.location))
      notification1 <- toEitherT(fileRetriever.retrieveUploadedFileDetails(parsedMessage.location), Some(context))
      notification2 = notification1.copyWithUserMetadata(
        "x-amz-meta-upscan-notify-received" -> message.receivedAt.toString)
      _ = upscanAuditingService.notifyFileUploadedSuccessfully(notification2)
      _ = collectMetricsBeforeNotification(notification2)
      notification3 <- toEitherT(notificationService.notifySuccessfulCallback(notification2), Some(context))
    } yield {
      collectMetricsAfterNotification(notification3, timingsLogger)
      context
    }

  private def collectMetricsBeforeNotification(notification: UploadedFile): Unit = {
    val totalProcessingTime = Duration.between(notification.uploadDetails.uploadTimestamp, clock.instant())

    if (totalProcessingTime.isNegative) {
      Logger.warn(
        "File processing time is negative, it might be caused by clocks out of sync, ignoring the measurement"
      )
    } else {
      metrics.defaultRegistry
        .timer("fileProcessingTimeExcludingNotification")
        .update(totalProcessingTime.toNanos, TimeUnit.NANOSECONDS)
    }
  }

  private[services] def collectMetricsAfterNotification(notification: UploadedFile, perfLogger: LoggerLike): Unit = {
    val respondedAt = clock.instant()

    val updatedNotification = notification.copyWithUserMetadata(
      ("x-amz-meta-upscan-notify-responded" -> respondedAt.toString)
    )

    val totalProcessingTime = Duration.between(updatedNotification.uploadDetails.uploadTimestamp, respondedAt)

    if (totalProcessingTime.isNegative) {
      Logger.warn(
        "File processing time is negative, it might be caused by clocks out of sync, ignoring the measurement")
    } else {
      metrics.defaultRegistry
        .timer("totalFileProcessingTime")
        .update(totalProcessingTime.toNanos, TimeUnit.NANOSECONDS)

      val endToEndProcessingThreshold: scala.concurrent.duration.Duration =
        serviceConfiguration.endToEndProcessingThreshold()

      if (totalProcessingTime.toMillis() > endToEndProcessingThreshold.toMillis) {

        val checkpoints = updatedNotification.checkpoints().toSeq.sorted.mkString("[", ", ", "]")

        perfLogger.warn(
          s"""Accepted file total processing time: [${totalProcessingTime.getSeconds} seconds] exceeded threshold of [$endToEndProcessingThreshold].
             |Processing checkpoints were: $checkpoints.
           """.stripMargin)
      }
    }

    metrics.defaultRegistry.histogram("fileSize").update(updatedNotification.size)
    metrics.defaultRegistry.counter("successfulUploadNotificationSent").inc()
  }
}

class NotifyOnQuarantineFileUploadMessageProcessingJob[F[_]] @Inject()(
  override val consumer: QuarantineQueueConsumer[F],
  parser: MessageParser[F],
  fileRetriever: FileNotificationDetailsRetriever[F],
  notificationService: NotificationService[F],
  metrics: Metrics,
  clock: Clock,
  upscanAuditingService: UpscanAuditingService,
  serviceConfiguration: ServiceConfiguration
)(implicit val monadError: MonadError[F, Throwable])
    extends MessageProcessingJob[F] {

  private val timingsLogger = Logger(classOf[NotifyOnQuarantineFileUploadMessageProcessingJob[F]])

  override def processMessage(message: Message): EitherT[F, ExceptionWithContext, MessageContext] =
    for {
      parsedMessage <- toEitherT(parser.parse(message))
      context = MessageContext(LoggingDetails.fromS3ObjectLocation(parsedMessage.location))
      notification1 <- toEitherT(
                        fileRetriever.retrieveQuarantinedFileDetails(parsedMessage.location),
                        Some(context)
                      )
      notification2 = notification1.copyWithUserMetadata(
        "x-amz-meta-upscan-notify-received" -> message.receivedAt.toString)
      _ = upscanAuditingService.notifyFileIsQuarantined(notification2)
      notification3 <- toEitherT(notificationService.notifyFailedCallback(notification2), Some(context))
    } yield {
      collectMetricsAfterNotification(notification3, timingsLogger)
      context
    }

  private[services] def collectMetricsAfterNotification(notification: QuarantinedFile, perfLogger: LoggerLike): Unit = {
    val respondedAt = clock.instant()

    val updatedNotification = notification.copyWithUserMetadata(
      ("x-amz-meta-upscan-notify-responded" -> respondedAt.toString)
    )

    val totalProcessingTime = Duration.between(updatedNotification.uploadDetails.uploadTimestamp, respondedAt)

    if (totalProcessingTime.isNegative) {
      Logger.warn(
        "File processing time is negative, it might be caused by clocks out of sync, ignoring the measurement")
    } else {
      metrics.defaultRegistry.counter("quarantinedUploadNotificationSent").inc()

      val endToEndProcessingThreshold: scala.concurrent.duration.Duration =
        serviceConfiguration.endToEndProcessingThreshold()

      if (totalProcessingTime.toMillis() > endToEndProcessingThreshold.toMillis) {

        val checkpoints = updatedNotification.checkpoints().toSeq.sorted.mkString("[", ", ", "]")

        perfLogger.warn(
          s"""Rejected file total processing time: x[${totalProcessingTime.getSeconds} seconds] exceeded threshold of [$endToEndProcessingThreshold].
             |Processing checkpoints were: $checkpoints.
           """.stripMargin)
      }
    }
  }
}
