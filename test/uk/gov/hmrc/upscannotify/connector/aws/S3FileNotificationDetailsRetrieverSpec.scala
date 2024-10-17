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
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.upscannotify.model.{FileReference, RequestContext, S3ObjectLocation}
import uk.gov.hmrc.upscannotify.service.SuccessfulFileDetails
import uk.gov.hmrc.upscannotify.test.UnitSpec

import java.net.URL
import java.time.Instant
import java.time.format.DateTimeFormatter
import scala.concurrent.{ExecutionContext, Future}

class S3FileNotificationDetailsRetrieverSpec
  extends UnitSpec
     with ScalaFutures:

  private val callbackUrl      = URL("http://my.callback.url")
  private val initiateDate     = Instant.parse("2018-04-24T09:30:00Z")
  private val checksum         = "1a2b3c4d5e"
  private val consumingService = "consumingService"
  private val contentLength    = 42L

  import ExecutionContext.Implicits.global

  "S3FileNotificationDetailsRetriever" should:
    "allow to fetch objects metadata" in:
      val objectLocation = S3ObjectLocation("bucket", "objectKey")

      val s3FileManager = mock[S3FileManager]
      val s3FileNotificationDetailsRetriever = S3FileNotificationDetailsRetriever(s3FileManager)

      val userMetadata = Map(
        "callback-url"      -> callbackUrl.toString,
        "initiate-date"     -> DateTimeFormatter.ISO_INSTANT.format(initiateDate),
        "checksum"          -> checksum,
        "request-id"        -> "REQUEST_ID",
        "session-id"        -> "SESSION_ID",
        "original-filename" -> "test.pdf",
        "mime-type"         -> "application/pdf",
        "client-ip"         -> "127.0.0.1",
        "file-reference"    -> "ref1",
        "consuming-service" -> consumingService
      )

      when(s3FileManager.getObjectMetadata(objectLocation))
        .thenReturn:
          Future.successful:
            S3ObjectMetadata(
              objectLocation,
              userMetadata,
              initiateDate,
              contentLength
            )

      val result = s3FileNotificationDetailsRetriever.receiveSuccessfulFileDetails(objectLocation).futureValue

      result shouldBe SuccessfulFileDetails(
        fileReference    = FileReference("ref1"),
        callbackUrl      = callbackUrl,
        fileName         = "test.pdf",
        fileMimeType     = "application/pdf",
        uploadTimestamp  = initiateDate,
        checksum         = checksum,
        size             = contentLength,
        requestContext   = RequestContext(Some("REQUEST_ID"), Some("SESSION_ID"), "127.0.0.1"),
        consumingService = consumingService,
        userMetadata    = Map(
                            "mime-type"         -> "application/pdf",
                            "callback-url"      -> "http://my.callback.url",
                            "file-reference"    -> "ref1",
                            "consuming-service" -> "consumingService",
                            "request-id"        -> "REQUEST_ID",
                            "session-id"        -> "SESSION_ID",
                            "checksum"          -> "1a2b3c4d5e",
                            "client-ip"         -> "127.0.0.1",
                            "original-filename" -> "test.pdf",
                            "initiate-date"     -> "2018-04-24T09:30:00Z"
                          )
      )

    "return wrapped failure if the metadata doesn't contain callback URL for uploaded file" in:
      val objectLocation = S3ObjectLocation("bucket", "objectKey")

      val s3FileManager = mock[S3FileManager]
      val s3FileNotificationDetailsRetriever = S3FileNotificationDetailsRetriever(s3FileManager)

      val userMetadata = Map(
        "initiate-date"     -> DateTimeFormatter.ISO_INSTANT.format(initiateDate),
        "checksum"          -> checksum,
        "original-filename" -> "test.pdf",
        "mime-type"         -> "application/xml",
        "client-ip"         -> "127.0.0.1",
        "file-reference"    -> "ref1"
      )

      when(s3FileManager.getObjectMetadata(objectLocation))
        .thenReturn:
          Future.successful:
            S3ObjectMetadata(
              objectLocation,
              userMetadata,
              initiateDate,
              contentLength
            )

      val error = s3FileNotificationDetailsRetriever.receiveSuccessfulFileDetails(objectLocation).failed.futureValue
      error            shouldBe a[NoSuchElementException]
      error.getMessage shouldBe s"Metadata not found: [callback-url] for object=[${objectLocation.objectKey}]."

    "return wrapped failure if the metadata doesn't contain a name for uploaded file" in:
      val objectLocation = S3ObjectLocation("bucket", "objectKey")

      val s3FileManager = mock[S3FileManager]
      val s3FileNotificationDetailsRetriever = S3FileNotificationDetailsRetriever(s3FileManager)

      val userMetadata = Map(
        "callback-url"   -> callbackUrl.toString,
        "initiate-date"  -> DateTimeFormatter.ISO_INSTANT.format(initiateDate),
        "checksum"       -> checksum,
        "mime-type"      -> "application/xml",
        "client-ip"      -> "127.0.0.1",
        "file-reference" -> "ref1"
      )

      when(s3FileManager.getObjectMetadata(objectLocation))
        .thenReturn:
          Future.successful:
            S3ObjectMetadata(
              objectLocation,
              userMetadata,
              initiateDate,
              contentLength
            )

      val error = s3FileNotificationDetailsRetriever.receiveSuccessfulFileDetails(objectLocation).failed.futureValue
      error            shouldBe a[NoSuchElementException]
      error.getMessage shouldBe s"Metadata not found: [original-filename] for object=[${objectLocation.objectKey}]."

    "return wrapped failure if the metadata doesn't contain a mime-type for uploaded file" in:
      val objectLocation = S3ObjectLocation("bucket", "objectKey")

      val s3FileManager = mock[S3FileManager]
      val s3FileNotificationDetailsRetriever = S3FileNotificationDetailsRetriever(s3FileManager)

      val userMetadata = Map(
        "callback-url"      -> callbackUrl.toString,
        "initiate-date"     -> DateTimeFormatter.ISO_INSTANT.format(initiateDate),
        "checksum"          -> checksum,
        "original-filename" -> "test.pdf",
        "client-ip"         -> "127.0.0.1",
        "file-reference"    -> "ref1"
      )

      when(s3FileManager.getObjectMetadata(objectLocation))
        .thenReturn:
          Future.successful:
            S3ObjectMetadata(
              objectLocation,
              userMetadata,
              initiateDate,
              contentLength
            )

      val error = s3FileNotificationDetailsRetriever.receiveSuccessfulFileDetails(objectLocation).failed.futureValue
      error            shouldBe a[NoSuchElementException]
      error.getMessage shouldBe s"Metadata not found: [mime-type] for object=[${objectLocation.objectKey}]."

    "return wrapped failure if the metadata doesn't contain a timestamp for uploaded file" in:
      val objectLocation = S3ObjectLocation("bucket", "objectKey")

      val s3FileManager = mock[S3FileManager]
      val s3FileNotificationDetailsRetriever = S3FileNotificationDetailsRetriever(s3FileManager)

      val userMetadata = Map(
        "callback-url"   -> callbackUrl.toString,
        "checksum"       -> checksum,
        "client-ip"      -> "127.0.0.1",
        "file-reference" -> "ref1"
      )

      when(s3FileManager.getObjectMetadata(objectLocation))
        .thenReturn:
          Future.successful:
            S3ObjectMetadata(
              objectLocation,
              userMetadata,
              initiateDate,
              contentLength
            )

      val error = s3FileNotificationDetailsRetriever.receiveSuccessfulFileDetails(objectLocation).failed.futureValue
      error            shouldBe a[NoSuchElementException]
      error.getMessage shouldBe s"Metadata not found: [initiate-date] for object=[${objectLocation.objectKey}]."

    "return wrapped failure if the metadata doesn't contain a checksum for uploaded file" in:
      val objectLocation = S3ObjectLocation("bucket", "objectKey")

      val s3FileManager = mock[S3FileManager]
      val s3FileNotificationDetailsRetriever = S3FileNotificationDetailsRetriever(s3FileManager)

      val userMetadata = Map(
        "callback-url"      -> callbackUrl.toString,
        "initiate-date"     -> DateTimeFormatter.ISO_INSTANT.format(initiateDate),
        "original-filename" -> "test.pdf",
        "mime-type"         -> "application/xml",
        "client-ip"         -> "127.0.0.1",
        "file-reference"    -> "ref1"
      )

      when(s3FileManager.getObjectMetadata(objectLocation))
        .thenReturn:
          Future.successful:
            S3ObjectMetadata(
              objectLocation,
              userMetadata,
              initiateDate,
              contentLength
            )

      val error = s3FileNotificationDetailsRetriever.receiveSuccessfulFileDetails(objectLocation).failed.futureValue
      error            shouldBe a[NoSuchElementException]
      error.getMessage shouldBe s"Metadata not found: [checksum] for object=[${objectLocation.objectKey}]."

    "return wrapped failure if the metadata doesn't contain a client-ip for uploaded file" in:
      val objectLocation = S3ObjectLocation("bucket", "objectKey")

      val s3FileManager = mock[S3FileManager]
      val s3FileNotificationDetailsRetriever = S3FileNotificationDetailsRetriever(s3FileManager)

      val userMetadata = Map(
        "callback-url"      -> callbackUrl.toString,
        "initiate-date"     -> DateTimeFormatter.ISO_INSTANT.format(initiateDate),
        "original-filename" -> "test.pdf",
        "mime-type"         -> "application/xml",
        "checksum"          -> "123456",
        "file-reference"    -> "ref1"
      )

      when(s3FileManager.getObjectMetadata(objectLocation))
        .thenReturn:
          Future.successful:
            S3ObjectMetadata(
              objectLocation,
              userMetadata,
              initiateDate,
              contentLength
            )

      val error = s3FileNotificationDetailsRetriever.receiveSuccessfulFileDetails(objectLocation).failed.futureValue
      error            shouldBe a[NoSuchElementException]
      error.getMessage shouldBe s"Metadata not found: [client-ip] for object=[${objectLocation.objectKey}]."

    "return wrapped failure if the callback metadata is not a valid URL for uploaded file" in:
      val objectLocation = S3ObjectLocation("bucket", "objectKey")

      val s3FileManager = mock[S3FileManager]
      val s3FileNotificationDetailsRetriever = S3FileNotificationDetailsRetriever(s3FileManager)

      val userMetadata = Map(
        "callback-url"   -> "not-a-valid-url",
        "initiate-date"  -> DateTimeFormatter.ISO_INSTANT.format(initiateDate),
        "checksum"       -> checksum,
        "client-ip"      -> "127.0.0.1",
        "file-reference" -> "ref1"
      )

      when(s3FileManager.getObjectMetadata(objectLocation))
        .thenReturn:
          Future.successful:
            S3ObjectMetadata(
              objectLocation,
              userMetadata,
              initiateDate,
              contentLength
            )

      val error = s3FileNotificationDetailsRetriever.receiveSuccessfulFileDetails(objectLocation).failed.futureValue
      error shouldBe a[Exception]
      error.getMessage shouldBe s"Invalid metadata: [callback-url: not-a-valid-url] for object=[${objectLocation.objectKey}]. " +
        s"Error: java.net.MalformedURLException: no protocol: not-a-valid-url"

    "return wrapped failure if the initiate date is not a valid date for uploaded file" in:
      val objectLocation = S3ObjectLocation("bucket", "objectKey")

      val s3FileManager = mock[S3FileManager]
      val s3FileNotificationDetailsRetriever = S3FileNotificationDetailsRetriever(s3FileManager)

      val userMetadata = Map(
        "callback-url"   -> callbackUrl.toString,
        "initiate-date"  -> "not-a-valid-date",
        "checksum"       -> checksum,
        "client-ip"      -> "127.0.0.1",
        "file-reference" -> "ref1"
      )

      when(s3FileManager.getObjectMetadata(objectLocation))
        .thenReturn:
          Future.successful:
            S3ObjectMetadata(
              objectLocation,
              userMetadata,
              initiateDate,
              contentLength
            )

      val error = s3FileNotificationDetailsRetriever.receiveSuccessfulFileDetails(objectLocation).failed.futureValue
      error shouldBe a[Exception]
      error.getMessage shouldBe s"Invalid metadata: [initiate-date: not-a-valid-date] for object=[${objectLocation.objectKey}]. " +
        s"Error: java.time.format.DateTimeParseException: Text 'not-a-valid-date' could not be parsed at index 0"

    "properly handle exceptions when fetching metadata" in:
      val objectLocation = S3ObjectLocation("bucket", "objectKey")

      val s3FileManager    = mock[S3FileManager]
      val s3FileNotificationDetailsRetriever = S3FileNotificationDetailsRetriever(s3FileManager)

      when(s3FileManager.getObjectMetadata(objectLocation))
        .thenReturn:
          Future.failed(RuntimeException("Exception"))

      val error = s3FileNotificationDetailsRetriever.receiveSuccessfulFileDetails(objectLocation).failed.futureValue
      error.getMessage shouldBe "Exception"

    "decode MIME-encoded original filenames from metadata" in:

      val originalFileName = "赴任・着任証明書-tteesstt.pdf"

      val objectLocation = S3ObjectLocation("bucket", "objectKey")

      val s3FileManager = mock[S3FileManager]
      val s3FileNotificationDetailsRetriever = S3FileNotificationDetailsRetriever(s3FileManager)

      val userMetadata = Map(
        "callback-url"      -> callbackUrl.toString,
        "initiate-date"     -> DateTimeFormatter.ISO_INSTANT.format(initiateDate),
        "checksum"          -> checksum,
        "request-id"        -> "REQUEST_ID",
        "session-id"        -> "SESSION_ID",
        "original-filename" -> MimeUtility.encodeText(originalFileName),
        "mime-type"         -> "application/pdf",
        "client-ip"         -> "127.0.0.1",
        "file-reference"    -> "ref1",
        "consuming-service" -> consumingService
      )

      when(s3FileManager.getObjectMetadata(objectLocation))
        .thenReturn:
          Future.successful:
            S3ObjectMetadata(
              objectLocation,
              userMetadata,
              initiateDate,
              contentLength
            )

      val result = s3FileNotificationDetailsRetriever.receiveSuccessfulFileDetails(objectLocation).futureValue
      result.fileName shouldBe originalFileName
