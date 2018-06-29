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

package services

import java.net.URL
import java.time.Instant

import com.amazonaws.AmazonServiceException
import config.ServiceConfiguration
import model._
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{GivenWhenThen, Matchers}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

class S3FileNotificationDetailsRetrieverSpec extends UnitSpec with Matchers with GivenWhenThen with MockitoSugar {

  "S3FileNotificationDetailsRetriever" should {

    val location = S3ObjectLocation("my-bucket", "my-key")

    val config = mock[ServiceConfiguration]

    val urlGenerator = mock[DownloadUrlGenerator]

    val callbackUrl  = new URL("http://my.callback.url")
    val initiateDate = Instant.parse("2018-04-24T09:30:00Z")
    val checksum     = "1a2b3c4d5e"

    "return callback URL from S3 metadata for uploaded file with all required metadata" in {
      val objectMetadata =
        ReadyObjectMetadata(
          callbackUrl,
          UploadDetails("test.pdf", "application/pdf", initiateDate, checksum),
          10L,
          Some("requestId"),
          Some("sessionId"))

      val fileManager = mock[FileManager]
      Mockito.when(fileManager.retrieveReadyMetadata(any())).thenReturn(Future.successful(objectMetadata))

      val downloadUrl = new URL("http://remotehost/my-bucket/my-key")
      Mockito.when(urlGenerator.generate(any())).thenReturn(downloadUrl)

      Given("a S3 file notification retriever and a valid set of retrieval details")
      val retriever = new S3FileNotificationDetailsRetriever(fileManager, config, urlGenerator)

      When("the retrieve method is called")
      val result = Await.result(retriever.retrieveUploadedFileDetails(location), 2.seconds)

      And("the expected callback URL is returned")
      result shouldBe UploadedFile(
        callbackUrl,
        FileReference("my-key"),
        downloadUrl,
        10L,
        UploadDetails("test.pdf", "application/pdf", initiateDate, checksum),
        RequestContext(Some("requestId"), Some("sessionId"))
      )
    }

    //TODO add tests for including requestId and sessionId

    "return wrapped failure if S3 call errors for uploaded file" in {
      val fileManager = mock[FileManager]
      Mockito
        .when(fileManager.retrieveReadyMetadata(any()))
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
      val objectMetadata = FailedObjectMetadata(callbackUrl, 10L, Some("requestId"), Some("sessionId"))
      val s3Object =
        FailedObjectWithMetadata(
          """{"failureReason": "QUARANTINE", "message": "This file has a virus"}""",
          objectMetadata)

      val fileManager = mock[FileManager]
      Mockito.when(fileManager.retrieveFailedObject(any())).thenReturn(Future.successful(s3Object))

      Given("a S3 file notification retriever and a valid set of retrieval details")
      val retriever = new S3FileNotificationDetailsRetriever(fileManager, config, urlGenerator)

      When("the retrieve method is called")
      val result = Await.result(retriever.retrieveQuarantinedFileDetails(location), 2.seconds)

      Then("the expected callback URL is returned")
      result shouldBe QuarantinedFile(
        callbackUrl,
        FileReference("my-key"),
        ErrorDetails("QUARANTINE", "This file has a virus"),
        RequestContext(Some("requestId"), Some("sessionId")))
    }

    "return callback URL from S3 metadata for rejected file" in {
      val objectMetadata = FailedObjectMetadata(callbackUrl, 10L, Some("requestId"), Some("sessionId"))
      val s3Object =
        FailedObjectWithMetadata(
          """{"failureReason": "REJECTED", "message": "MIME type not allowed"}""",
          objectMetadata)

      val fileManager = mock[FileManager]
      Mockito.when(fileManager.retrieveFailedObject(any())).thenReturn(Future.successful(s3Object))

      Given("a S3 file notification retriever and a valid set of retrieval details")
      val retriever = new S3FileNotificationDetailsRetriever(fileManager, config, urlGenerator)

      When("the retrieve method is called")
      val result = Await.result(retriever.retrieveQuarantinedFileDetails(location), 2.seconds)

      Then("the expected callback URL is returned")
      result shouldBe QuarantinedFile(
        callbackUrl,
        FileReference("my-key"),
        ErrorDetails("REJECTED", "MIME type not allowed"),
        RequestContext(Some("requestId"), Some("sessionId")))
    }

    "return callback URL from S3 metadata for file with unknown error" in {
      val objectMetadata = FailedObjectMetadata(callbackUrl, 10L, Some("requestId"), Some("sessionId"))
      val s3Object       = FailedObjectWithMetadata("Something unexpected happened here", objectMetadata)

      val fileManager = mock[FileManager]
      Mockito.when(fileManager.retrieveFailedObject(any())).thenReturn(Future.successful(s3Object))

      Given("a S3 file notification retriever and a valid set of retrieval details")
      val retriever = new S3FileNotificationDetailsRetriever(fileManager, config, urlGenerator)

      When("the retrieve method is called")
      val result = Await.result(retriever.retrieveQuarantinedFileDetails(location), 2.seconds)

      Then("the expected callback URL is returned")
      result shouldBe QuarantinedFile(
        callbackUrl,
        FileReference("my-key"),
        ErrorDetails("UNKNOWN", "Something unexpected happened here"),
        RequestContext(Some("requestId"), Some("sessionId")))
    }

    "return wrapped failure if S3 call errors for quarantined file" in {
      val fileManager = mock[FileManager]
      Mockito
        .when(fileManager.retrieveFailedObject(any()))
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
