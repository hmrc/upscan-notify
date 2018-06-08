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
import java.time.Instant
import javax.inject.Inject

import com.amazonaws.services.s3.AmazonS3
import model.{S3ObjectLocation, UploadDetails}
import org.apache.commons.io.IOUtils
import play.api.Logger
import services._
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails
import util.logging.LoggingDetails

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class S3FileManager @Inject()(s3Client: AmazonS3) extends FileManager {

  private val metadataKeyCallbackUrl = "callback-url"

  private val metadataKeyInitiateDate = "initiate-date"

  private val metadataKeyChecksum = "checksum"

  override def retrieveReadyMetadata(objectLocation: S3ObjectLocation): Future[ReadyObjectMetadata] = {
    implicit val ld = LoggingDetails.fromS3ObjectLocation(objectLocation)

    for {
      metadata      <- Future(s3Client.getObjectMetadata(objectLocation.bucket, objectLocation.objectKey))
      callbackUrl   <- Future.fromTry(retrieveCallbackUrl(metadata.getUserMetadata.asScala.toMap, objectLocation))
      uploadDetails <- Future.fromTry(retrieveUploadDetails(metadata.getUserMetadata.asScala.toMap, objectLocation))
    } yield {
      ReadyObjectMetadata(callbackUrl, uploadDetails.uploadTimestamp, uploadDetails.checksum, metadata.getContentLength)
    }
  }

  override def retrieveReadyObject(objectLocation: S3ObjectLocation): Future[ReadyObjectWithMetadata] = {
    implicit val ld = LoggingDetails.fromS3ObjectLocation(objectLocation)

    for {
      s3Object <- Future(s3Client.getObject(objectLocation.bucket, objectLocation.objectKey))
      content  <- Future.fromTry(Try(IOUtils.toString(s3Object.getObjectContent)))
      metadata = s3Object.getObjectMetadata.getUserMetadata.asScala.toMap
      callbackUrl   <- Future.fromTry(retrieveCallbackUrl(metadata, objectLocation))
      uploadDetails <- Future.fromTry(retrieveUploadDetails(metadata, objectLocation))
    } yield {
      Logger.debug(s"Fetched object with metadata for objectKey: [${objectLocation.objectKey}].")
      val readyObjectMetadata = ReadyObjectMetadata(
        callbackUrl,
        uploadDetails.uploadTimestamp,
        uploadDetails.checksum,
        s3Object.getObjectMetadata.getContentLength)

      ReadyObjectWithMetadata(content, readyObjectMetadata)
    }
  }

  override def retrieveFailedMetadata(objectLocation: S3ObjectLocation): Future[FailedObjectMetadata] = {
    implicit val ld = LoggingDetails.fromS3ObjectLocation(objectLocation)

    for {
      metadata    <- Future(s3Client.getObjectMetadata(objectLocation.bucket, objectLocation.objectKey))
      callbackUrl <- Future.fromTry(retrieveCallbackUrl(metadata.getUserMetadata.asScala.toMap, objectLocation))
    } yield {
      FailedObjectMetadata(callbackUrl, metadata.getContentLength)
    }
  }

  override def retrieveFailedObject(objectLocation: S3ObjectLocation): Future[FailedObjectWithMetadata] = {
    implicit val ld = LoggingDetails.fromS3ObjectLocation(objectLocation)

    for {
      s3Object <- Future(s3Client.getObject(objectLocation.bucket, objectLocation.objectKey))
      content  <- Future.fromTry(Try(IOUtils.toString(s3Object.getObjectContent)))
      metadata = s3Object.getObjectMetadata.getUserMetadata.asScala.toMap
      callbackUrl <- Future.fromTry(retrieveCallbackUrl(metadata, objectLocation))
    } yield {
      Logger.debug(s"Fetched object with metadata for objectKey: [${objectLocation.objectKey}].")
      val failedObjectMetadata = FailedObjectMetadata(callbackUrl, s3Object.getObjectMetadata.getContentLength)
      FailedObjectWithMetadata(content, failedObjectMetadata)
    }
  }

  private def retrieveCallbackUrl(metadata: Map[String, String], location: S3ObjectLocation): Try[URL] =
    retrieveAndParseMetadata(metadata, { new URL(_) }, metadataKeyCallbackUrl, location)

  private def retrieveUploadDetails(metadata: Map[String, String], location: S3ObjectLocation): Try[UploadDetails] =
    for {
      uploadTimestamp <- retrieveAndParseMetadata(metadata, { Instant.parse(_) }, metadataKeyInitiateDate, location)
      checksum        <- retrieveMetadata(metadata, metadataKeyChecksum, location)
    } yield UploadDetails(uploadTimestamp, checksum)

  private def retrieveMetadata(metadata: Map[String, String], key: String, location: S3ObjectLocation): Try[String] =
    metadata.get(key) match {
      case Some(metadataValue) => Success(metadataValue)
      case None =>
        Failure(new NoSuchElementException(s"Metadata not found: [$key], for objectKey: [${location.objectKey}]."))
    }

  private def retrieveAndParseMetadata[T](
    metadata: Map[String, String],
    parseFunc: String => T,
    key: String,
    location: S3ObjectLocation): Try[T] =
    metadata.get(key) match {
      case Some(metadataValue) => parseMetadata(metadataValue, parseFunc, key, location)
      case None =>
        Failure(new NoSuchElementException(s"Metadata not found: [$key], for objectKey: [${location.objectKey}]."))
    }

  private def parseMetadata[T](
    originalValue: String,
    parse: String => T,
    key: String,
    location: S3ObjectLocation): Try[T] =
    Try(parse(originalValue)) match {
      case Success(parsedValue) => Success(parsedValue)
      case Failure(error) =>
        Failure(
          new Exception(
            s"Invalid metadata: [$key: $originalValue], for objectKey: [${location.objectKey}]. Error: $error"
          ))
    }
}
