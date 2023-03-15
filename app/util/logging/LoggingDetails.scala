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

package util.logging

import model.{FileReference, S3ObjectLocation}
import uk.gov.hmrc.http.HeaderCarrier

/**
 * Convenience methods to create a [[uk.gov.hmrc.http.logging.LoggingDetails]] instance, required by [[uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext]].
 */
object LoggingDetails {
  val ObjectKey = "object-key"
  val FileReference = "file-reference"

  def fromS3ObjectLocation(s3ObjectLocation: S3ObjectLocation): HeaderCarrier =
    fromValues(ObjectKey -> s3ObjectLocation.objectKey)

  def fromFileReference(fileReference: FileReference): HeaderCarrier =
    fromValues(FileReference -> fileReference.reference)

  def fromS3ObjectLocationWithReference(s3ObjectLocation: S3ObjectLocation, fileReference: FileReference): HeaderCarrier =
    fromValues(ObjectKey -> s3ObjectLocation.objectKey, FileReference -> fileReference.reference)

  private def fromValues(values: (String, String)*): HeaderCarrier =
    new HeaderCarrier() {
      override lazy val mdcData: Map[String, String] = super.mdcData ++ values
    }
}
