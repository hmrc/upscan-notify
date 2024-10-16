/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.upscannotify.service

import cats.data.EitherT
import cats.implicits._
import play.api.LoggerLike
import play.api.Logger
import uk.gov.hmrc.http.logging.LoggingDetails
import uk.gov.hmrc.play.bootstrap.metrics.Metrics
import uk.gov.hmrc.upscannotify.config.ServiceConfiguration
import uk.gov.hmrc.upscannotify.model._
import uk.gov.hmrc.upscannotify.util.logging.LoggingDetails
import uk.gov.hmrc.upscannotify.util.logging.WithLoggingDetails.withLoggingDetails

import java.time.{Clock, Duration}
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

case class MessageContext(
  ld: LoggingDetails
)

case class ExceptionWithContext(
  e      : Exception,
  context: Option[MessageContext]
)

trait MessageProcessingJob extends PollingJob:
  private[service] val logger: LoggerLike

  def executionContext: ExecutionContext
  private given ExecutionContext = executionContext

  def consumer: QueueConsumer

  def processMessage(message: Message): EitherT[Future, ExceptionWithContext, MessageContext]

  def run(): Future[Unit] =
     for
       messages <- consumer.poll()
       _        <- Future.traverse(messages)(handleMessage)
     yield ()

  private def handleMessage(message: Message): Future[Unit] =
    val outcome: EitherT[Future, ExceptionWithContext, Unit] =
      for
        context <- processMessage(message)
        _       <- toEitherT(consumer.confirm(message), context = Some(context))
      yield ()

    outcome.value.map:
      case Left(ExceptionWithContext(exception, Some(context))) =>
        withLoggingDetails(context.ld):
          val objectKey = context.ld.mdcData.getOrElse(LoggingDetails.ObjectKey, "???")
          val fileReference = context.ld.mdcData.getOrElse(LoggingDetails.FileReference, "???")
          logger.error(
            s"Failed to process message '${message.id}' for object=[$objectKey] with upload Key=[$fileReference], cause ${exception.getMessage}",
            exception
          )
      case Left(ExceptionWithContext(exception, None)) =>
        logger.error(s"Failed to process message '${message.id}', cause ${exception.getMessage}", exception)
      case Right(_) =>
        ()

  def toEitherT[T](f: Future[T], context: Option[MessageContext] = None): EitherT[Future, ExceptionWithContext, T] =
    EitherT:
      f
        .map(Right.apply)
        .recover { case error: Exception => Left(ExceptionWithContext(error, context)) }

trait SuccessfulQueueConsumer extends QueueConsumer

trait QuarantineQueueConsumer extends QueueConsumer

class NotifyOnSuccessfulFileUploadMessageProcessingJob @Inject()(
  override val consumer: SuccessfulQueueConsumer,
  parser               : MessageParser,
  fileRetriever        : FileNotificationDetailsRetriever,
  notificationService  : NotificationService,
  metrics              : Metrics,
  clock                : Clock,
  upscanAuditingService: UpscanAuditingService,
  serviceConfiguration : ServiceConfiguration
)(using
  override val executionContext: ExecutionContext
) extends MessageProcessingJob:

  private[service] override val logger: Logger = Logger(getClass)

  override def processMessage(message: Message): EitherT[Future, ExceptionWithContext, MessageContext] =
    for
      parsedMessage               <- toEitherT(parser.parse(message))
      contextWithoutFileReference =  MessageContext(LoggingDetails.fromS3ObjectLocation(parsedMessage.location))
      notificationWithCheckpoints <- toEitherT(
                                      fileRetriever.retrieveUploadedFileDetails(parsedMessage.location),
                                      Some(contextWithoutFileReference))
      WithCheckpoints(notification, checkpoints1) = notificationWithCheckpoints
      context                     =  MessageContext(LoggingDetails.fromS3ObjectLocationWithReference(parsedMessage.location, notification.reference))
      checkpoint2                 =  Checkpoint("x-amz-meta-upscan-notify-received", message.receivedAt)
      _                           =  upscanAuditingService.notifyFileUploadedSuccessfully(notification)
      _                           =  collectMetricsBeforeNotification(notification)
      checkpoints3                <- toEitherT(notificationService.notifySuccessfulCallback(notification), Some(context))
    yield
      collectMetricsAfterNotification(notification, (checkpoints1 :+ checkpoint2) ++ checkpoints3)
      context

  private def collectMetricsBeforeNotification(notification: SuccessfulProcessingDetails): Unit =
    val totalProcessingTime = Duration.between(notification.uploadTimestamp, clock.instant())
    if totalProcessingTime.isNegative then
      logger.warn(
        "File processing time is negative, it might be caused by clocks out of sync, ignoring the measurement"
      )
    else
      metrics.defaultRegistry
        .timer("fileProcessingTimeExcludingNotification")
        .update(totalProcessingTime.toNanos, TimeUnit.NANOSECONDS)

  private[service] def collectMetricsAfterNotification(
    notification: SuccessfulProcessingDetails,
    checkpoints : Checkpoints
  ): Unit =
    val respondedAt = clock.instant()

    val updatedCheckpoints =
      checkpoints ++ Seq(
        Checkpoint("x-amz-meta-upscan-file-uploaded", notification.uploadTimestamp),
        Checkpoint("x-amz-meta-upscan-notify-responded", clock.instant())
      )

    val totalProcessingTime = Duration.between(notification.uploadTimestamp, respondedAt)

    if totalProcessingTime.isNegative then
      logger.warn(
        "File processing time is negative, it might be caused by clocks out of sync, ignoring the measurement")
    else
      metrics.defaultRegistry
        .timer("totalFileProcessingTime")
        .update(totalProcessingTime.toNanos, TimeUnit.NANOSECONDS)

      val endToEndProcessingThreshold: scala.concurrent.duration.Duration =
        serviceConfiguration.endToEndProcessingThreshold()

      if totalProcessingTime.toMillis > endToEndProcessingThreshold.toMillis then
        logger.warn:
          s"""Accepted file total processing time: [${totalProcessingTime.getSeconds} seconds] exceeded threshold of [$endToEndProcessingThreshold].
             |Processing checkpoints were:\n${updatedCheckpoints.breakdown}.
           """.stripMargin

    metrics.defaultRegistry.histogram("fileSize").update(notification.size)

    metrics.defaultRegistry.counter("successfulUploadNotificationSent").inc()

class NotifyOnQuarantineFileUploadMessageProcessingJob @Inject()(
  override val consumer: QuarantineQueueConsumer,
  parser               : MessageParser,
  fileRetriever        : FileNotificationDetailsRetriever,
  notificationService  : NotificationService,
  metrics              : Metrics,
  clock                : Clock,
  upscanAuditingService: UpscanAuditingService,
  serviceConfiguration : ServiceConfiguration
)(using
  override val executionContext: ExecutionContext
) extends MessageProcessingJob:

  private[service] override val logger: Logger = Logger(getClass)

  override def processMessage(message: Message): EitherT[Future, ExceptionWithContext, MessageContext] =
    for
      parsedMessage               <- toEitherT(parser.parse(message))
      contextWithoutFileReference =  MessageContext(LoggingDetails.fromS3ObjectLocation(parsedMessage.location))
      notificationWithCheckpoints <- toEitherT(
                                       fileRetriever.retrieveQuarantinedFileDetails(parsedMessage.location),
                                       Some(contextWithoutFileReference)
                                     )
      WithCheckpoints(notification, checkpoints1) = notificationWithCheckpoints
      context = MessageContext(LoggingDetails.fromS3ObjectLocationWithReference(parsedMessage.location, notification.reference))
      checkpoint2                 =  Checkpoint("x-amz-meta-upscan-notify-received", message.receivedAt)
      _                           =  upscanAuditingService.notifyFileIsQuarantined(notification)
      checkpoints3                <- toEitherT(notificationService.notifyFailedCallback(notification), Some(context))
    yield
      collectMetricsAfterNotification(notification, (checkpoints1 :+ checkpoint2) ++ checkpoints3)
      context

  private[service] def collectMetricsAfterNotification(
    notification: FailedProcessingDetails,
    checkpoints : Checkpoints
  ): Unit =
    val respondedAt = clock.instant()

    val updatedCheckpoints =
      checkpoints ++ Seq(
        Checkpoint("x-amz-meta-upscan-file-uploaded", notification.uploadTimestamp),
        Checkpoint("x-amz-meta-upscan-notify-responded", clock.instant())
      )

    val totalProcessingTime = Duration.between(notification.uploadTimestamp, respondedAt)

    if totalProcessingTime.isNegative then
      logger.warn(
        "File processing time is negative, it might be caused by clocks out of sync, ignoring the measurement")
    else
      metrics.defaultRegistry.counter("quarantinedUploadNotificationSent").inc()

      val endToEndProcessingThreshold: scala.concurrent.duration.Duration =
        serviceConfiguration.endToEndProcessingThreshold()

      if totalProcessingTime.toMillis > endToEndProcessingThreshold.toMillis then
        logger.warn:
          s"""Rejected file total processing time: [${totalProcessingTime.getSeconds} seconds] exceeded threshold of [$endToEndProcessingThreshold].
             |Processing checkpoints were:\n${updatedCheckpoints.breakdown}.
           """.stripMargin
