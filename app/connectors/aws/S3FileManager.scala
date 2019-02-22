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

import javax.inject.Inject
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.ObjectMetadata
import model.{FileReference, InvalidUploadDetails, RequestContext, S3ObjectLocation, UploadDetails, ValidUploadDetails}
import org.apache.commons.io.IOUtils
import services._
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails
import util.logging.LoggingDetails

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class S3FileManager @Inject()(s3Client: AmazonS3, downloadUrlGenerator: DownloadUrlGenerator) extends FileManager {

/*
  private def retrieveMetadata(userMetadata: S3ObjectMetadata) : Try[services.UploadedFileMetadata] = {
    for {
      callbackUrl      <- retrieveCallbackUrl(userMetadata)
      fileReference    <- userMetadata.get("file-reference", FileReference.apply)
      uploadDetails    <- retrieveUploadDetails(userMetadata)
      requestContext   <- retrieveUserContext(userMetadata)
    } yield services.UploadedFileMetadata(fileReference, callbackUrl, uploadDetails, userMetadata.underlying.getContentLength,
      requestContext, userMetadata.userMetadata)
  }
*/
/*
  private def _retrieveMetadata[E](userMetadata: S3ObjectMetadata, block: S3ObjectMetadata => Future[E]) : Future[(services.UploadedFileMetadata, E)] = {
    for {
      callbackUrl      <- Future.fromTry(retrieveCallbackUrl(userMetadata))
      fileReference    <- Future.fromTry(userMetadata.get("file-reference", FileReference.apply))
      uploadDetails    <- Future.fromTry(retrieveUploadDetails(userMetadata))
      requestContext   <- Future.fromTry(retrieveUserContext(userMetadata))
      enriched         <- block(userMetadata)
    } yield (services.UploadedFileMetadata(fileReference, callbackUrl, uploadDetails, userMetadata.underlying.getContentLength,
      requestContext, userMetadata.userMetadata), enriched)
  }
*/
  /**
    * Retrieve the common UploadedFileMetadata value.
    * Also extract use-case specific fields, such as downloadUrl, by delegating to the supplied `extracter`.
    * Finally assemble both of the above into a model, by delegating to the supplied `assembler`, and return it.
    *
    * @param userMetadata
    * @param extracter extracts use-case specific fields that will be included in the returned model.
    *              e.g. downloadUrl (for ready path) or errorMessage (for quarantined path).
    * @param assembler function to assemble the common UploadedFileMetadata and use-case specific fields
    *                  (e.g. downloadUrl) into the final model value returned from this method.
    * @tparam E the extracted data that will be assembled into the returned model, M, along with the common UploadedFileMetadata values.
    * @tparam M the returned model, which wraps UploadedFileMetadata with the use-case specific extracted fields from above.
    * @return the model encapsulating common fields in UploadedFileMetadata, and use-case specific fields, e.g. downloadUrl.
    */
  private def __retrieveMetadata[E,M](userMetadata: S3ObjectMetadata,
                                      extracter: S3ObjectMetadata => Future[E],
                                      assembler: (services.UploadedFileMetadata, E) => M) : Future[M] = {
    for {
      callbackUrl      <- Future.fromTry(retrieveCallbackUrl(userMetadata))
      fileReference    <- Future.fromTry(userMetadata.get("file-reference", FileReference.apply))
      uploadDetails    <- Future.fromTry(retrieveUploadDetails(userMetadata))
      requestContext   <- Future.fromTry(retrieveUserContext(userMetadata))
      extracted        <- extracter(userMetadata)
    } yield {
      assembler(
        services.UploadedFileMetadata(
          fileReference, callbackUrl, uploadDetails, userMetadata.underlying.getContentLength,
          requestContext, userMetadata.userMetadata
        ),
        extracted)
    }
  }

  private def assembleUploadedFileMetadataAndDetails[T,R](objectLocation: S3ObjectLocation,
                                                          extractor: S3ObjectMetadata => Future[T],
                                                          assembler: (services.UploadedFileMetadata, T) => R) : Future[R] = {
    implicit val ld = LoggingDetails.fromS3ObjectLocation(objectLocation)

    for {
      metadata  <- Future(s3Client.getObjectMetadata(objectLocation.bucket, objectLocation.objectKey))
      model     <- __retrieveMetadata(S3ObjectMetadata(metadata, objectLocation), extractor, assembler)

    } yield model
  }

  override def retrieveReadyMetadata(objectLocation: S3ObjectLocation): Future[UploadedFileMetadataWithDownloadUrl] = {

    def extractDownloadUrl(s3Metadata: S3ObjectMetadata): Future[URL] = {
      Future.fromTry {
        for {
          consumingService <- retrieveConsumingService(s3Metadata)
        } yield downloadUrlGenerator.generate(objectLocation, consumingService)
      }
    }

    def assembler(common: services.UploadedFileMetadata, downloadUrl: URL) = UploadedFileMetadataWithDownloadUrl(downloadUrl, common)

    assembleUploadedFileMetadataAndDetails(objectLocation, extractDownloadUrl, assembler)
  }

  override def retrieveFailedObject(objectLocation: S3ObjectLocation): Future[UploadedFileMetadataWithError] = {

    def extractErrorMessage(s3Metadata: S3ObjectMetadata): Future[String] = {
      implicit val ld = LoggingDetails.fromS3ObjectLocation(objectLocation)

      for {
        s3Object     <- Future(s3Client.getObject(objectLocation.bucket, objectLocation.objectKey))
        errorMessage <- Future.fromTry(Try[String](IOUtils.toString(s3Object.getObjectContent)))
      } yield errorMessage
    }

    def assembler(common: services.UploadedFileMetadata, errorMessage: String) = UploadedFileMetadataWithError(errorMessage, common)

    assembleUploadedFileMetadataAndDetails(objectLocation, extractErrorMessage, assembler)
  }
/*
  override def retrieveReadyMetadata(objectLocation: S3ObjectLocation): Future[UploadedFileMetadataWithDownloadUrl] = {
    implicit val ld = LoggingDetails.fromS3ObjectLocation(objectLocation)

    def extractDownloadUrl(s3Metadata: S3ObjectMetadata): Future[URL] = {
      Future.fromTry {
        for {
          consumingService <- retrieveConsumingService(s3Metadata)
        } yield downloadUrlGenerator.generate(objectLocation, consumingService)
      }
    }

    for {
      metadata          <- Future(s3Client.getObjectMetadata(objectLocation.bucket, objectLocation.objectKey))
      (common,enriched) <- _retrieveMetadata(S3ObjectMetadata(metadata, objectLocation), extractDownloadUrl)
    } yield {
      UploadedFileMetadataWithDownloadUrl(enriched, common)
    }
  }
*/
  /*
  override def retrieveFailedObject(objectLocation: S3ObjectLocation): Future[UploadedFileMetadataWithError] = {
    implicit val ld = LoggingDetails.fromS3ObjectLocation(objectLocation)

    def extractErrorMessage(s3Metadata: S3ObjectMetadata): Future[String] = {
      for {
        s3Object     <- Future(s3Client.getObject(objectLocation.bucket, objectLocation.objectKey))
        errorMessage <- Future.fromTry(Try[String](IOUtils.toString(s3Object.getObjectContent)))
      } yield errorMessage
    }

    for {
      metadata          <- Future(s3Client.getObjectMetadata(objectLocation.bucket, objectLocation.objectKey))
      (common,enriched) <- _retrieveMetadata(S3ObjectMetadata(metadata, objectLocation), extractErrorMessage)
    } yield {
      UploadedFileMetadataWithError(enriched, common)
    }
  }
  */
/*
  override def retrieveReadyMetadata(objectLocation: S3ObjectLocation): Future[UploadedFileMetadataWithDownloadUrl] = {
    implicit val ld = LoggingDetails.fromS3ObjectLocation(objectLocation)

    for {
      metadata          <- Future(s3Client.getObjectMetadata(objectLocation.bucket, objectLocation.objectKey))
      s3Metadata        = S3ObjectMetadata(metadata, objectLocation)
      parsedMetadata    <- Future.fromTry(retrieveMetadata(s3Metadata))
      consumingService  <- Future.fromTry(retrieveConsumingService(s3Metadata))
      downloadUrl       <- Future.successful(downloadUrlGenerator.generate(objectLocation, consumingService))
    } yield {
      UploadedFileMetadataWithDownloadUrl(downloadUrl, parsedMetadata)
    }
  }

  override def retrieveFailedObject(objectLocation: S3ObjectLocation): Future[UploadedFileMetadataWithError] = {
    implicit val ld = LoggingDetails.fromS3ObjectLocation(objectLocation)

    for {
      s3Object     <- Future(s3Client.getObject(objectLocation.bucket, objectLocation.objectKey))
      content      <- Future.fromTry(Try(IOUtils.toString(s3Object.getObjectContent)))
      fileMetadata <- Future.fromTry(retrieveMetadata(S3ObjectMetadata(s3Object.getObjectMetadata, objectLocation)))
    } yield {
      Logger.debug(s"Fetched object with metadata for objectKey: [${objectLocation.objectKey}].")
      UploadedFileMetadataWithError(content, fileMetadata)
    }
  }
*/
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

  private def retrieveUploadDetails(metadata: S3ObjectMetadata): Try[UploadDetails] =
    retrieveValidUploadDetails(metadata).recoverWith {
      case _ => retrieveInvalidUploadDetails(metadata)
    }

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

  val userMetadata: Map[String, String] = underlying.getUserMetadata.asScala.toMap

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
