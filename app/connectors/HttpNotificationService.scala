/*
 * Copyright 2020 HM Revenue & Customs
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

package connectors

import java.net.URL
import java.time.{Clock, Instant}

import _root_.util.logging.LoggingDetails
import javax.inject.Inject
import model._
import play.api.Logging
import play.api.libs.json._
import services.NotificationService
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

class HttpNotificationService @Inject()(httpClient: HttpClient, clock: Clock)(implicit ec: ExecutionContext) extends NotificationService with Logging {

  override def notifySuccessfulCallback(uploadedFile: SuccessfulProcessingDetails): Future[Seq[Checkpoint]] =
    makeCallback(
      ReadyCallbackBody(
        reference   = uploadedFile.reference,
        downloadUrl = uploadedFile.downloadUrl,
        uploadDetails = UploadDetails(
          fileName        = uploadedFile.fileName,
          fileMimeType    = uploadedFile.fileMimeType,
          uploadTimestamp = uploadedFile.uploadTimestamp,
          checksum        = uploadedFile.checksum
        )
      ),
      uploadedFile,
      "File ready"
    )

  override def notifyFailedCallback(quarantinedFile: FailedProcessingDetails): Future[Seq[Checkpoint]] =
    makeCallback(
      FailedCallbackBody(reference = quarantinedFile.reference, failureDetails = quarantinedFile.error),
      quarantinedFile,
      "File failed")

  private def makeCallback[T, M <: ProcessingDetails](callback: T, metadata: M, notificationType: String)(
    implicit writes: Writes[T]): Future[Seq[Checkpoint]] = {
    implicit val ld: HeaderCarrier = LoggingDetails.fromFileReference(metadata.reference)

    for (WithTimeMeasurement(measurement, httpResult) <- timed(
                                                          httpClient.POST[T, HttpResponse](
                                                            metadata.callbackUrl.toString,
                                                            callback))) yield {
      logger.info(
        s"""$notificationType notification sent to service with callbackUrl: [${metadata.callbackUrl}].
           | Response status was: [${httpResult.status}].""".stripMargin
      )
      collectExecutionTimeMetadata(measurement)
    }
  }

  private def collectExecutionTimeMetadata(timeMeasurement: TimeMeasurement): Seq[Checkpoint] =
    Seq(
      Checkpoint("x-amz-meta-upscan-notify-callback-started", timeMeasurement.start),
      Checkpoint("x-amz-meta-upscan-notify-callback-ended", timeMeasurement.end)
    )

  case class TimeMeasurement(start: Instant, end: Instant)
  case class WithTimeMeasurement[T](timeMeasurement: TimeMeasurement, result: T)

  private def timed[T](f: => Future[T])(implicit ec: ExecutionContext): Future[WithTimeMeasurement[T]] = {

    val startTime = clock.instant()
    f.map { result =>
      val endTime = clock.instant()
      WithTimeMeasurement(TimeMeasurement(startTime, endTime), result)
    }
  }

}

case class ReadyCallbackBody(
  reference: FileReference,
  downloadUrl: URL,
  fileStatus: FileStatus = ReadyFileStatus,
  uploadDetails: UploadDetails
)

case class UploadDetails(fileName: String, fileMimeType: String, uploadTimestamp: Instant, checksum: String)

object UploadDetails {
  implicit val formatsValidUploadDetails: Format[UploadDetails] = Json.format[UploadDetails]
}

object ReadyCallbackBody {
  implicit val urlFormats = JsonWriteHelpers.urlFormats
  implicit val writesReadyCallback: Writes[ReadyCallbackBody] = Json.writes[ReadyCallbackBody]
}

case class FailedCallbackBody(
  reference: FileReference,
  fileStatus: FileStatus = FailedFileStatus,
  failureDetails: ErrorDetails
)

object FailedCallbackBody {
  implicit val writesFailedCallback: Writes[FailedCallbackBody] = Json.writes[FailedCallbackBody]
}

sealed trait FileStatus {
  val status: String
}
case object ReadyFileStatus extends FileStatus {
  override val status: String = "READY"
}
case object FailedFileStatus extends FileStatus {
  override val status: String = "FAILED"
}

object FileStatus {
  implicit val fileStatusWrites: Writes[FileStatus] = new Writes[FileStatus] {
    override def writes(o: FileStatus): JsValue = JsString(o.status)
  }
}
