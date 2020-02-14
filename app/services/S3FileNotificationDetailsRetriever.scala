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

package services

import java.time.Instant

import javax.inject.Inject
import config.ServiceConfiguration
import model._
import play.api.Logger
import play.api.libs.json.{JsError, JsSuccess, Json}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails
import util.logging.LoggingDetails

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class S3FileNotificationDetailsRetriever @Inject()(
  fileManager: FileManager,
  config: ServiceConfiguration,
  downloadUrlGenerator: DownloadUrlGenerator)
    extends FileNotificationDetailsRetriever {

  override def retrieveUploadedFileDetails(
    objectLocation: S3ObjectLocation): Future[WithCheckpoints[SuccessfulProcessingDetails]] = {
    implicit val ld = LoggingDetails.fromS3ObjectLocation(objectLocation)

    for {
      metadata <- fileManager.receiveSuccessfulFileDetails(objectLocation)
      downloadUrl = downloadUrlGenerator.generate(objectLocation, metadata)
    } yield {
      val checkpoints = parseCheckpoints(metadata.userMetadata)
      val retrieved =
        SuccessfulProcessingDetails(
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
      Logger.debug(
        s"Retrieved file with callbackUrl: [${retrieved.callbackUrl}], for objectKey: [${objectLocation.objectKey}].")
      WithCheckpoints(retrieved, Checkpoints(checkpoints))
    }
  }

  override def retrieveQuarantinedFileDetails(
    objectLocation: S3ObjectLocation): Future[WithCheckpoints[FailedProcessingDetails]] = {
    implicit val ld = LoggingDetails.fromS3ObjectLocation(objectLocation)

    for {
      quarantineFile <- fileManager.receiveFailedFileDetails(objectLocation)
    } yield {
      val checkpoints = parseCheckpoints(quarantineFile.userMetadata)
      val retrieved =
        FailedProcessingDetails(
          callbackUrl     = quarantineFile.callbackUrl,
          reference       = quarantineFile.fileReference,
          fileName        = quarantineFile.fileName,
          uploadTimestamp = quarantineFile.uploadTimestamp,
          error           = parseContents(quarantineFile.failureDetailsAsJson),
          requestContext  = quarantineFile.requestContext
        )
      Logger.debug(
        s"Retrieved quarantined file with callbackUrl: [${retrieved.callbackUrl}], for objectKey: [${objectLocation.objectKey}].")
      WithCheckpoints(retrieved, Checkpoints(checkpoints))
    }
  }

  private def parseCheckpoints(userMetadata: Map[String, String]) =
    userMetadata
      .filterKeys(_.startsWith("x-amz-meta-upscan-"))
      .flatMap {
        case (key, value) =>
          Try(Instant.parse(value)) match {
            case Success(parsedTimestamp) => Some(Checkpoint(key, parsedTimestamp))
            case Failure(exception) =>
              Logger.warn(s"Checkpoint field $key has invalid format", exception)
              None
          }
      }
      .toSeq

  private def parseContents(contents: String): ErrorDetails = {
    def unknownError(): ErrorDetails = ErrorDetails("UNKNOWN", contents)

    Try(Json.parse(contents)) match {
      case Success(json) =>
        json.validate[ErrorDetails] match {
          case JsSuccess(details, _) => details
          case _: JsError            => unknownError()
        }
      case Failure(_) => unknownError()
    }
  }
}
