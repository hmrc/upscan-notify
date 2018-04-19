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

import com.amazonaws.AmazonServiceException
import config.ServiceConfiguration
import model.{QuarantinedFile, S3ObjectLocation, UploadedFile}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{GivenWhenThen, Matchers}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class S3FileNotificationDetailsRetrieverSpec extends UnitSpec with Matchers with GivenWhenThen with MockitoSugar {

  "S3FileNotificationDetailsRetriever" should {

    val awsMetadataKey = "aws-metadata-key"
    val location       = S3ObjectLocation("my-bucket", "my-key")

    val config = mock[ServiceConfiguration]
    Mockito.when(config.callbackUrlMetadataKey).thenReturn(awsMetadataKey)

    val urlGenerator = mock[DownloadUrlGenerator]

    "return callback URL from S3 metadata for uploaded file" in {
      val callbackUrl = new URL("http://my.callback.url")

      val objectMetadata = ObjectMetadata(Map(awsMetadataKey -> callbackUrl.toString), 10L)

      val fileManager = mock[FileManager]
      Mockito.when(fileManager.retrieveMetadata(any())).thenReturn(Future.successful(objectMetadata))

      val downloadUrl = new URL("http://remotehost/my-bucket/my-key")
      Mockito.when(urlGenerator.generate(any())).thenReturn(downloadUrl)

      Given("a S3 file notification retriever and a valid set of retrieval details")
      val retriever = new S3FileNotificationDetailsRetriever(fileManager, config, urlGenerator)

      When("the retrieve method is called")
      val result = Await.result(retriever.retrieveUploadedFileDetails(location), 2.seconds)

      And("the expected callback URL is returned")
      result shouldBe UploadedFile(callbackUrl, "my-key", downloadUrl, 10L)
    }

    "return wrapped failure if S3 call errors for uploaded file" in {
      val fileManager = mock[FileManager]
      Mockito
        .when(fileManager.retrieveMetadata(any()))
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

    "return wrapped failure if the metadata doesn't contain callback URL for uploaded file" in {
      val objectMetadata = ObjectMetadata(Map(), 10L)

      val fileManager = mock[FileManager]
      Mockito.when(fileManager.retrieveMetadata(any())).thenReturn(Future.successful(objectMetadata))

      Given("a S3 file notification retriever and a valid set of retrieval details")
      val retriever = new S3FileNotificationDetailsRetriever(fileManager, config, urlGenerator)

      When("the retrieve method is called")
      val result = retriever.retrieveUploadedFileDetails(location)

      ScalaFutures.whenReady(result.failed) { error =>
        And("a wrapped error returned")
        error            shouldBe a[NoSuchElementException]
        error.getMessage shouldBe s"Metadata not found: $awsMetadataKey for file: ${location.objectKey}"
      }
    }

    "return wrapped failure if the callback metadata is not a valid URL for uploaded file" in {

      val objectMetadata = ObjectMetadata(Map(awsMetadataKey -> "this-is-not-a-url"), 10L)

      val fileManager = mock[FileManager]
      Mockito.when(fileManager.retrieveMetadata(any())).thenReturn(Future.successful(objectMetadata))

      Given("a S3 file notification retriever and a valid set of retrieval details")
      val retriever = new S3FileNotificationDetailsRetriever(fileManager, config, urlGenerator)

      When("the retrieve method is called")
      val result = retriever.retrieveUploadedFileDetails(location)

      ScalaFutures.whenReady(result.failed) { error =>
        Then("a wrapped error returned")
        error shouldBe an[Exception]
        error.getMessage
          .contains(s"Invalid metadata: aws-metadata-key: this-is-not-a-url for file: ${location.objectKey}") shouldBe true
      }
    }

    "return callback URL from S3 metadata for quarantined file" in {

      val callbackUrl    = "http://my.callback.url"
      val objectMetadata = ObjectMetadata(Map(awsMetadataKey -> callbackUrl), 10L)
      val s3Object       = ObjectWithMetadata("This file has a virus", objectMetadata)

      val fileManager = mock[FileManager]
      Mockito.when(fileManager.retrieveObject(any())).thenReturn(Future.successful(s3Object))

      Given("a S3 file notification retriever and a valid set of retrieval details")
      val retriever = new S3FileNotificationDetailsRetriever(fileManager, config, urlGenerator)

      When("the retrieve method is called")
      val result = Await.result(retriever.retrieveQuarantinedFileDetails(location), 2.seconds)

      Then("the expected callback URL is returned")
      result shouldBe QuarantinedFile(new URL(callbackUrl), "my-key", "This file has a virus")
    }

    "return wrapped failure if S3 call errors for quarantined file" in {
      val fileManager = mock[FileManager]
      Mockito
        .when(fileManager.retrieveObject(any()))
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

    "return wrapped failure if the metadata doesn't contain callback URL for quarantined file" in {
      val objectMetadata = ObjectMetadata(Map(), 10L)
      val s3Object       = ObjectWithMetadata("This file has a virus", objectMetadata)

      val fileManager = mock[FileManager]
      Mockito.when(fileManager.retrieveObject(any())).thenReturn(Future.successful(s3Object))

      Given("a S3 file notification retriever and a valid set of retrieval details")
      val retriever = new S3FileNotificationDetailsRetriever(fileManager, config, urlGenerator)

      When("the retrieve method is called")
      val result = retriever.retrieveQuarantinedFileDetails(location)

      ScalaFutures.whenReady(result.failed) { error =>
        Then("a wrapped error returned")
        error            shouldBe a[NoSuchElementException]
        error.getMessage shouldBe s"Metadata not found: $awsMetadataKey for file: ${location.objectKey}"
      }
    }

    "return wrapped failure if the callback metadata is not a valid URL for quarantined file" in {

      val callbackUrl    = "this-is-not-a-url"
      val objectMetadata = ObjectMetadata(Map(awsMetadataKey -> callbackUrl), 10L)
      val s3Object       = ObjectWithMetadata("This file has a virus", objectMetadata)

      val fileManager = mock[FileManager]
      Mockito.when(fileManager.retrieveObject(any())).thenReturn(s3Object)

      Given("a S3 file notification retriever and a valid set of retrieval details")
      val retriever = new S3FileNotificationDetailsRetriever(fileManager, config, urlGenerator)

      When("the retrieve method is called")
      val result = retriever.retrieveQuarantinedFileDetails(location)

      ScalaFutures.whenReady(result.failed) { error =>
        Then("a wrapped error returned")
        error shouldBe an[Exception]
        error.getMessage
          .contains(s"Invalid metadata: aws-metadata-key: this-is-not-a-url for file: ${location.objectKey}") shouldBe true
      }
    }
  }
}
