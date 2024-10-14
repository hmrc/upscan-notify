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

package services

import model._
import play.api.Logging
import play.api.libs.json.{JsError, JsSuccess, Json}
import util.logging.WithLoggingDetails.withLoggingDetails
import util.logging.LoggingDetails

import java.time.Instant
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class S3FileNotificationDetailsRetriever @Inject()(
  fileManager         : FileManager,
  downloadUrlGenerator: DownloadUrlGenerator
)(implicit
  ec: ExecutionContext
) extends FileNotificationDetailsRetriever with Logging:

  override def retrieveUploadedFileDetails(objectLocation: S3ObjectLocation): Future[WithCheckpoints[SuccessfulProcessingDetails]] =
    for
      metadata <- fileManager.receiveSuccessfulFileDetails(objectLocation)
      downloadUrl = downloadUrlGenerator.generate(objectLocation, metadata)
    yield
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
      withLoggingDetails(LoggingDetails.fromFileReference(retrieved.reference)):
        logger.debug:
          s"Retrieved file with Key=[${retrieved.reference.reference}] and callbackUrl=[${retrieved.callbackUrl}] for object=[${objectLocation.objectKey}]."
      WithCheckpoints(retrieved, Checkpoints(checkpoints))

  override def retrieveQuarantinedFileDetails(objectLocation: S3ObjectLocation): Future[WithCheckpoints[FailedProcessingDetails]] =
    for
      quarantineFile <- fileManager.receiveFailedFileDetails(objectLocation)
    yield
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
      withLoggingDetails(LoggingDetails.fromFileReference(retrieved.reference))
        logger.debug:
          s"Retrieved quarantined file with Key=[${retrieved.reference.reference}] and callbackUrl=[${retrieved.callbackUrl}] for object=[${objectLocation.objectKey}]."
      WithCheckpoints(retrieved, Checkpoints(checkpoints))

  private def parseCheckpoints(userMetadata: Map[String, String]) =
    userMetadata
      .view
      .filterKeys(_.startsWith("x-amz-meta-upscan-"))
      .flatMap:
        case (key, value) =>
          Try(Instant.parse(value)) match
            case Success(parsedTimestamp) =>
              Some(Checkpoint(key, parsedTimestamp))
            case Failure(exception)       =>
              logger.warn(s"Checkpoint field $key has invalid format", exception)
              None
      .toSeq

  private def parseContents(contents: String): ErrorDetails =
    def unknownError(): ErrorDetails = ErrorDetails("UNKNOWN", contents)

    Try(Json.parse(contents)) match
      case Success(json) =>
        json.validate[ErrorDetails] match
          case JsSuccess(details, _) => details
          case _: JsError            => unknownError()
      case Failure(_)   =>
        unknownError()
