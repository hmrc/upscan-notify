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

package connectors.aws

import java.net.URL
import javax.inject.Inject

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.ObjectMetadata
import config.ServiceConfiguration
import model.{S3ObjectLocation, UploadedFile}
import services.{DownloadUrlGenerator, FileNotificationDetailsRetriever}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class S3FileNotificationDetailsRetriever @Inject()(
  s3Client: AmazonS3,
  config: ServiceConfiguration,
  downloadUrlGenerator: DownloadUrlGenerator)(implicit ec: ExecutionContext)
    extends FileNotificationDetailsRetriever {

  private val metadataKey = config.callbackUrlMetadataKey

  override def retrieveUploadedFileDetails(objectLocation: S3ObjectLocation): Future[UploadedFile] =
    for {
      metadata     <- Future(s3Client.getObjectMetadata(objectLocation.bucket, objectLocation.objectKey))
      uploadedFile <- retrieveUploadedFile(metadata, objectLocation)
    } yield uploadedFile

  private def retrieveUploadedFile(metadata: ObjectMetadata, objectLocation: S3ObjectLocation): Future[UploadedFile] =
    metadata.getUserMetadata.asScala.get(metadataKey) match {
      case Some(callbackMetadata) =>
        Try(new URL(callbackMetadata)) match {
          case Success(callbackUrl) =>
            val downloadUrl = downloadUrlGenerator.generate(objectLocation)
            Future.successful(UploadedFile(callbackUrl, objectLocation.objectKey, downloadUrl))
          case Failure(error) =>
            Future.failed(new IllegalArgumentException(
              s"Invalid metadata: $metadataKey: $callbackMetadata for file: ${objectLocation.objectKey}. Error: $error"))
        }
      case None =>
        Future.failed(
          new NoSuchElementException(s"Metadata not found: $metadataKey for file: ${objectLocation.objectKey}"))
    }
}
