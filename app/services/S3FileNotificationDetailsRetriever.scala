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

package services

import java.net.URL

import config.ServiceConfiguration
import javax.inject.Inject
import model.{FileReference, QuarantinedFile, S3ObjectLocation, UploadedFile}
import play.api.Logger

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails
import util.logging.LoggingDetails

class S3FileNotificationDetailsRetriever @Inject()(
  fileManager: FileManager,
  config: ServiceConfiguration,
  downloadUrlGenerator: DownloadUrlGenerator)
    extends FileNotificationDetailsRetriever {

  private val metadataKey = config.callbackUrlMetadataKey

  override def retrieveUploadedFileDetails(objectLocation: S3ObjectLocation): Future[UploadedFile] = {
    implicit val ld = LoggingDetails.fromS3ObjectLocation(objectLocation)

    for {
      metadata <- fileManager.retrieveMetadata(objectLocation)
      callbackUrl <- Future.fromTry(retrieveCallbackUrl(metadata, objectLocation))
      downloadUrl = downloadUrlGenerator.generate(objectLocation)
    } yield {
      val retrieved = UploadedFile(callbackUrl, FileReference(objectLocation.objectKey), downloadUrl, metadata.size)
      Logger.debug(s"Retrieved file with callbackUrl: [${retrieved.callbackUrl}], for objectKey: [${objectLocation.objectKey}].")
      retrieved
    }
  }

  override def retrieveQuarantinedFileDetails(objectLocation: S3ObjectLocation): Future[QuarantinedFile] = {
    implicit val ld = LoggingDetails.fromS3ObjectLocation(objectLocation)

    for {
      quarantineFile <- fileManager.retrieveObject(objectLocation)
      callbackUrl <- Future.fromTry(retrieveCallbackUrl(quarantineFile.metadata, objectLocation))
    } yield {
      val retrieved = QuarantinedFile(callbackUrl, FileReference(objectLocation.objectKey), quarantineFile.content)
      Logger.debug(s"Retrieved quarantined file with callbackUrl: [${retrieved.callbackUrl}], for objectKey: [${objectLocation.objectKey}].")
      retrieved
    }
  }

  private def retrieveCallbackUrl(metadata: ObjectMetadata, objectLocation: S3ObjectLocation): Try[URL] =
    metadata.userMetadata.get(metadataKey) match {
      case Some(callbackMetadata) =>
        Try(new URL(callbackMetadata)) match {
          case Success(callbackUrl) => Success(callbackUrl)
          case Failure(error) =>
            Failure(new Exception(
              s"Invalid metadata: [$metadataKey: $callbackMetadata], for objectKey: [${objectLocation.objectKey}]. Error: $error",
              error))
        }
      case None =>
        Failure(new NoSuchElementException(s"Metadata not found: [$metadataKey], for objectKey: [${objectLocation.objectKey}]."))
    }
}
