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

package services

import java.net.URL
import java.time.Instant

import com.amazonaws.AmazonServiceException
import config.ServiceConfiguration
import connectors.aws.{FailedFileMetadata, SuccessfulFileMetadata}
import model._
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{GivenWhenThen, Matchers}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class S3FileNotificationDetailsRetrieverSpec extends UnitSpec with Matchers with GivenWhenThen with MockitoSugar {

  "S3FileNotificationDetailsRetriever" should {

    val config = mock[ServiceConfiguration]

    val urlGenerator = mock[DownloadUrlGenerator]

    val fileReference = FileReference("my-reference")
    val location      = S3ObjectLocation("my-bucket", "my-key")
    val callbackUrl   = new URL("http://my.callback.url")
    val initiateDate  = Instant.parse("2018-04-24T09:30:00Z")
    val checksum      = "1a2b3c4d5e"
    val fileSize      = 10L

    val requestId        = Some("requestId")
    val sessionId        = Some("sessionId")
    val requestContext   = RequestContext(requestId, sessionId, "127.0.0.1")
    val fileName         = "test.pdf"
    val fileMime         = "application/pdf"
    val downloadUrl      = new URL("http://remotehost/my-bucket/my-key")
    val consumingService = "consuming-service"

    val uploadDetails =
      SuccessfulFileMetadata("test.pdf", "application/pdf", Instant.parse("2018-04-24T09:30:00Z"), "1a2b3c4d5e")

    val invalidUploadDetails =
      FailedFileMetadata("test.pdf", Instant.parse("2018-04-24T09:30:00Z"))

    "return callback URL from S3 metadata for uploaded file with all required metadata" in {
      val objectMetadata =
        SuccessfulFileDetails(
          fileReference,
          callbackUrl,
          uploadDetails.fileName,
          uploadDetails.fileMimeType,
          uploadDetails.uploadTimestamp,
          uploadDetails.checksum,
          fileSize,
          requestContext,
          consumingService,
          Map()
        )

      val fileManager = mock[FileManager]
      Mockito.when(fileManager.receiveSuccessfulFileDetails(any())).thenReturn(Future.successful(objectMetadata))

      Mockito.when(urlGenerator.generate(any(), any())).thenReturn(downloadUrl)

      Given("a S3 file notification retriever and a valid set of retrieval details")
      val retriever = new S3FileNotificationDetailsRetriever(fileManager, config, urlGenerator)

      When("the retrieve method is called")
      val result = Await.result(retriever.retrieveUploadedFileDetails(location), 2.seconds)

      And("the expected callback URL is returned")
      result shouldBe FileProcessingDetails(
        callbackUrl,
        fileReference,
        SucessfulResult(
          downloadUrl,
          fileSize,
          uploadDetails.fileName,
          uploadDetails.fileMimeType,
          uploadDetails.uploadTimestamp,
          uploadDetails.checksum),
        requestContext,
        Map()
      )
    }

    //TODO add tests for including requestId and sessionId

    "return wrapped failure if S3 call errors for uploaded file" in {
      val fileManager = mock[FileManager]
      Mockito
        .when(fileManager.receiveSuccessfulFileDetails(any()))
        .thenReturn(Future.failed(new AmazonServiceException("Expected exception")))

      Given("a S3 file notification retriever and a valid set of retrieval details")
      val retriever = new S3FileNotificationDetailsRetriever(fileManager, config, urlGenerator)

      When("the retrieve method is called")
      val result = retriever.retrieveUploadedFileDetails(location)

      ScalaFutures.whenReady(result.failed) { error =>
        Then("a wrapped error returned")
        error shouldBe a[AmazonServiceException]
      }
    }

    "return callback URL from S3 metadata for quarantined file" in {
      val failedObject = FailedFileDetails(
        fileReference,
        callbackUrl,
        invalidUploadDetails.fileName,
        invalidUploadDetails.uploadTimestamp,
        10L,
        requestContext,
        Map(),
        """{"failureReason": "QUARANTINE", "message": "This file has a virus"}"""
      )

      val fileManager = mock[FileManager]
      Mockito.when(fileManager.receiveFailedFileDetails(any())).thenReturn(Future.successful(failedObject))

      Given("a S3 file notification retriever and a valid set of retrieval details")
      val retriever = new S3FileNotificationDetailsRetriever(fileManager, config, urlGenerator)

      When("the retrieve method is called")
      val result = Await.result(retriever.retrieveQuarantinedFileDetails(location), 2.seconds)

      Then("the expected callback URL is returned")
      result shouldBe FileProcessingDetails(
        callbackUrl,
        fileReference,
        QuarantinedResult(
          ErrorDetails("QUARANTINE", "This file has a virus"),
          invalidUploadDetails.fileName,
          invalidUploadDetails.uploadTimestamp),
        requestContext,
        Map()
      )
    }

    "return callback URL from S3 metadata for rejected file" in {
      val failedObject = FailedFileDetails(
        fileReference,
        callbackUrl,
        invalidUploadDetails.fileName,
        invalidUploadDetails.uploadTimestamp,
        10L,
        requestContext,
        Map(),
        """{"failureReason": "REJECTED", "message": "MIME type not allowed"}"""
      )

      val fileManager = mock[FileManager]
      Mockito.when(fileManager.receiveFailedFileDetails(any())).thenReturn(Future.successful(failedObject))

      Given("a S3 file notification retriever and a valid set of retrieval details")
      val retriever = new S3FileNotificationDetailsRetriever(fileManager, config, urlGenerator)

      When("the retrieve method is called")
      val result = Await.result(retriever.retrieveQuarantinedFileDetails(location), 2.seconds)

      Then("the expected callback URL is returned")
      result shouldBe FileProcessingDetails(
        callbackUrl,
        fileReference,
        QuarantinedResult(
          ErrorDetails("REJECTED", "MIME type not allowed"),
          invalidUploadDetails.fileName,
          invalidUploadDetails.uploadTimestamp),
        requestContext,
        Map()
      )
    }

    "return callback URL from S3 metadata for file with unknown error" in {
      val failedObject = FailedFileDetails(
        fileReference,
        callbackUrl,
        invalidUploadDetails.fileName,
        invalidUploadDetails.uploadTimestamp,
        10L,
        requestContext,
        Map(),
        "Something unexpected happened here"
      )

      val fileManager = mock[FileManager]
      Mockito.when(fileManager.receiveFailedFileDetails(any())).thenReturn(Future.successful(failedObject))

      Given("a S3 file notification retriever and a valid set of retrieval details")
      val retriever = new S3FileNotificationDetailsRetriever(fileManager, config, urlGenerator)

      When("the retrieve method is called")
      val result = Await.result(retriever.retrieveQuarantinedFileDetails(location), 2.seconds)

      Then("the expected callback URL is returned")
      result shouldBe FileProcessingDetails(
        callbackUrl,
        fileReference,
        QuarantinedResult(
          ErrorDetails("UNKNOWN", "Something unexpected happened here"),
          invalidUploadDetails.fileName,
          invalidUploadDetails.uploadTimestamp),
        requestContext,
        Map()
      )
    }

    "return wrapped failure if S3 call errors for quarantined file" in {
      val fileManager = mock[FileManager]
      Mockito
        .when(fileManager.receiveFailedFileDetails(any()))
        .thenReturn(Future.failed(new AmazonServiceException("Expected exception")))

      Given("a S3 file notification retriever and a valid set of retrieval details")
      val retriever = new S3FileNotificationDetailsRetriever(fileManager, config, urlGenerator)

      When("the retrieve method is called")
      val result = retriever.retrieveQuarantinedFileDetails(location)

      ScalaFutures.whenReady(result.failed) { error =>
        Then("a wrapped error returned")
        error shouldBe a[AmazonServiceException]
      }
    }
  }
}
