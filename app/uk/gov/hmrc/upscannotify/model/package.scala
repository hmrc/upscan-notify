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

package uk.gov.hmrc.upscannotify.model

import play.api.libs.json._

import java.net.URL
import java.time.Instant

case class FileReference(
  reference: String
)

object FileReference:
  given Writes[FileReference] =
    (o: FileReference) => JsString(o.reference)

case class Message(
  id           : String,
  body         : String,
  receiptHandle: String,
  receivedAt   : Instant
)

case class RequestContext(
  requestId: Option[String],
  sessionId: Option[String],
  clientIp : String
)

sealed trait ProcessingDetails:
  def callbackUrl: URL
  def reference  : FileReference

case class SuccessfulProcessingDetails(
  callbackUrl    : URL,
  reference      : FileReference,
  downloadUrl    : URL,
  size           : Long,
  fileName       : String,
  fileMimeType   : String,
  uploadTimestamp: Instant,
  checksum       : String,
  requestContext : RequestContext
) extends ProcessingDetails

case class FailedProcessingDetails(
  callbackUrl    : URL,
  reference      : FileReference,
  fileName       : String,
  uploadTimestamp: Instant,
  error          : ErrorDetails,
  requestContext : RequestContext
) extends ProcessingDetails

case class S3ObjectLocation(
  bucket   : String,
  objectKey: String
)

case class FileUploadEvent(
  location: S3ObjectLocation
)

case class ErrorDetails(
  failureReason: String,
  message      : String
)

object ErrorDetails:
  given Format[ErrorDetails] =
    Json.format[ErrorDetails]
