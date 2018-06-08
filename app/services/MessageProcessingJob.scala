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

import cats.data.EitherT
import cats.implicits._
import com.kenshoo.play.metrics.Metrics
import javax.inject.Inject
import model._
import play.api.Logger
import uk.gov.hmrc.http.logging.LoggingDetails
import util.logging.LoggingDetails
import util.logging.WithLoggingDetails.withLoggingDetails

import scala.concurrent.{ExecutionContext, Future}

case class MessageContext(ld: LoggingDetails)

case class ExceptionWithContext(e: Exception, context: Option[MessageContext])

trait MessageProcessingJob extends PollingJob {

  implicit def executionContext: ExecutionContext

  def consumer: QueueConsumer

  def processMessage(message: Message): EitherT[Future, ExceptionWithContext, MessageContext]

  def run(): Future[Unit] = {
    val outcomes = for {
      messages        <- consumer.poll()
      messageOutcomes <- Future.sequence { messages.map(handleMessage) }
    } yield messageOutcomes

    outcomes.map(_ => ())
  }

  private def handleMessage(message: Message): Future[Unit] = {
    val outcome: EitherT[Future, ExceptionWithContext, Unit] = for {
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

  def toEitherT[T](f: Future[T], context: Option[MessageContext] = None): EitherT[Future, ExceptionWithContext, T] = {
    val futureEither: Future[Either[ExceptionWithContext, T]] =
      f.map(Right(_))
        .recover { case error: Exception => Left(ExceptionWithContext(error, context)) }
    EitherT(futureEither)
  }

}

trait SuccessfulQueueConsumer extends QueueConsumer
trait QuarantineQueueConsumer extends QueueConsumer

class NotifyOnSuccessfulFileUploadMessageProcessingJob @Inject()(
  val consumer: SuccessfulQueueConsumer,
  parser: MessageParser,
  fileRetriever: FileNotificationDetailsRetriever,
  notificationService: NotificationService,
  metrics: Metrics,
  clock: Clock)(implicit val executionContext: ExecutionContext)
    extends MessageProcessingJob {

  override def processMessage(message: Message): EitherT[Future, ExceptionWithContext, MessageContext] =
    for {
      parsedMessage <- toEitherT(parser.parse(message))
      context = MessageContext(LoggingDetails.fromS3ObjectLocation(parsedMessage.location))
      notification <- toEitherT(fileRetriever.retrieveUploadedFileDetails(parsedMessage.location), Some(context))
      _ = collectMetricsBeforeNotification(notification)
      _ <- toEitherT(notificationService.notifySuccessfulCallback(notification), Some(context))
    } yield {
      collectMetricsAfterNotification(notification)
      context
    }

  private def collectMetricsBeforeNotification(notification: UploadedFile): Unit = {
    val totalProcessingTime = Duration.between(notification.uploadDetails.uploadTimestamp, clock.instant())
    if (totalProcessingTime.isNegative) {
      Logger.warn(
        "File processing time is negative, it might be caused by clocks out of sync, ignoring the measurement")
    } else {
      metrics.defaultRegistry
        .timer("fileProcessingTimeExcludingNotification")
        .update(totalProcessingTime.toNanos, TimeUnit.NANOSECONDS)
    }
  }

  private def collectMetricsAfterNotification(notification: UploadedFile): Unit = {
    val totalProcessingTime = Duration.between(notification.uploadDetails.uploadTimestamp, clock.instant())
    if (totalProcessingTime.isNegative) {
      Logger.warn(
        "File processing time is negative, it might be caused by clocks out of sync, ignoring the measurement")
    } else {
      metrics.defaultRegistry
        .timer("totalFileProcessingTime")
        .update(totalProcessingTime.toNanos, TimeUnit.NANOSECONDS)
    }
    metrics.defaultRegistry.histogram("fileSize").update(notification.size)
    metrics.defaultRegistry.counter("successfulUploadNotificationSent").inc()
  }
}

class NotifyOnQuarantineFileUploadMessageProcessingJob @Inject()(
  val consumer: QuarantineQueueConsumer,
  parser: MessageParser,
  fileRetriever: FileNotificationDetailsRetriever,
  notificationService: NotificationService,
  metrics: Metrics
)(implicit val executionContext: ExecutionContext)
    extends MessageProcessingJob {

  override def processMessage(message: Message): EitherT[Future, ExceptionWithContext, MessageContext] =
    for {
      parsedMessage <- toEitherT(parser.parse(message))
      context = MessageContext(LoggingDetails.fromS3ObjectLocation(parsedMessage.location))
      notification <- toEitherT(
                       fileRetriever.retrieveQuarantinedFileDetails(parsedMessage.location),
                       Some(context)
                     )
      _ <- toEitherT(notificationService.notifyFailedCallback(notification), Some(context))
    } yield {
      metrics.defaultRegistry.counter("quarantinedUploadNotificationSent").inc()
      context
    }
}
