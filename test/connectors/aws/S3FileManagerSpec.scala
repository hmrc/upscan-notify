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

package connectors.aws

import java.net.URL
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.ObjectMetadata
import jakarta.mail.internet.MimeUtility
import model.{FileReference, RequestContext, S3ObjectLocation}
import org.scalatest.concurrent.ScalaFutures
import services.SuccessfulFileDetails
import test.UnitSpec
import scala.concurrent.ExecutionContext

class S3FileManagerSpec extends UnitSpec {

  private val callbackUrl      = new URL("http://my.callback.url")
  private val initiateDate     = Instant.parse("2018-04-24T09:30:00Z")
  private val checksum         = "1a2b3c4d5e"
  private val consumingService = "consumingService"
  private val contentLength    = 42
  private implicit val ec      = ExecutionContext.Implicits.global

  "FileManager" should {
    "allow to fetch objects metadata" in {
      val fileLocation = S3ObjectLocation("bucket", "objectKey")

      val s3client    = mock[AmazonS3]
      val fileManager = new S3FileManager(s3client)

      val userMetadata = new util.TreeMap[String, String]()
      userMetadata.put("callback-url", callbackUrl.toString)
      userMetadata.put("initiate-date", DateTimeFormatter.ISO_INSTANT.format(initiateDate))
      userMetadata.put("checksum", checksum)
      userMetadata.put("request-id", "REQUEST_ID")
      userMetadata.put("session-id", "SESSION_ID")
      userMetadata.put("original-filename", "test.pdf")
      userMetadata.put("mime-type", "application/pdf")
      userMetadata.put("client-ip", "127.0.0.1")
      userMetadata.put("file-reference", "ref1")
      userMetadata.put("consuming-service", consumingService)

      val objectMetadata = mock[ObjectMetadata]
      when(objectMetadata.getUserMetadata).thenReturn(userMetadata)
      when(objectMetadata.getContentLength).thenReturn(contentLength)

      when(s3client.getObjectMetadata(fileLocation.bucket, fileLocation.objectKey)).thenReturn(objectMetadata)

      val result = fileManager.receiveSuccessfulFileDetails(fileLocation)

      ScalaFutures.whenReady(result) { result =>
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
          userMetadata = Map(
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
      }
    }

    "return wrapped failure if the metadata doesn't contain callback URL for uploaded file" in {
      val fileLocation = S3ObjectLocation("bucket", "objectKey")

      val s3client    = mock[AmazonS3]
      val fileManager = new S3FileManager(s3client)

      val userMetadata = new util.TreeMap[String, String]()
      userMetadata.put("initiate-date", DateTimeFormatter.ISO_INSTANT.format(initiateDate))
      userMetadata.put("checksum", checksum)
      userMetadata.put("original-filename", "test.pdf")
      userMetadata.put("mime-type", "application/xml")
      userMetadata.put("client-ip", "127.0.0.1")
      userMetadata.put("file-reference", "ref1")

      val objectMetadata = mock[ObjectMetadata]
      when(objectMetadata.getUserMetadata).thenReturn(userMetadata)

      when(s3client.getObjectMetadata(fileLocation.bucket, fileLocation.objectKey)).thenReturn(objectMetadata)

      val result = fileManager.receiveSuccessfulFileDetails(fileLocation)

      ScalaFutures.whenReady(result.failed) { error =>
        error            shouldBe a[NoSuchElementException]
        error.getMessage shouldBe s"Metadata not found: [callback-url] for object=[${fileLocation.objectKey}]."
      }
    }

    "return wrapped failure if the metadata doesn't contain a name for uploaded file" in {
      val fileLocation = S3ObjectLocation("bucket", "objectKey")

      val s3client    = mock[AmazonS3]
      val fileManager = new S3FileManager(s3client)

      val userMetadata = new util.TreeMap[String, String]()
      userMetadata.put("callback-url", callbackUrl.toString)
      userMetadata.put("initiate-date", DateTimeFormatter.ISO_INSTANT.format(initiateDate))
      userMetadata.put("checksum", checksum)
      userMetadata.put("mime-type", "application/xml")
      userMetadata.put("client-ip", "127.0.0.1")
      userMetadata.put("file-reference", "ref1")

      val objectMetadata = mock[ObjectMetadata]
      when(objectMetadata.getUserMetadata).thenReturn(userMetadata)

      when(s3client.getObjectMetadata(fileLocation.bucket, fileLocation.objectKey)).thenReturn(objectMetadata)

      val result = fileManager.receiveSuccessfulFileDetails(fileLocation)

      ScalaFutures.whenReady(result.failed) { error =>
        error            shouldBe a[NoSuchElementException]
        error.getMessage shouldBe s"Metadata not found: [original-filename] for object=[${fileLocation.objectKey}]."
      }
    }

    "return wrapped failure if the metadata doesn't contain a mime-type for uploaded file" in {
      val fileLocation = S3ObjectLocation("bucket", "objectKey")

      val s3client    = mock[AmazonS3]
      val fileManager = new S3FileManager(s3client)

      val userMetadata = new util.TreeMap[String, String]()
      userMetadata.put("callback-url", callbackUrl.toString)
      userMetadata.put("initiate-date", DateTimeFormatter.ISO_INSTANT.format(initiateDate))
      userMetadata.put("checksum", checksum)
      userMetadata.put("original-filename", "test.pdf")
      userMetadata.put("client-ip", "127.0.0.1")
      userMetadata.put("file-reference", "ref1")

      val objectMetadata = mock[ObjectMetadata]
      when(objectMetadata.getUserMetadata).thenReturn(userMetadata)

      when(s3client.getObjectMetadata(fileLocation.bucket, fileLocation.objectKey)).thenReturn(objectMetadata)

      val result = fileManager.receiveSuccessfulFileDetails(fileLocation)

      ScalaFutures.whenReady(result.failed) { error =>
        error            shouldBe a[NoSuchElementException]
        error.getMessage shouldBe s"Metadata not found: [mime-type] for object=[${fileLocation.objectKey}]."
      }
    }

    "return wrapped failure if the metadata doesn't contain a timestamp for uploaded file" in {
      val fileLocation = S3ObjectLocation("bucket", "objectKey")

      val s3client    = mock[AmazonS3]
      val fileManager = new S3FileManager(s3client)

      val userMetadata = new util.TreeMap[String, String]()
      userMetadata.put("callback-url", callbackUrl.toString)
      userMetadata.put("checksum", checksum)
      userMetadata.put("client-ip", "127.0.0.1")
      userMetadata.put("file-reference", "ref1")

      val objectMetadata = mock[ObjectMetadata]
      when(objectMetadata.getUserMetadata).thenReturn(userMetadata)

      when(s3client.getObjectMetadata(fileLocation.bucket, fileLocation.objectKey)).thenReturn(objectMetadata)

      val result = fileManager.receiveSuccessfulFileDetails(fileLocation)

      ScalaFutures.whenReady(result.failed) { error =>
        error            shouldBe a[NoSuchElementException]
        error.getMessage shouldBe s"Metadata not found: [initiate-date] for object=[${fileLocation.objectKey}]."
      }
    }

    "return wrapped failure if the metadata doesn't contain a checksum for uploaded file" in {
      val fileLocation = S3ObjectLocation("bucket", "objectKey")

      val s3client    = mock[AmazonS3]
      val fileManager = new S3FileManager(s3client)

      val userMetadata = new util.TreeMap[String, String]()
      userMetadata.put("callback-url", callbackUrl.toString)
      userMetadata.put("initiate-date", DateTimeFormatter.ISO_INSTANT.format(initiateDate))
      userMetadata.put("original-filename", "test.pdf")
      userMetadata.put("mime-type", "application/xml")
      userMetadata.put("client-ip", "127.0.0.1")
      userMetadata.put("file-reference", "ref1")

      val objectMetadata = mock[ObjectMetadata]
      when(objectMetadata.getUserMetadata).thenReturn(userMetadata)

      when(s3client.getObjectMetadata(fileLocation.bucket, fileLocation.objectKey)).thenReturn(objectMetadata)

      val result = fileManager.receiveSuccessfulFileDetails(fileLocation)

      ScalaFutures.whenReady(result.failed) { error =>
        error            shouldBe a[NoSuchElementException]
        error.getMessage shouldBe s"Metadata not found: [checksum] for object=[${fileLocation.objectKey}]."
      }
    }

    "return wrapped failure if the metadata doesn't contain a client-ip for uploaded file" in {
      val fileLocation = S3ObjectLocation("bucket", "objectKey")

      val s3client    = mock[AmazonS3]
      val fileManager = new S3FileManager(s3client)

      val userMetadata = new util.TreeMap[String, String]()
      userMetadata.put("callback-url", callbackUrl.toString)
      userMetadata.put("initiate-date", DateTimeFormatter.ISO_INSTANT.format(initiateDate))
      userMetadata.put("original-filename", "test.pdf")
      userMetadata.put("mime-type", "application/xml")
      userMetadata.put("checksum", "123456")
      userMetadata.put("file-reference", "ref1")

      val objectMetadata = mock[ObjectMetadata]
      when(objectMetadata.getUserMetadata).thenReturn(userMetadata)

      when(s3client.getObjectMetadata(fileLocation.bucket, fileLocation.objectKey)).thenReturn(objectMetadata)

      val result = fileManager.receiveSuccessfulFileDetails(fileLocation)

      ScalaFutures.whenReady(result.failed) { error =>
        error            shouldBe a[NoSuchElementException]
        error.getMessage shouldBe s"Metadata not found: [client-ip] for object=[${fileLocation.objectKey}]."
      }
    }

    "return wrapped failure if the callback metadata is not a valid URL for uploaded file" in {
      val fileLocation = S3ObjectLocation("bucket", "objectKey")

      val s3client    = mock[AmazonS3]
      val fileManager = new S3FileManager(s3client)

      val userMetadata = new util.TreeMap[String, String]()
      userMetadata.put("callback-url", "not-a-valid-url")
      userMetadata.put("initiate-date", DateTimeFormatter.ISO_INSTANT.format(initiateDate))
      userMetadata.put("checksum", checksum)
      userMetadata.put("client-ip", "127.0.0.1")
      userMetadata.put("file-reference", "ref1")

      val objectMetadata = mock[ObjectMetadata]
      when(objectMetadata.getUserMetadata).thenReturn(userMetadata)

      when(s3client.getObjectMetadata(fileLocation.bucket, fileLocation.objectKey)).thenReturn(objectMetadata)

      val result = fileManager.receiveSuccessfulFileDetails(fileLocation)

      ScalaFutures.whenReady(result.failed) { error =>
        error shouldBe a[Exception]
        error.getMessage shouldBe s"Invalid metadata: [callback-url: not-a-valid-url] for object=[${fileLocation.objectKey}]. " +
          s"Error: java.net.MalformedURLException: no protocol: not-a-valid-url"
      }
    }

    "return wrapped failure if the initiate date is not a valid date for uploaded file" in {
      val fileLocation = S3ObjectLocation("bucket", "objectKey")

      val s3client    = mock[AmazonS3]
      val fileManager = new S3FileManager(s3client)

      val userMetadata = new util.TreeMap[String, String]()
      userMetadata.put("callback-url", callbackUrl.toString)
      userMetadata.put("initiate-date", "not-a-valid-date")
      userMetadata.put("checksum", checksum)
      userMetadata.put("client-ip", "127.0.0.1")
      userMetadata.put("file-reference", "ref1")

      val objectMetadata = mock[ObjectMetadata]
      when(objectMetadata.getUserMetadata).thenReturn(userMetadata)

      when(s3client.getObjectMetadata(fileLocation.bucket, fileLocation.objectKey)).thenReturn(objectMetadata)

      val result = fileManager.receiveSuccessfulFileDetails(fileLocation)

      ScalaFutures.whenReady(result.failed) { error =>
        error shouldBe a[Exception]
        error.getMessage shouldBe s"Invalid metadata: [initiate-date: not-a-valid-date] for object=[${fileLocation.objectKey}]. " +
          s"Error: java.time.format.DateTimeParseException: Text 'not-a-valid-date' could not be parsed at index 0"
      }
    }

    "properly handle exceptions when fetching metadata" in {
      val fileLocation = S3ObjectLocation("bucket", "objectKey")

      val s3client    = mock[AmazonS3]
      val fileManager = new S3FileManager(s3client)

      when(s3client.getObjectMetadata(fileLocation.bucket, fileLocation.objectKey)).thenThrow(
        new RuntimeException("Exception"))

      val result = fileManager.receiveSuccessfulFileDetails(fileLocation)

      ScalaFutures.whenReady(result.failed) { result =>
        result.getMessage shouldBe "Exception"
      }
    }

    "decode MIME-encoded original filenames from metadata" in {

      val originalFileName = "赴任・着任証明書-tteesstt.pdf"

      val fileLocation = S3ObjectLocation("bucket", "objectKey")

      val s3client    = mock[AmazonS3]
      val fileManager = new S3FileManager(s3client)

      val userMetadata = new util.TreeMap[String, String]()
      userMetadata.put("callback-url", callbackUrl.toString)
      userMetadata.put("initiate-date", DateTimeFormatter.ISO_INSTANT.format(initiateDate))
      userMetadata.put("checksum", checksum)
      userMetadata.put("request-id", "REQUEST_ID")
      userMetadata.put("session-id", "SESSION_ID")
      userMetadata.put("original-filename", MimeUtility.encodeText(originalFileName))
      userMetadata.put("mime-type", "application/pdf")
      userMetadata.put("client-ip", "127.0.0.1")
      userMetadata.put("file-reference", "ref1")
      userMetadata.put("consuming-service", consumingService)

      val objectMetadata = mock[ObjectMetadata]
      when(objectMetadata.getUserMetadata).thenReturn(userMetadata)
      when(objectMetadata.getContentLength).thenReturn(contentLength)

      when(s3client.getObjectMetadata(fileLocation.bucket, fileLocation.objectKey)).thenReturn(objectMetadata)

      val result = fileManager.receiveSuccessfulFileDetails(fileLocation)

      ScalaFutures.whenReady(result) { result =>
        result.fileName shouldBe originalFileName
      }
    }
  }
}
