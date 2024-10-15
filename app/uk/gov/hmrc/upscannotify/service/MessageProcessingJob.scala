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

import cats.implicits._
import com.codahale.metrics.MetricRegistry
import play.api.LoggerLike
import play.api.Logger
import play.api.libs.json.{JsError, JsSuccess, Json}
import uk.gov.hmrc.upscannotify.config.ServiceConfiguration
import uk.gov.hmrc.upscannotify.model._
import uk.gov.hmrc.upscannotify.util.logging.LoggingUtils

import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

trait MessageProcessingJob extends PollingJob:
  private[service] val logger: LoggerLike

  def executionContext: ExecutionContext
  private given ExecutionContext = executionContext

  def parser  : MessageParser

  def consumer: QueueConsumer

  def processMessage(message: Message, parsedMessage: FileUploadEvent): Future[Boolean]

  def run(): Future[Unit] =
     for
       messages <- consumer.poll()
       _        <- Future.traverse(messages)(handleMessage)
     yield ()

  private def handleMessage(message: Message): Future[Unit] =
    parser.parse(message)
      .flatMap: parsedMessage =>
        LoggingUtils.withMdc(Map(
          "object-key" -> parsedMessage.location.objectKey
        )):
          (for
            isHandled <- processMessage(message, parsedMessage)
            _         <- // TODO if not handled, return to queue immediately for retry rather than waiting for visibility timeout
                         if isHandled then consumer.confirm(message) else Future.unit
           yield ()
          ).recover:
            case exception =>
              logger.error(s"Failed to process message '${message.id}' for object=[${parsedMessage.location.objectKey}], cause ${exception.getMessage}", exception)
      .recover:
        case exception =>
          logger.error(s"Failed to process message '${message.id}', cause ${exception.getMessage}", exception)

trait SuccessfulQueueConsumer extends QueueConsumer

trait QuarantineQueueConsumer extends QueueConsumer

class NotifyOnSuccessfulFileUploadMessageProcessingJob @Inject()(
  override val consumer: SuccessfulQueueConsumer,
  override val parser  : MessageParser,
  fileManager          : FileManager,
  notificationService  : NotificationService,
  downloadUrlGenerator : DownloadUrlGenerator,
  upscanAuditingService: UpscanAuditingService,
  serviceConfiguration : ServiceConfiguration
)(using
  override val executionContext: ExecutionContext,
  metricRegistry       : MetricRegistry,
  clock                : Clock
) extends MessageProcessingJob:

  private[service] override val logger: Logger = Logger(getClass)

  override def processMessage(message: Message, parsedMessage: FileUploadEvent): Future[Boolean] =
    fileManager.receiveSuccessfulFileDetails(parsedMessage.location)
      .flatMap: metadata =>
        LoggingUtils.withMdc(Map(
          "file-reference" -> metadata.fileReference.reference
        )):
          (for
             _            <- Future.unit
             downloadUrl  =  downloadUrlGenerator.generate(parsedMessage.location, metadata)
             checkpoints1 =  Checkpoints(MessageProcessingJob.parseCheckpoints(metadata.userMetadata))
             retrieved    =  SuccessfulProcessingDetails(
                               metadata.callbackUrl,
                               metadata.fileReference,
                               downloadUrl     = downloadUrl,
                               size            = metadata.size,
                               fileName        = metadata.fileName,
                               fileMimeType    = metadata.fileMimeType,
                               uploadTimestamp = metadata.uploadTimestamp,
                               checksum        = metadata.checksum,
                               metadata.requestContext
                             )
             _            =  if metadata.fileName.contains("{filename}") then
                               // Debug whether filename is ever `${filename}`
                               logger.warn(s"Unexpected original filename ${metadata.fileName} for object=[${parsedMessage.location.objectKey}] with upload Key=[${metadata.fileReference.reference}].")
             _            =  logger.debug:
                               s"Retrieved file with Key=[${metadata.fileReference.reference}] and callbackUrl=[${metadata.callbackUrl}] for object=[${parsedMessage.location.objectKey}]."
             checkpoint2  =  Checkpoint("x-amz-meta-upscan-notify-received", message.receivedAt)
             _            =  upscanAuditingService.notifyFileUploadedSuccessfully(retrieved)
             _            =  MessageProcessingJob.collectMetricsBeforeNotification(retrieved)
             checkpoints3 <- notificationService.notifySuccessfulCallback(retrieved)
             _            =  MessageProcessingJob.collectMetricsAfterNotificationSuccess(
                               retrieved,
                               (checkpoints1 :+ checkpoint2) ++ checkpoints3,
                               endToEndProcessingThreshold = serviceConfiguration.endToEndProcessingThreshold
                             )
           yield
             true
          ).recover:
            case exception =>
              logger.error(
                s"Failed to process message '${message.id}' for object=[${parsedMessage.location.objectKey}] with upload Key=[${metadata.fileReference.reference}], cause ${exception.getMessage}",
                exception
              )
              false


class NotifyOnQuarantineFileUploadMessageProcessingJob @Inject()(
  override val consumer: QuarantineQueueConsumer,
  override val parser  : MessageParser,
  fileManager          : FileManager,
  notificationService  : NotificationService,
  upscanAuditingService: UpscanAuditingService,
  serviceConfiguration : ServiceConfiguration
)(using
  override val executionContext: ExecutionContext,
  metricRegistry       : MetricRegistry,
  clock                : Clock
) extends MessageProcessingJob:

  private[service] override val logger: Logger = Logger(getClass)

  override def processMessage(message: Message, parsedMessage: FileUploadEvent): Future[Boolean] =
    fileManager.receiveFailedFileDetails(parsedMessage.location)
      .flatMap: quarantineFile =>
        LoggingUtils.withMdc(Map(
          "file-reference" -> quarantineFile.fileReference.reference
        )):
          (for
             _            <- Future.unit
             checkpoints1 =  Checkpoints(MessageProcessingJob.parseCheckpoints(quarantineFile.userMetadata))
             retrieved    =  FailedProcessingDetails(
                               callbackUrl     = quarantineFile.callbackUrl,
                               reference       = quarantineFile.fileReference,
                               fileName        = quarantineFile.fileName,
                               uploadTimestamp = quarantineFile.uploadTimestamp,
                               error           = MessageProcessingJob.parseContents(quarantineFile.failureDetailsAsJson),
                               requestContext  = quarantineFile.requestContext
                             )
             _            =  logger.debug:
                               s"Retrieved quarantined file with Key=[${quarantineFile.fileReference.reference}] and callbackUrl=[${quarantineFile.callbackUrl}] for object=[${parsedMessage.location.objectKey}]."
             checkpoint2  =  Checkpoint("x-amz-meta-upscan-notify-received", message.receivedAt)
             _            =  upscanAuditingService.notifyFileIsQuarantined(retrieved)
             checkpoints3 <- notificationService.notifyFailedCallback(retrieved)
             _            =  MessageProcessingJob.collectMetricsAfterNotificationFailed(
                               retrieved,
                               (checkpoints1 :+ checkpoint2) ++ checkpoints3,
                               endToEndProcessingThreshold = serviceConfiguration.endToEndProcessingThreshold
                             )
           yield
             true
          ).recover:
            case exception =>
              logger.error(
                s"Failed to process message '${message.id}' for object=[${parsedMessage.location.objectKey}] with upload Key=[${quarantineFile.fileReference.reference}], cause ${exception.getMessage}",
                exception
              )
              false


object MessageProcessingJob:
  private[service] val logger = Logger(getClass)

  def parseCheckpoints(userMetadata: Map[String, String]) =
    userMetadata
      .view
      .filterKeys(_.startsWith("x-amz-meta-upscan-"))
      .flatMap:
        case (key, value) =>
          Try(java.time.Instant.parse(value)) match
            case Success(parsedTimestamp) =>
              Some(Checkpoint(key, parsedTimestamp))
            case Failure(exception)       =>
              logger.warn(s"Checkpoint field $key has invalid format", exception)
              None
      .toSeq

  def parseContents(contents: String): ErrorDetails =
    def unknownError(): ErrorDetails = ErrorDetails("UNKNOWN", contents)

    Try(Json.parse(contents)) match
      case Success(json) =>
        json.validate[ErrorDetails] match
          case JsSuccess(details, _) => details
          case _: JsError            => unknownError()
      case Failure(_)   =>
        unknownError()

  def collectMetricsBeforeNotification(notification: SuccessfulProcessingDetails)(using metricRegistry: MetricRegistry, clock: Clock): Unit =
    val totalProcessingTime = java.time.Duration.between(notification.uploadTimestamp, clock.instant())
    if totalProcessingTime.isNegative then
      logger.warn(
        "File processing time is negative, it might be caused by clocks out of sync, ignoring the measurement"
      )
    else
      metricRegistry
        .timer("fileProcessingTimeExcludingNotification")
        .update(totalProcessingTime.toNanos, TimeUnit.NANOSECONDS)

  def collectMetricsAfterNotificationSuccess(
    notification               : SuccessfulProcessingDetails,
    checkpoints                : Checkpoints,
    endToEndProcessingThreshold: Duration
  )(using metricRegistry: MetricRegistry, clock: Clock): Unit =
    val respondedAt = clock.instant()

    val updatedCheckpoints =
      checkpoints ++ Seq(
        Checkpoint("x-amz-meta-upscan-file-uploaded", notification.uploadTimestamp),
        Checkpoint("x-amz-meta-upscan-notify-responded", respondedAt)
      )

    val totalProcessingTime = java.time.Duration.between(notification.uploadTimestamp, respondedAt)

    if totalProcessingTime.isNegative then
      logger.warn(
        "File processing time is negative, it might be caused by clocks out of sync, ignoring the measurement")
    else
      metricRegistry
        .timer("totalFileProcessingTime")
        .update(totalProcessingTime.toNanos, TimeUnit.NANOSECONDS)

      if totalProcessingTime.toMillis > endToEndProcessingThreshold.toMillis then
        logger.warn:
          s"""Accepted file total processing time: [${totalProcessingTime.getSeconds} seconds] exceeded threshold of [$endToEndProcessingThreshold].
             |Processing checkpoints were:\n${updatedCheckpoints.breakdown}.
           """.stripMargin

    metricRegistry.histogram("fileSize").update(notification.size)

    metricRegistry.counter("successfulUploadNotificationSent").inc()

  def collectMetricsAfterNotificationFailed(
    notification               : FailedProcessingDetails,
    checkpoints                : Checkpoints,
    endToEndProcessingThreshold: Duration
  )(using metricRegistry: MetricRegistry, clock: Clock): Unit =
    val respondedAt = clock.instant()

    val updatedCheckpoints =
      checkpoints ++ Seq(
        Checkpoint("x-amz-meta-upscan-file-uploaded", notification.uploadTimestamp),
        Checkpoint("x-amz-meta-upscan-notify-responded", respondedAt)
      )

    val totalProcessingTime = java.time.Duration.between(notification.uploadTimestamp, respondedAt)

    if totalProcessingTime.isNegative then
      logger.warn(
        "File processing time is negative, it might be caused by clocks out of sync, ignoring the measurement")
    else
      metricRegistry.counter("quarantinedUploadNotificationSent").inc()

      if totalProcessingTime.toMillis > endToEndProcessingThreshold.toMillis then
        logger.warn:
          s"""Rejected file total processing time: [${totalProcessingTime.getSeconds} seconds] exceeded threshold of [$endToEndProcessingThreshold].
             |Processing checkpoints were:\n${updatedCheckpoints.breakdown}.
           """.stripMargin
