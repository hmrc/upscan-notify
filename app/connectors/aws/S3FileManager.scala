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
import com.amazonaws.services.s3.model.ObjectMetadata
import model.{RequestContext, S3ObjectLocation, UploadDetails}
import org.apache.commons.io.IOUtils
import play.api.Logger
import services._
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails
import util.logging.LoggingDetails

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class S3FileManager @Inject()(s3Client: AmazonS3) extends FileManager {

  override def retrieveReadyMetadata(objectLocation: S3ObjectLocation): Future[ReadyObjectMetadata] = {
    implicit val ld = LoggingDetails.fromS3ObjectLocation(objectLocation)

    for {
      metadata       <- Future(s3Client.getObjectMetadata(objectLocation.bucket, objectLocation.objectKey))
      parsedMetadata <- Future.fromTry(parseReadyObjectMetadata(metadata, objectLocation))
    } yield {
      parsedMetadata
    }
  }

  private def parseReadyObjectMetadata(metadata: ObjectMetadata, objectLocation: S3ObjectLocation) = {
    val userMetadata = S3ObjectMetadata(metadata, objectLocation)
    for {
      callbackUrl   <- retrieveCallbackUrl(userMetadata)
      uploadDetails <- retrieveUploadDetails(userMetadata)
      requestContext = retrieveUserContext(userMetadata)
    } yield {
      ReadyObjectMetadata(
        callbackUrl,
        uploadDetails,
        metadata.getContentLength,
        requestContext.requestId,
        requestContext.sessionId)
    }
  }

  override def retrieveFailedObject(objectLocation: S3ObjectLocation): Future[FailedObjectWithMetadata] = {
    implicit val ld = LoggingDetails.fromS3ObjectLocation(objectLocation)

    for {
      s3Object <- Future(s3Client.getObject(objectLocation.bucket, objectLocation.objectKey))
      content  <- Future.fromTry(Try(IOUtils.toString(s3Object.getObjectContent)))
      metadata = S3ObjectMetadata(s3Object.getObjectMetadata, objectLocation)
      callbackUrl <- Future.fromTry(retrieveCallbackUrl(metadata))
      requestContext = retrieveUserContext(metadata)
    } yield {
      Logger.debug(s"Fetched object with metadata for objectKey: [${objectLocation.objectKey}].")
      val failedObjectMetadata = FailedObjectMetadata(
        callbackUrl,
        s3Object.getObjectMetadata.getContentLength,
        requestContext.requestId,
        requestContext.sessionId)
      FailedObjectWithMetadata(content, failedObjectMetadata)
    }
  }

  private def retrieveCallbackUrl(metadata: S3ObjectMetadata): Try[URL] =
    metadata.get("callback-url", { new URL(_) })

  private def retrieveUserContext(metadata: S3ObjectMetadata): RequestContext =
    RequestContext(metadata.get("request-id").toOption, metadata.get("session-id").toOption)

  private def retrieveUploadDetails(metadata: S3ObjectMetadata): Try[UploadDetails] =
    for {
      uploadTimestamp <- metadata.get("initiate-date", Instant.parse)
      checksum        <- metadata.get("checksum")
      fileName        <- metadata.get("original-filename")
      mimeType        <- metadata.get("mime-type")
    } yield UploadDetails(fileName, mimeType, uploadTimestamp, checksum)

}

case class S3ObjectMetadata(underlying: ObjectMetadata, location: S3ObjectLocation) {

  private val userMetadata = underlying.getUserMetadata.asScala.toMap

  def get(key: String): Try[String] =
    userMetadata.get(key) match {
      case Some(metadataValue) => Success(metadataValue)
      case None =>
        Failure(new NoSuchElementException(s"Metadata not found: [$key], for objectKey: [${location.objectKey}]."))
    }

  def get[T](key: String, parseFunc: String => T): Try[T] =
    userMetadata.get(key) match {
      case Some(metadataValue) => parse(metadataValue, parseFunc, key)
      case None =>
        Failure(new NoSuchElementException(s"Metadata not found: [$key], for objectKey: [${location.objectKey}]."))
    }

  private def parse[T](originalValue: String, parsingFunction: String => T, key: String): Try[T] =
    Try(parsingFunction(originalValue)) match {
      case Success(parsedValue) => Success(parsedValue)
      case Failure(error) =>
        Failure(
          new Exception(
            s"Invalid metadata: [$key: $originalValue], for objectKey: [${location.objectKey}]. Error: $error"
          ))
    }

}
