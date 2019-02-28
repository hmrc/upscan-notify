/*
 * Copyright 2019 HM Revenue & Customs
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

case class Message(id: String, body: String, receiptHandle: String, receivedAt: Instant)

case class RequestContext(requestId: Option[String], sessionId: Option[String], clientIp: String)

object UserMetadataLike {
  val sortChronologically: ((String, String)) => String = (checkpoint) => checkpoint._2
}

case class FileProcessingDetails[R <: ProcessingResult](
  callbackUrl: URL,
  reference: FileReference,
  result: R,
  requestContext: RequestContext,
  userMetadata: Map[String, String]) {
  def copyWithUserMetadata(kv: (String, String)): FileProcessingDetails[R] =
    copy(userMetadata = (this.userMetadata + kv))

  def copyWithUserMetadata(
    kv1: (String, String),
    kv2: (String, String),
    kv3: (String, String)*): FileProcessingDetails[R] =
    copy(userMetadata = (this.userMetadata + (kv1, kv2, kv3: _*)))

  def checkpoints(): Map[String, String] = userMetadata.filterKeys {
    _.startsWith("x-amz-meta-upscan-")
  }
}

sealed trait ProcessingResult {
  def uploadTimestamp: Instant
}

case class SucessfulResult(
  downloadUrl: URL,
  size: Long,
  fileName: String,
  fileMimeType: String,
  uploadTimestamp: Instant,
  checksum: String)
    extends ProcessingResult

case class QuarantinedResult(error: ErrorDetails, fileName: String, uploadTimestamp: Instant) extends ProcessingResult

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

case class UploadDetails(fileName: String, fileMimeType: String, uploadTimestamp: Instant, checksum: String)

object UploadDetails {
  implicit val formatsValidUploadDetails: Format[UploadDetails] = Json.format[UploadDetails]
}

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
