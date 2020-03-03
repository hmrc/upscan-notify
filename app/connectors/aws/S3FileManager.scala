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

package connectors.aws

import java.net.URL
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.ObjectMetadata
import javax.inject.Inject
import model.{FileReference, RequestContext, S3ObjectLocation}
import org.apache.commons.io.IOUtils
import play.api.Logger
import services._

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class S3FileManager @Inject()(s3Client: AmazonS3)(implicit ec: ExecutionContext) extends FileManager {

  override def receiveSuccessfulFileDetails(objectLocation: S3ObjectLocation): Future[SuccessfulFileDetails] =
    for {
      metadata       <- Future(s3Client.getObjectMetadata(objectLocation.bucket, objectLocation.objectKey))
      parsedMetadata <- Future.fromTry(parseReadyObjectMetadata(metadata, objectLocation))
    } yield {
      parsedMetadata
    }

  private def parseReadyObjectMetadata(metadata: ObjectMetadata, objectLocation: S3ObjectLocation): Try[SuccessfulFileDetails] = {
    val userMetadata = S3ObjectMetadata(metadata, objectLocation)

    for {
      callbackUrl      <- retrieveCallbackUrl(userMetadata)
      fileReference    <- userMetadata.get("file-reference", FileReference.apply)
      uploadDetails    <- parseSuccessfulFileMetadata(userMetadata)
      requestContext   <- retrieveUserContext(userMetadata)
      consumingService <- retrieveConsumingService(userMetadata)
    } yield {
      SuccessfulFileDetails(
        fileReference    = fileReference,
        callbackUrl      = callbackUrl,
        fileName         = uploadDetails.fileName,
        fileMimeType     = uploadDetails.fileMimeType,
        uploadTimestamp  = uploadDetails.uploadTimestamp,
        checksum         = uploadDetails.checksum,
        size             = metadata.getContentLength,
        requestContext   = requestContext,
        consumingService = consumingService,
        userMetadata     = metadata.getUserMetadata.asScala.toMap
      )
    }
  }

  override def receiveFailedFileDetails(objectLocation: S3ObjectLocation): Future[FailedFileDetails] =
    for {
      s3Object <- Future(s3Client.getObject(objectLocation.bucket, objectLocation.objectKey))
      content  <- Future.fromTry(Try(IOUtils.toString(s3Object.getObjectContent, UTF_8)))
      metadata = S3ObjectMetadata(s3Object.getObjectMetadata, objectLocation)
      callbackUrl    <- Future.fromTry(retrieveCallbackUrl(metadata))
      fileReference  <- Future.fromTry(metadata.get("file-reference", FileReference.apply))
      uploadDetails  <- Future.fromTry(parseFailedFileMetadata(metadata))
      requestContext <- Future.fromTry(retrieveUserContext(metadata))
    } yield {
      Logger.debug(s"Fetched object with metadata for objectKey: [${objectLocation.objectKey}].")
      FailedFileDetails(
        fileReference   = fileReference,
        callbackUrl     = callbackUrl,
        fileName        = uploadDetails.fileName,
        uploadTimestamp = uploadDetails.uploadTimestamp,
        size            = metadata.underlying.getContentLength,
        requestContext  = requestContext,
        userMetadata    = metadata.underlying.getUserMetadata.asScala.toMap,
        content
      )
    }

  private def retrieveCallbackUrl(metadata: S3ObjectMetadata): Try[URL] =
    metadata.get("callback-url", { new URL(_) })

  private def retrieveUserContext(metadata: S3ObjectMetadata): Try[RequestContext] =
    for {
      clientIp <- metadata.get("client-ip")
    } yield {
      RequestContext(metadata.get("request-id").toOption, metadata.get("session-id").toOption, clientIp)
    }

  private def retrieveConsumingService(metadata: S3ObjectMetadata): Try[String] =
    metadata.get("consuming-service")

  private def parseSuccessfulFileMetadata(metadata: S3ObjectMetadata): Try[SuccessfulFileMetadata] =
    for {
      uploadTimestamp <- metadata.get("initiate-date", Instant.parse)
      checksum        <- metadata.get("checksum")
      fileName        <- metadata.get("original-filename")
      mimeType        <- metadata.get("mime-type")
    } yield SuccessfulFileMetadata(fileName, mimeType, uploadTimestamp, checksum)

  private def parseFailedFileMetadata(metadata: S3ObjectMetadata): Try[FailedFileMetadata] =
    for {
      uploadTimestamp <- metadata.get("initiate-date", Instant.parse)
      fileName        <- metadata.get("original-filename")
    } yield FailedFileMetadata(fileName, uploadTimestamp)

}

case class SuccessfulFileMetadata(fileName: String, fileMimeType: String, uploadTimestamp: Instant, checksum: String)
case class FailedFileMetadata(fileName: String, uploadTimestamp: Instant)

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
