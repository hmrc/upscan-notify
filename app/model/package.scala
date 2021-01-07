/*
 * Copyright 2021 HM Revenue & Customs
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

import play.api.libs.json._

case class FileReference(reference: String)
object FileReference {
  implicit val fileReferenceWrites: Writes[FileReference] = new Writes[FileReference] {
    override def writes(o: FileReference): JsValue = JsString(o.reference)
  }
}

case class Message(id: String, body: String, receiptHandle: String, receivedAt: Instant)

case class RequestContext(requestId: Option[String], sessionId: Option[String], clientIp: String)

case class Checkpoint(name: String, timestamp: Instant)
case class Checkpoints(items: Seq[Checkpoint]) {
  def :+(checkpoint: Checkpoint) = copy(items = items :+ checkpoint)

  def ++(newCheckpoints: Seq[Checkpoint]) = copy(items = items ++ newCheckpoints)

  def sortedCheckpoints =
    items.sortBy(_.timestamp)
}

case class WithCheckpoints[T](details: T, checkpoints: Checkpoints)

sealed trait ProcessingDetails {
  def callbackUrl: URL
  def reference: FileReference
}

case class SuccessfulProcessingDetails(
  callbackUrl: URL,
  reference: FileReference,
  downloadUrl: URL,
  size: Long,
  fileName: String,
  fileMimeType: String,
  uploadTimestamp: Instant,
  checksum: String,
  requestContext: RequestContext
) extends ProcessingDetails

case class FailedProcessingDetails(
  callbackUrl: URL,
  reference: FileReference,
  fileName: String,
  uploadTimestamp: Instant,
  error: ErrorDetails,
  requestContext: RequestContext
) extends ProcessingDetails

case class S3ObjectLocation(bucket: String, objectKey: String)
case class FileUploadEvent(location: S3ObjectLocation)

case class ErrorDetails(failureReason: String, message: String)

object ErrorDetails {
  implicit val formatsErrorDetails: Format[ErrorDetails] = Json.format[ErrorDetails]
}
