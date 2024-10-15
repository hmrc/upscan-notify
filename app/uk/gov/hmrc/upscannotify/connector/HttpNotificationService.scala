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

package uk.gov.hmrc.upscannotify.connector

import play.api.Logging
import play.api.libs.json._
import play.api.libs.ws.writeableOf_JsValue
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.upscannotify.model._
import uk.gov.hmrc.upscannotify.service.NotificationService

import java.net.URL
import java.time.{Clock, Instant}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class HttpNotificationService @Inject()(
  httpClientV2: HttpClientV2,
  clock       : Clock
)(using
  ExecutionContext
) extends NotificationService with Logging:

  private given HeaderCarrier = HeaderCarrier()

  override def notifySuccessfulCallback(uploadedFile: SuccessfulProcessingDetails): Future[Seq[Checkpoint]] =
    makeCallback(
      ReadyCallbackBody(
        reference     = uploadedFile.reference,
        downloadUrl   = uploadedFile.downloadUrl,
        uploadDetails = UploadDetails(
          fileName        = uploadedFile.fileName,
          fileMimeType    = uploadedFile.fileMimeType,
          uploadTimestamp = uploadedFile.uploadTimestamp,
          checksum        = uploadedFile.checksum,
          size            = uploadedFile.size
        )
      ),
      uploadedFile,
      "File ready"
    )

  override def notifyFailedCallback(quarantinedFile: FailedProcessingDetails): Future[Seq[Checkpoint]] =
    makeCallback(
      FailedCallbackBody(reference = quarantinedFile.reference, failureDetails = quarantinedFile.error),
      quarantinedFile,
      "File failed"
    )

  private def makeCallback[T, M <: ProcessingDetails](
    callback: T,
    metadata: M,
    notificationType: String
  )(using
    Writes[T]
  ): Future[Seq[Checkpoint]] =
    given HttpReads[HttpResponse] =
      HttpReads.Implicits.throwOnFailure(HttpReads.Implicits.readEitherOf(HttpReads.Implicits.readRaw))
    timed(
      httpClientV2.post(metadata.callbackUrl)
        .withBody(Json.toJson(callback))
        .execute[HttpResponse]
    ).map:
      case WithTimeMeasurement(measurement, httpResult) =>
        logger.info:
          s"""$notificationType notification for Key=[${metadata.reference.reference}] sent to service with callbackUrl: [${metadata.callbackUrl}].
            | Response status was: [${httpResult.status}].""".stripMargin
        collectExecutionTimeMetadata(measurement)

  private def collectExecutionTimeMetadata(timeMeasurement: TimeMeasurement): Seq[Checkpoint] =
    Seq(
      Checkpoint("x-amz-meta-upscan-notify-callback-started", timeMeasurement.start),
      Checkpoint("x-amz-meta-upscan-notify-callback-ended", timeMeasurement.end)
    )

  case class TimeMeasurement(
    start: Instant,
    end  : Instant
  )

  case class WithTimeMeasurement[T](
    timeMeasurement: TimeMeasurement,
    result         : T
  )

  private def timed[T](f: => Future[T])(using ExecutionContext): Future[WithTimeMeasurement[T]] =
    val startTime = clock.instant()
    f.map: result =>
      val endTime = clock.instant()
      WithTimeMeasurement(TimeMeasurement(startTime, endTime), result)

case class ReadyCallbackBody(
  reference    : FileReference,
  downloadUrl  : URL,
  fileStatus   : FileStatus    = FileStatus.Ready,
  uploadDetails: UploadDetails
)

case class UploadDetails(
  fileName       : String,
  fileMimeType   : String,
  uploadTimestamp: Instant,
  checksum       : String,
  size           : Long  // bytes
)

object UploadDetails:
  given Writes[UploadDetails] =
    Json.writes[UploadDetails]

object ReadyCallbackBody:
  given Writes[ReadyCallbackBody] =
    given urlFormats: Writes[URL] = JsonWriteHelpers.urlFormats
    Json.writes[ReadyCallbackBody]

case class FailedCallbackBody(
  reference     : FileReference,
  fileStatus    : FileStatus = FileStatus.Failed,
  failureDetails: ErrorDetails
)

object FailedCallbackBody:
  given Writes[FailedCallbackBody] =
    Json.writes[FailedCallbackBody]

// TODO not needed - can be inferred from ReadyCallbackBody or FailedCallbackBody
enum FileStatus(val status: String):
  case Ready  extends FileStatus("READY")
  case Failed extends FileStatus("FAILED")

object FileStatus:
  given Writes[FileStatus] =
    (o: FileStatus) => JsString(o.status)
