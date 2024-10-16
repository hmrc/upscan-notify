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

package uk.gov.hmrc.upscannotify.service

import com.amazonaws.AmazonServiceException
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.GivenWhenThen
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.upscannotify.connector.aws.{FailedFileMetadata, SuccessfulFileMetadata}
import uk.gov.hmrc.upscannotify.model._
import uk.gov.hmrc.upscannotify.test.UnitSpec

import java.net.URL
import java.time.Instant
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class S3FileNotificationDetailsRetrieverSpec extends UnitSpec with GivenWhenThen:

  "S3FileNotificationDetailsRetriever" should:
    val urlGenerator = mock[DownloadUrlGenerator]

    val fileReference = FileReference("my-reference")
    val location      = S3ObjectLocation("my-bucket", "my-key")
    val callbackUrl   = URL("http://my.callback.url")
    val fileSize      = 10L

    val requestId        = Some("requestId")
    val sessionId        = Some("sessionId")
    val requestContext   = RequestContext(requestId, sessionId, "127.0.0.1")
    val downloadUrl      = URL("http://remotehost/my-bucket/my-key")
    val consumingService = "consuming-service"

    val uploadDetails =
      SuccessfulFileMetadata("test.pdf", "application/pdf", Instant.parse("2018-04-24T09:30:00Z"), "1a2b3c4d5e")

    val invalidUploadDetails =
      FailedFileMetadata("test.pdf", Instant.parse("2018-04-24T09:30:00Z"))

    import ExecutionContext.Implicits.global

    "return callback URL from S3 metadata for uploaded file with all required metadata" in:
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
          Map(
            "unused-metadata"                  -> "xxx",
            "x-amz-meta-upscan-started-upload" -> "2017-04-24T09:30:00Z",
            "x-amz-meta-upscan-not-checkpoint" -> "xxx"
          )
        )

      val fileManager = mock[FileManager]
      when(fileManager.receiveSuccessfulFileDetails(any[S3ObjectLocation]))
        .thenReturn(Future.successful(objectMetadata))

      when(urlGenerator.generate(any[S3ObjectLocation], any[SuccessfulFileDetails]))
        .thenReturn(downloadUrl)

      Given("a S3 file notification retriever and a valid set of retrieval details")
      val retriever = S3FileNotificationDetailsRetriever(fileManager, urlGenerator)

      When("the retrieve method is called")
      val result = Await.result(retriever.retrieveUploadedFileDetails(location), 2.seconds)

      And("the expected callback URL is returned")
      result shouldBe WithCheckpoints(
        SuccessfulProcessingDetails(
          callbackUrl     = callbackUrl,
          reference       = fileReference,
          downloadUrl     = downloadUrl,
          size            = fileSize,
          fileName        = uploadDetails.fileName,
          fileMimeType    = uploadDetails.fileMimeType,
          uploadTimestamp = uploadDetails.uploadTimestamp,
          checksum        = uploadDetails.checksum,
          requestContext  = requestContext
        ),
        Checkpoints(Seq(Checkpoint("x-amz-meta-upscan-started-upload", Instant.parse("2017-04-24T09:30:00Z"))))
      )

    //TODO add tests for including requestId and sessionId

    "return wrapped failure if S3 call errors for uploaded file" in:
      val fileManager = mock[FileManager]
      when(fileManager.receiveSuccessfulFileDetails(any[S3ObjectLocation])).thenReturn(
        Future.failed(AmazonServiceException("Expected exception")))

      Given("a S3 file notification retriever and a valid set of retrieval details")
      val retriever = S3FileNotificationDetailsRetriever(fileManager, urlGenerator)

      When("the retrieve method is called")
      val result = retriever.retrieveUploadedFileDetails(location)

      ScalaFutures.whenReady(result.failed): error =>
        Then("a wrapped error returned")
        error shouldBe a[AmazonServiceException]

    "return callback URL from S3 metadata for quarantined file" in:
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
      when(fileManager.receiveFailedFileDetails(any[S3ObjectLocation]))
        .thenReturn(Future.successful(failedObject))

      Given("a S3 file notification retriever and a valid set of retrieval details")
      val retriever = S3FileNotificationDetailsRetriever(fileManager, urlGenerator)

      When("the retrieve method is called")
      val result = Await.result(retriever.retrieveQuarantinedFileDetails(location), 2.seconds)

      Then("the expected callback URL is returned")
      result shouldBe WithCheckpoints(
        FailedProcessingDetails(
          callbackUrl     = callbackUrl,
          reference       = fileReference,
          fileName        = invalidUploadDetails.fileName,
          uploadTimestamp = invalidUploadDetails.uploadTimestamp,
          error           = ErrorDetails("QUARANTINE", "This file has a virus"),
          requestContext  = requestContext
        ),
        Checkpoints(Nil)
      )

    "return callback URL from S3 metadata for rejected file" in:
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
      when(fileManager.receiveFailedFileDetails(any[S3ObjectLocation]))
        .thenReturn(Future.successful(failedObject))

      Given("a S3 file notification retriever and a valid set of retrieval details")
      val retriever = S3FileNotificationDetailsRetriever(fileManager, urlGenerator)

      When("the retrieve method is called")
      val result = Await.result(retriever.retrieveQuarantinedFileDetails(location), 2.seconds)

      Then("the expected callback URL is returned")
      result shouldBe WithCheckpoints(
        FailedProcessingDetails(
          callbackUrl     = callbackUrl,
          reference       = fileReference,
          fileName        = invalidUploadDetails.fileName,
          uploadTimestamp = invalidUploadDetails.uploadTimestamp,
          error           = ErrorDetails("REJECTED", message = "MIME type not allowed"),
          requestContext  = requestContext
        ),
        Checkpoints(Nil)
      )

    "return callback URL from S3 metadata for file with unknown error" in:
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
      when(fileManager.receiveFailedFileDetails(any[S3ObjectLocation]))
        .thenReturn(Future.successful(failedObject))

      Given("a S3 file notification retriever and a valid set of retrieval details")
      val retriever = S3FileNotificationDetailsRetriever(fileManager, urlGenerator)

      When("the retrieve method is called")
      val result = Await.result(retriever.retrieveQuarantinedFileDetails(location), 2.seconds)

      Then("the expected callback URL is returned")
      result shouldBe WithCheckpoints(
        FailedProcessingDetails(
          callbackUrl     = callbackUrl,
          reference       = fileReference,
          fileName        = invalidUploadDetails.fileName,
          uploadTimestamp = invalidUploadDetails.uploadTimestamp,
          error           = ErrorDetails("UNKNOWN", "Something unexpected happened here"),
          requestContext  = requestContext
        ),
        Checkpoints(Nil)
      )

    "return wrapped failure if S3 call errors for quarantined file" in:
      val fileManager = mock[FileManager]
      when(fileManager.receiveFailedFileDetails(any[S3ObjectLocation]))
        .thenReturn(Future.failed(AmazonServiceException("Expected exception")))

      Given("a S3 file notification retriever and a valid set of retrieval details")
      val retriever = S3FileNotificationDetailsRetriever(fileManager, urlGenerator)

      When("the retrieve method is called")
      val result = retriever.retrieveQuarantinedFileDetails(location)

      ScalaFutures.whenReady(result.failed): error =>
        Then("a wrapped error returned")
        error shouldBe a[AmazonServiceException]
