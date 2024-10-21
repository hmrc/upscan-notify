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

import java.time.{Clock, Instant}
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

  def processParsedMessage(message: Message, parsedMessage: FileUploadEvent): Future[Boolean]

  override def processMessage(message: Message): Future[Boolean] =
    parser.parse(message)
      .flatMap: parsedMessage =>
        LoggingUtils.withMdc(Map(
          "object-key" -> parsedMessage.location.objectKey
        )):
          processParsedMessage(message, parsedMessage)
            .recover:
              case exception =>
                logger.error(s"Failed to process message '${message.id}' for object=[${parsedMessage.location.objectKey}], cause ${exception.getMessage}", exception)
                false
      .recover:
        case exception =>
          logger.error(s"Failed to process message '${message.id}', cause ${exception.getMessage}", exception)
          false

class NotifyOnSuccessfulFileUploadMessageProcessingJob @Inject()(
  override val parser             : MessageParser,
  fileNotificationDetailsRetriever: FileNotificationDetailsRetriever,
  notificationService             : NotificationService,
  downloadUrlGenerator            : DownloadUrlGenerator,
  upscanAuditingService           : UpscanAuditingService,
  serviceConfiguration            : ServiceConfiguration
)(using
  override val executionContext   : ExecutionContext,
  metricRegistry                  : MetricRegistry,
  clock                           : Clock
) extends MessageProcessingJob:

  private[service] override val logger: Logger = Logger(getClass)

  override val queueUrl            = serviceConfiguration.outboundSuccessfulQueueUrl
  override val processingBatchSize = serviceConfiguration.successfulProcessingBatchSize
  override val waitTime            = serviceConfiguration.successfulWaitTime

  override def processParsedMessage(message: Message, parsedMessage: FileUploadEvent): Future[Boolean] =
    fileNotificationDetailsRetriever.receiveSuccessfulFileDetails(parsedMessage.location)
      .flatMap: metadata =>
        LoggingUtils.withMdc(Map(
          "file-reference" -> metadata.fileReference.reference
        )):
          (for
             _            <- Future.unit
             downloadUrl  =  downloadUrlGenerator.generate(parsedMessage.location, metadata)
             checkpoints  =  Checkpoint.parse(metadata.userMetadata)
                               :+ Checkpoint("upscan-file-uploaded"  , metadata.uploadTimestamp)
                               :+ Checkpoint("upscan-notify-received", message.receivedAt)
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
             _            =  upscanAuditingService.notifyFileUploadedSuccessfully(retrieved)
             _            =  MessageProcessingJob.collectMetricsBeforeNotification(retrieved)
             cp2          =  Checkpoint("upscan-notify-callback-started")
             _            <- notificationService.notifySuccessfulCallback(retrieved)
             cp3          =  Checkpoint("upscan-notify-callback-ended")
             cp4          =  Checkpoint("upscan-notify-responded")
             _            =  MessageProcessingJob.collectMetricsAfterNotificationSuccess(
                               retrieved,
                               checkpoints :+ cp2 :+ cp3 :+ cp4,
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
  override val parser             : MessageParser,
  fileNotificationDetailsRetriever: FileNotificationDetailsRetriever,
  notificationService             : NotificationService,
  upscanAuditingService           : UpscanAuditingService,
  serviceConfiguration            : ServiceConfiguration
)(using
  override val executionContext   : ExecutionContext,
  metricRegistry                  : MetricRegistry,
  clock                           : Clock
) extends MessageProcessingJob:

  private[service] override val logger: Logger = Logger(getClass)

  override val queueUrl            = serviceConfiguration.outboundQuarantineQueueUrl
  override val processingBatchSize = serviceConfiguration.quarantineProcessingBatchSize
  override val waitTime            = serviceConfiguration.quarantineWaitTime

  override def processParsedMessage(message: Message, parsedMessage: FileUploadEvent): Future[Boolean] =
    fileNotificationDetailsRetriever.receiveFailedFileDetails(parsedMessage.location)
      .flatMap: quarantineFile =>
        LoggingUtils.withMdc(Map(
          "file-reference" -> quarantineFile.fileReference.reference
        )):
          (for
             _            <- Future.unit
             checkpoints  =  Checkpoint.parse(quarantineFile.userMetadata)
                               :+ Checkpoint("upscan-file-uploaded"  , quarantineFile.uploadTimestamp)
                               :+ Checkpoint("upscan-notify-received", message.receivedAt)
             retrieved    =  FailedProcessingDetails(
                               callbackUrl     = quarantineFile.callbackUrl,
                               reference       = quarantineFile.fileReference,
                               fileName        = quarantineFile.fileName,
                               uploadTimestamp = quarantineFile.uploadTimestamp,
                               error           = MessageProcessingJob.parseErrorDetails(quarantineFile.failureDetailsAsJson),
                               requestContext  = quarantineFile.requestContext
                             )
             _            =  logger.debug:
                               s"Retrieved quarantined file with Key=[${quarantineFile.fileReference.reference}] and callbackUrl=[${quarantineFile.callbackUrl}] for object=[${parsedMessage.location.objectKey}]."
             _            =  upscanAuditingService.notifyFileIsQuarantined(retrieved)
             cp2          =  Checkpoint("upscan-notify-callback-started")
             _            <- notificationService.notifyFailedCallback(retrieved)
             cp3          =  Checkpoint("upscan-notify-callback-ended")
             cp4          =  Checkpoint("upscan-notify-responded")
             _            =  MessageProcessingJob.collectMetricsAfterNotificationFailed(
                               retrieved,
                               checkpoints :+ cp2 :+ cp3 :+ cp4,
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

  def parseErrorDetails(contents: String): ErrorDetails =
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
      logger.warn:
        "File processing time is negative, it might be caused by clocks out of sync, ignoring the measurement"
    else
      metricRegistry
        .timer("fileProcessingTimeExcludingNotification")
        .update(totalProcessingTime.toNanos, TimeUnit.NANOSECONDS)

  def collectMetricsAfterNotificationSuccess(
    notification               : SuccessfulProcessingDetails,
    checkpoints                : Seq[Checkpoint],
    endToEndProcessingThreshold: Duration
  )(using metricRegistry: MetricRegistry, clock: Clock): Unit =
    val totalProcessingTime = java.time.Duration.between(notification.uploadTimestamp, clock.instant())
    if totalProcessingTime.isNegative then
      logger.warn:
        "File processing time is negative, it might be caused by clocks out of sync, ignoring the measurement"
    else
      metricRegistry
        .timer("totalFileProcessingTime")
        .update(totalProcessingTime.toNanos, TimeUnit.NANOSECONDS)

      if totalProcessingTime.toMillis > endToEndProcessingThreshold.toMillis then
        logger.warn:
          s"""Accepted file total processing time: [${totalProcessingTime.getSeconds} seconds] exceeded threshold of [$endToEndProcessingThreshold].
             |Processing checkpoints were:\n${Checkpoint.breakdown(checkpoints)}.
           """.stripMargin

    metricRegistry.histogram("fileSize").update(notification.size)
    metricRegistry.counter("successfulUploadNotificationSent").inc()

  def collectMetricsAfterNotificationFailed(
    notification               : FailedProcessingDetails,
    checkpoints                : Seq[Checkpoint],
    endToEndProcessingThreshold: Duration
  )(using metricRegistry: MetricRegistry, clock: Clock): Unit =
    val totalProcessingTime = java.time.Duration.between(notification.uploadTimestamp, clock.instant())
    if totalProcessingTime.isNegative then
      logger.warn:
        "File processing time is negative, it might be caused by clocks out of sync, ignoring the measurement"
    else
      if totalProcessingTime.toMillis > endToEndProcessingThreshold.toMillis then
        logger.warn:
          s"""Rejected file total processing time: [${totalProcessingTime.getSeconds} seconds] exceeded threshold of [$endToEndProcessingThreshold].
             |Processing checkpoints were:\n${Checkpoint.breakdown(checkpoints)}.
           """.stripMargin

    metricRegistry.counter("quarantinedUploadNotificationSent").inc()


case class Checkpoint(
  name     : String,
  timestamp: Instant
)

object Checkpoint:
  private val logger = Logger(getClass)

  def parse(userMetadata: Map[String, String]): Seq[Checkpoint] =
    userMetadata
      .view
      .filterKeys(_.startsWith("upscan-"))
      .flatMap:
        case (key, value) =>
          Try(java.time.Instant.parse(value)) match
            case Success(parsedTimestamp) =>
              Some(Checkpoint(key, parsedTimestamp))
            case Failure(exception)       =>
              logger.warn(s"Checkpoint field $key has invalid format", exception)
              None
      .toSeq

  def apply(name: String)(using clock: Clock): Checkpoint =
    apply(name, clock.instant())

  def breakdown(checkpoints: Seq[Checkpoint]): String =
    checkpoints
      .sortBy(_.timestamp)
      .foldLeft((Option.empty[Checkpoint], List.empty[String])):
        case ((optPrevious, acc), current) =>
          val description =
            optPrevious.foldLeft(s"${current.name} @ ${current.timestamp}"): (prefix, previous) =>
              val duration = java.time.Duration.between(previous.timestamp, current.timestamp).toMillis
              s"$prefix, took $duration ms"
          (Some(current), acc :+ description)
      ._2
      .mkString("\n")
