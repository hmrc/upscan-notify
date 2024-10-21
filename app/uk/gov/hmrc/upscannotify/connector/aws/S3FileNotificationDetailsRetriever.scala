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

package uk.gov.hmrc.upscannotify.connector.aws

import jakarta.mail.internet.MimeUtility
import play.api.Logging
import uk.gov.hmrc.upscannotify.model.{FileReference, RequestContext, S3ObjectLocation}
import uk.gov.hmrc.upscannotify.service._

import java.net.URL
import java.time.Instant
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class S3FileNotificationDetailsRetriever @Inject()(
  s3FileManager: S3FileManager
)(using
  ExecutionContext
) extends FileNotificationDetailsRetriever with Logging:

  override def receiveSuccessfulFileDetails(objectLocation: S3ObjectLocation): Future[SuccessfulFileDetails] =
    for
      metadata         <- s3FileManager.getObjectMetadata(objectLocation)
      _                =  logger.info(s"retrieved metadata for $objectLocation: $metadata")
      callbackUrl      <- Future.fromTry(retrieveCallbackUrl(metadata))
      fileReference    <- Future.fromTry(metadata.get("file-reference", FileReference.apply))
      uploadDetails    <- Future.fromTry(parseSuccessfulFileMetadata(metadata))
      requestContext   <- Future.fromTry(retrieveUserContext(metadata))
      consumingService <- Future.fromTry(retrieveConsumingService(metadata))
    yield
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
        userMetadata     = metadata.items
      )

  override def receiveFailedFileDetails(objectLocation: S3ObjectLocation): Future[FailedFileDetails] =
    for
      (metadata, content) <- s3FileManager.getObject(objectLocation)
      callbackUrl         <- Future.fromTry(retrieveCallbackUrl(metadata))
      fileReference       <- Future.fromTry(metadata.get("file-reference", FileReference.apply))
      uploadDetails       <- Future.fromTry(parseFailedFileMetadata(metadata))
      requestContext      <- Future.fromTry(retrieveUserContext(metadata))
    yield
      FailedFileDetails(
        fileReference   = fileReference,
        callbackUrl     = callbackUrl,
        fileName        = uploadDetails.fileName,
        uploadTimestamp = uploadDetails.uploadTimestamp,
        size            = metadata.getContentLength,
        requestContext  = requestContext,
        userMetadata    = metadata.items,
        content
      )

  private def retrieveCallbackUrl(metadata: S3ObjectMetadata): Try[URL] =
    metadata.get("callback-url", URL(_))

  private def retrieveUserContext(metadata: S3ObjectMetadata): Try[RequestContext] =
    for
      clientIp <- metadata.get("client-ip")
    yield
      RequestContext(metadata.get("request-id").toOption, metadata.get("session-id").toOption, clientIp)

  private def retrieveConsumingService(metadata: S3ObjectMetadata): Try[String] =
    metadata.get("consuming-service")

  private def parseSuccessfulFileMetadata(metadata: S3ObjectMetadata): Try[SuccessfulFileMetadata] =
    for
      uploadTimestamp <- metadata.get("initiate-date", Instant.parse)
      checksum        <- metadata.get("checksum")
      fileName        <- parseOriginalFileName(metadata)
      mimeType        <- metadata.get("mime-type")
    yield SuccessfulFileMetadata(fileName, mimeType, uploadTimestamp, checksum)

  private def parseFailedFileMetadata(metadata: S3ObjectMetadata): Try[FailedFileMetadata] =
    for
      uploadTimestamp <- metadata.get("initiate-date", Instant.parse)
      fileName        <- parseOriginalFileName(metadata)
    yield FailedFileMetadata(fileName, uploadTimestamp)

  private def parseOriginalFileName(metadata: S3ObjectMetadata): Try[String] =
    val fileName = metadata.get("original-filename")
    fileName.flatMap(f => Try(MimeUtility.decodeText(f))).orElse(fileName)

case class SuccessfulFileMetadata(
  fileName       : String,
  fileMimeType   : String,
  uploadTimestamp: Instant,
  checksum       : String
)

case class FailedFileMetadata(
  fileName       : String,
  uploadTimestamp: Instant
)

case class S3ObjectMetadata(
  objectLocation   : S3ObjectLocation,
  items            : Map[String, String],
  uploadedTimestamp: Instant, // used?
  getContentLength : Long // TODO rename to contentLength
):
  def get(key: String): Try[String] =
    items.get(key) match
      case Some(metadataValue) => Success(metadataValue)
      case None                => Failure(new NoSuchElementException(s"Metadata not found: [$key] for object=[${objectLocation.objectKey}]."))

  def get[T](key: String, parseFunc: String => T): Try[T] =
    items.get(key) match
      case Some(metadataValue) => parse(metadataValue, parseFunc, key)
      case None                => Failure(new NoSuchElementException(s"Metadata not found: [$key] for object=[${objectLocation.objectKey}]."))

  private def parse[T](originalValue: String, parsingFunction: String => T, key: String): Try[T] =
    Try(parsingFunction(originalValue)) match
      case Success(parsedValue) => Success(parsedValue)
      case Failure(error)       => Failure:
                                    Exception:
                                      s"Invalid metadata: [$key: $originalValue] for object=[${objectLocation.objectKey}]. Error: $error"
