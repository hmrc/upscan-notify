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
import model.{QuarantinedFile, S3ObjectLocation, UploadedFile}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class S3FileNotificationDetailsRetriever @Inject()(
  fileManager: FileManager,
  config: ServiceConfiguration,
  downloadUrlGenerator: DownloadUrlGenerator)(implicit ec: ExecutionContext)
    extends FileNotificationDetailsRetriever {

  private val metadataKey = config.callbackUrlMetadataKey

  override def retrieveUploadedFileDetails(objectLocation: S3ObjectLocation): Future[UploadedFile] =
    for {
      metadata    <- fileManager.retrieveMetadata(objectLocation)
      callbackUrl <- Future.fromTry(retrieveCallbackUrl(metadata, objectLocation))
      downloadUrl = downloadUrlGenerator.generate(objectLocation)
    } yield UploadedFile(callbackUrl, objectLocation.objectKey, downloadUrl, metadata.size)

  override def retrieveQuarantinedFileDetails(objectLocation: S3ObjectLocation): Future[QuarantinedFile] =
    for {
      quarantineFile <- fileManager.retrieveObject(objectLocation)
      callbackUrl    <- Future.fromTry(retrieveCallbackUrl(quarantineFile.metadata, objectLocation))
    } yield QuarantinedFile(callbackUrl, objectLocation.objectKey, quarantineFile.content)

  private def retrieveCallbackUrl(metadata: ObjectMetadata, objectLocation: S3ObjectLocation): Try[URL] =
    metadata.userMetadata.get(metadataKey) match {
      case Some(callbackMetadata) =>
        Try(new URL(callbackMetadata)) match {
          case Success(callbackUrl) => Success(callbackUrl)
          case Failure(error) =>
            Failure(new Exception(
              s"Invalid metadata: $metadataKey: $callbackMetadata for file: ${objectLocation.objectKey}. Error: $error",
              error))
        }
      case None =>
        Failure(new NoSuchElementException(s"Metadata not found: $metadataKey for file: ${objectLocation.objectKey}"))
    }
}
