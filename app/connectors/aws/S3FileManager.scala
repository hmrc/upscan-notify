/*
 * Copyright 2019 HM Revenue & Customs
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
import java.util.concurrent.Executors

import cats.effect.{ContextShift, IO}
import cats.implicits._
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.ObjectMetadata
import javax.inject.Inject
import model._
import org.apache.commons.io.IOUtils
import play.api.Logger
import services._
import util.logging.LoggingDetails

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

class S3FileManager @Inject()(s3Client: AmazonS3)(implicit contextShift: ContextShift[IO]) extends FileManager[IO] {

  private val s3ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(8))

  override def retrieveReadyMetadata(objectLocation: S3ObjectLocation): IO[ReadyObjectMetadata] = {
    implicit val ld = LoggingDetails.fromS3ObjectLocation(objectLocation)

    for {
      metadata <- contextShift.evalOn(s3ExecutionContext) {
                   IO.delay {
                     s3Client.getObjectMetadata(objectLocation.bucket, objectLocation.objectKey)
                   }
                 }
      parsedMetadata <- IO.fromEither(Either.fromTry(parseReadyObjectMetadata(metadata, objectLocation)))
    } yield {
      parsedMetadata
    }
  }

  private def parseReadyObjectMetadata(metadata: ObjectMetadata, objectLocation: S3ObjectLocation) = {
    val userMetadata = S3ObjectMetadata(metadata, objectLocation)

    for {
      callbackUrl      <- retrieveCallbackUrl(userMetadata)
      fileReference    <- userMetadata.get("file-reference", FileReference.apply)
      uploadDetails    <- retrieveValidUploadDetails(userMetadata)
      requestContext   <- retrieveUserContext(userMetadata)
      consumingService <- retrieveConsumingService(userMetadata)
    } yield {
      ReadyObjectMetadata(
        fileReference,
        callbackUrl,
        uploadDetails,
        metadata.getContentLength,
        requestContext,
        consumingService,
        metadata.getUserMetadata().asScala.toMap)
    }
  }

  override def retrieveFailedObject(objectLocation: S3ObjectLocation): IO[FailedObjectWithMetadata] = {
    implicit val ld = LoggingDetails.fromS3ObjectLocation(objectLocation)

    for {
      s3Object <- contextShift.evalOn(s3ExecutionContext) {
                   IO.delay {
                     s3Client.getObject(objectLocation.bucket, objectLocation.objectKey)
                   }
                 }
      content <- contextShift.evalOn(s3ExecutionContext) {
                  IO.delay {
                    IOUtils.toString(s3Object.getObjectContent)
                  }
                }
      metadata = S3ObjectMetadata(s3Object.getObjectMetadata, objectLocation)
      callbackUrl    <- IO.fromEither(Either.fromTry(retrieveCallbackUrl(metadata)))
      fileReference  <- IO.fromEither(Either.fromTry(metadata.get("file-reference", FileReference.apply)))
      uploadDetails  <- IO.fromEither(Either.fromTry(retrieveInvalidUploadDetails(metadata)))
      requestContext <- IO.fromEither(Either.fromTry(retrieveUserContext(metadata)))
    } yield {
      Logger.debug(s"Fetched object with metadata for objectKey: [${objectLocation.objectKey}].")

      val failedObjectMetadata =
        FailedObjectMetadata(
          fileReference,
          callbackUrl,
          uploadDetails,
          metadata.underlying.getContentLength,
          requestContext,
          metadata.underlying.getUserMetadata().asScala.toMap)

      FailedObjectWithMetadata(content, failedObjectMetadata)
    }
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

  private def retrieveValidUploadDetails(metadata: S3ObjectMetadata): Try[UploadDetails] =
    for {
      uploadTimestamp <- metadata.get("initiate-date", Instant.parse)
      checksum        <- metadata.get("checksum")
      fileName        <- metadata.get("original-filename")
      mimeType        <- metadata.get("mime-type")
    } yield ValidUploadDetails(fileName, mimeType, uploadTimestamp, checksum)

  private def retrieveInvalidUploadDetails(metadata: S3ObjectMetadata): Try[UploadDetails] =
    for {
      uploadTimestamp <- metadata.get("initiate-date", Instant.parse)
      fileName        <- metadata.get("original-filename")
    } yield InvalidUploadDetails(fileName, uploadTimestamp)

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
