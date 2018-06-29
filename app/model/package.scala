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

package model

import java.net.URL
import java.time.Instant

import JsonWriteHelpers.urlFormats
import play.api.libs.json._

case class FileReference(reference: String)
object FileReference {
  implicit val fileReferenceWrites: Writes[FileReference] = new Writes[FileReference] {
    override def writes(o: FileReference): JsValue = JsString(o.reference)
  }
}

case class Message(id: String, body: String, receiptHandle: String)

case class UploadDetails(fileName: String, fileMimeType: String, uploadTimestamp: Instant, checksum: String)

case class RequestContext(requestId: Option[String], sessionId: Option[String])

object UploadDetails {
  implicit val formatsUploadDetails: Format[UploadDetails] = Json.format[UploadDetails]
}

case class UploadedFile(
  callbackUrl: URL,
  reference: FileReference,
  downloadUrl: URL,
  size: Long,
  uploadDetails: UploadDetails,
  requestContext: RequestContext
)

case class QuarantinedFile(
  callbackUrl: URL,
  reference: FileReference,
  error: ErrorDetails,
  requestContext: RequestContext)

case class S3ObjectLocation(bucket: String, objectKey: String)
case class FileUploadEvent(location: S3ObjectLocation)

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

case class ErrorDetails(failureReason: String, message: String)

object ErrorDetails {
  implicit val formatsErrorDetails: Format[ErrorDetails] = Json.format[ErrorDetails]
}

case class ReadyCallbackBody(
  reference: FileReference,
  downloadUrl: URL,
  fileStatus: FileStatus = ReadyFileStatus,
  uploadDetails: UploadDetails
)

object ReadyCallbackBody {
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
