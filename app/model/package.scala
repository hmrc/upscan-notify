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

case class Message(id: String, body: String, receiptHandle: String, receivedAt: Instant)

sealed abstract class UploadDetails(fileName: String, val uploadTimestamp: Instant)
case class ValidUploadDetails(fileName: String, fileMimeType: String, override val uploadTimestamp: Instant, checksum: String) extends UploadDetails(fileName, uploadTimestamp)
case class InvalidUploadDetails(fileName: String, override val uploadTimestamp: Instant) extends UploadDetails(fileName, uploadTimestamp)

case class RequestContext(requestId: Option[String], sessionId: Option[String], clientIp: String)

object UploadDetails {
  implicit val formatUploadDetails: Format[UploadDetails] = new Format[UploadDetails] {
    override def reads(json: JsValue): JsResult[UploadDetails] = {
      ValidUploadDetails.formatsValidUploadDetails.reads(json).orElse(InvalidUploadDetails.formatsInvalidUploadDetails.reads(json))
    }
    override def writes(up: UploadDetails): JsValue = up match {
      case v: ValidUploadDetails   => ValidUploadDetails.formatsValidUploadDetails.writes(v)
      case i: InvalidUploadDetails => InvalidUploadDetails.formatsInvalidUploadDetails.writes(i)
    }
  }
}
object ValidUploadDetails {
  implicit val formatsValidUploadDetails: Format[ValidUploadDetails] = Json.format[ValidUploadDetails]
}
object InvalidUploadDetails {
  implicit val formatsInvalidUploadDetails: Format[InvalidUploadDetails] = Json.format[InvalidUploadDetails]
}

trait UserMetadataLike {
  val userMetadata: Map[String,String]

  def checkpoints(): Map[String,String] = userMetadata.filterKeys {
    _.startsWith("x-amz-meta-upscan-")
  }
}

case class UploadedFile(
  callbackUrl: URL,
  reference: FileReference,
  downloadUrl: URL,
  size: Long,
  uploadDetails: UploadDetails,
  requestContext: RequestContext,
  override val userMetadata: Map[String, String]) extends UserMetadataLike {

  def copyWithUserMetadata(kv: (String,String)): UploadedFile = {
    copy(userMetadata = (this.userMetadata + kv))
  }

  def copyWithUserMetadata(kv1: (String,String), kv2: (String,String), kv3: (String,String)*): UploadedFile = {
    copy(userMetadata = (this.userMetadata + (kv1, kv2, kv3:_*)))
  }
}

case class QuarantinedFile(
  callbackUrl: URL,
  reference: FileReference,
  error: ErrorDetails,
  uploadDetails: UploadDetails,
  requestContext: RequestContext,
  override val userMetadata: Map[String, String]) extends UserMetadataLike {

  def copyWithUserMetadata(kv: (String,String)): QuarantinedFile = {
    copy(userMetadata = (this.userMetadata + kv))
  }

  def copyWithUserMetadata(kv1: (String,String), kv2: (String,String), kv3: (String,String)*): QuarantinedFile = {
    copy(userMetadata = (this.userMetadata + (kv1, kv2, kv3:_*)))
  }
}

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
