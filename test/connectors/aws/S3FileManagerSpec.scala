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

import java.io.ByteArrayInputStream
import java.net.URL
import java.nio.charset.Charset
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.S3ObjectInputStream
import model.S3ObjectLocation
import org.mockito.Mockito
import org.scalatest.Matchers
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import services.ReadyObjectMetadata
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class S3FileManagerSpec extends UnitSpec with Matchers with MockitoSugar {

  private val callbackUrl  = new URL("http://my.callback.url")
  private val initiateDate = Instant.parse("2018-04-24T09:30:00Z")
  private val checksum     = "1a2b3c4d5e"

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

      val objectMetadata = mock[com.amazonaws.services.s3.model.ObjectMetadata]
      Mockito.when(objectMetadata.getUserMetadata).thenReturn(userMetadata)

      Mockito
        .when(s3client.getObjectMetadata(fileLocation.bucket, fileLocation.objectKey))
        .thenReturn(objectMetadata)

      val result = fileManager.retrieveReadyMetadata(fileLocation)

      ScalaFutures.whenReady(result) { result =>
        result shouldBe ReadyObjectMetadata(
          callbackUrl,
          initiateDate,
          checksum,
          0L,
          Some("REQUEST_ID"),
          Some("SESSION_ID"))
      }
    }

    "return wrapped failure if the metadata doesn't contain callback URL for uploaded file" in {
      val fileLocation = S3ObjectLocation("bucket", "objectKey")

      val s3client    = mock[AmazonS3]
      val fileManager = new S3FileManager(s3client)

      val userMetadata = new util.TreeMap[String, String]()
      userMetadata.put("initiate-date", DateTimeFormatter.ISO_INSTANT.format(initiateDate))
      userMetadata.put("checksum", checksum)

      val objectMetadata = mock[com.amazonaws.services.s3.model.ObjectMetadata]
      Mockito.when(objectMetadata.getUserMetadata).thenReturn(userMetadata)

      Mockito
        .when(s3client.getObjectMetadata(fileLocation.bucket, fileLocation.objectKey))
        .thenReturn(objectMetadata)

      val result = fileManager.retrieveReadyMetadata(fileLocation)

      ScalaFutures.whenReady(result.failed) { error =>
        error            shouldBe a[NoSuchElementException]
        error.getMessage shouldBe s"Metadata not found: [callback-url], for objectKey: [${fileLocation.objectKey}]."
      }
    }

    "return wrapped failure if the metadata doesn't contain a timestamp for uploaded file" in {
      val fileLocation = S3ObjectLocation("bucket", "objectKey")

      val s3client    = mock[AmazonS3]
      val fileManager = new S3FileManager(s3client)

      val userMetadata = new util.TreeMap[String, String]()
      userMetadata.put("callback-url", callbackUrl.toString)
      userMetadata.put("checksum", checksum)

      val objectMetadata = mock[com.amazonaws.services.s3.model.ObjectMetadata]
      Mockito.when(objectMetadata.getUserMetadata).thenReturn(userMetadata)

      Mockito
        .when(s3client.getObjectMetadata(fileLocation.bucket, fileLocation.objectKey))
        .thenReturn(objectMetadata)

      val result = fileManager.retrieveReadyMetadata(fileLocation)

      ScalaFutures.whenReady(result.failed) { error =>
        error            shouldBe a[NoSuchElementException]
        error.getMessage shouldBe s"Metadata not found: [initiate-date], for objectKey: [${fileLocation.objectKey}]."
      }
    }

    "return wrapped failure if the metadata doesn't contain a checksum for uploaded file" in {
      val fileLocation = S3ObjectLocation("bucket", "objectKey")

      val s3client    = mock[AmazonS3]
      val fileManager = new S3FileManager(s3client)

      val userMetadata = new util.TreeMap[String, String]()
      userMetadata.put("callback-url", callbackUrl.toString)
      userMetadata.put("initiate-date", DateTimeFormatter.ISO_INSTANT.format(initiateDate))

      val objectMetadata = mock[com.amazonaws.services.s3.model.ObjectMetadata]
      Mockito.when(objectMetadata.getUserMetadata).thenReturn(userMetadata)

      Mockito
        .when(s3client.getObjectMetadata(fileLocation.bucket, fileLocation.objectKey))
        .thenReturn(objectMetadata)

      val result = fileManager.retrieveReadyMetadata(fileLocation)

      ScalaFutures.whenReady(result.failed) { error =>
        error            shouldBe a[NoSuchElementException]
        error.getMessage shouldBe s"Metadata not found: [checksum], for objectKey: [${fileLocation.objectKey}]."
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

      val objectMetadata = mock[com.amazonaws.services.s3.model.ObjectMetadata]
      Mockito.when(objectMetadata.getUserMetadata).thenReturn(userMetadata)

      Mockito
        .when(s3client.getObjectMetadata(fileLocation.bucket, fileLocation.objectKey))
        .thenReturn(objectMetadata)

      val result = fileManager.retrieveReadyMetadata(fileLocation)

      ScalaFutures.whenReady(result.failed) { error =>
        error shouldBe a[Exception]
        error.getMessage shouldBe s"Invalid metadata: [callback-url: not-a-valid-url], for objectKey: [${fileLocation.objectKey}]. " +
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

      val objectMetadata = mock[com.amazonaws.services.s3.model.ObjectMetadata]
      Mockito.when(objectMetadata.getUserMetadata).thenReturn(userMetadata)

      Mockito
        .when(s3client.getObjectMetadata(fileLocation.bucket, fileLocation.objectKey))
        .thenReturn(objectMetadata)

      val result = fileManager.retrieveReadyMetadata(fileLocation)

      ScalaFutures.whenReady(result.failed) { error =>
        error shouldBe a[Exception]
        error.getMessage shouldBe s"Invalid metadata: [initiate-date: not-a-valid-date], for objectKey: [${fileLocation.objectKey}]. " +
          s"Error: java.time.format.DateTimeParseException: Text 'not-a-valid-date' could not be parsed at index 0"
      }
    }

    "properly handle exceptions when fetching metadata" in {
      val fileLocation = S3ObjectLocation("bucket", "objectKey")

      val s3client    = mock[AmazonS3]
      val fileManager = new S3FileManager(s3client)

      val userMetadata = new util.TreeMap[String, String]()
      userMetadata.put("key1", "value1")
      userMetadata.put("key2", "value2")

      val objectMetadata = mock[com.amazonaws.services.s3.model.ObjectMetadata]
      Mockito.when(objectMetadata.getUserMetadata).thenReturn(userMetadata)

      Mockito
        .when(s3client.getObjectMetadata(fileLocation.bucket, fileLocation.objectKey))
        .thenThrow(new RuntimeException("Exception"))

      val result = fileManager.retrieveReadyMetadata(fileLocation)

      ScalaFutures.whenReady(result.failed) { result =>
        result.getMessage shouldBe "Exception"
      }
    }

    "allow to fetch objects metadata and content" in {
      val fileLocation = S3ObjectLocation("bucket", "objectKey")

      val s3client    = mock[AmazonS3]
      val fileManager = new S3FileManager(s3client)

      val userMetadata = new util.TreeMap[String, String]()
      userMetadata.put("callback-url", callbackUrl.toString)
      userMetadata.put("initiate-date", DateTimeFormatter.ISO_INSTANT.format(initiateDate))
      userMetadata.put("checksum", checksum)
      userMetadata.put("request-id", "REQUEST_ID")
      userMetadata.put("session-id", "SESSION_ID")

      val objectMetadata = mock[com.amazonaws.services.s3.model.ObjectMetadata]
      Mockito.when(objectMetadata.getUserMetadata).thenReturn(userMetadata)

      val s3object = mock[com.amazonaws.services.s3.model.S3Object]
      Mockito.when(s3object.getObjectMetadata).thenReturn(objectMetadata)
      Mockito
        .when(s3object.getObjectContent)
        .thenReturn(new S3ObjectInputStream(new ByteArrayInputStream("TEST".getBytes(Charset.defaultCharset())), null))

      Mockito
        .when(s3client.getObject(fileLocation.bucket, fileLocation.objectKey))
        .thenReturn(s3object)

      val result = fileManager.retrieveReadyObject(fileLocation)

      ScalaFutures.whenReady(result) { result =>
        result.metadata shouldBe ReadyObjectMetadata(
          callbackUrl,
          initiateDate,
          checksum,
          0L,
          Some("REQUEST_ID"),
          Some("SESSION_ID"))
        result.content shouldBe "TEST"
      }

    }

    "properly handle exceptions when fetching metadata and content" in {
      val fileLocation = S3ObjectLocation("bucket", "objectKey")

      val s3client    = mock[AmazonS3]
      val fileManager = new S3FileManager(s3client)

      Mockito
        .when(s3client.getObject(fileLocation.bucket, fileLocation.objectKey))
        .thenThrow(new RuntimeException("Exception"))

      val result = fileManager.retrieveReadyObject(fileLocation)

      ScalaFutures.whenReady(result.failed) { result =>
        result.getMessage shouldBe "Exception"
      }
    }
  }
}
