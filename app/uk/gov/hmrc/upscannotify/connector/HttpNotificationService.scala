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
import java.time.Instant
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class HttpNotificationService @Inject()(
  httpClientV2: HttpClientV2
)(using
  ExecutionContext
) extends NotificationService with Logging:

  private given HeaderCarrier = HeaderCarrier()

  override def notifySuccessfulCallback(uploadedFile: SuccessfulProcessingDetails): Future[Unit] =
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

  override def notifyFailedCallback(quarantinedFile: FailedProcessingDetails): Future[Unit] =
    makeCallback(
      FailedCallbackBody(reference = quarantinedFile.reference, failureDetails = quarantinedFile.error),
      quarantinedFile,
      "File failed"
    )

  private def makeCallback[T: Writes](
    callback        : T,
    metadata        : ProcessingDetails,
    notificationType: String
  ): Future[Unit] =
    given HttpReads[HttpResponse] =
      HttpReads.Implicits.throwOnFailure(HttpReads.Implicits.readEitherOf(HttpReads.Implicits.readRaw))
    httpClientV2.post(metadata.callbackUrl)
      .withBody(Json.toJson(callback))
      .execute[HttpResponse]
      .map:
        case httpResult =>
          logger.info:
            s"""$notificationType notification for Key=[${metadata.reference.reference}] sent to service with callbackUrl: [${metadata.callbackUrl}].
              | Response status was: [${httpResult.status}].""".stripMargin

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

enum FileStatus(val status: String):
  case Ready  extends FileStatus("READY")
  case Failed extends FileStatus("FAILED")

object FileStatus:
  given Writes[FileStatus] =
    (o: FileStatus) => JsString(o.status)
