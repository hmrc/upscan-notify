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
import java.util

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.ObjectMetadata
import config.ServiceConfiguration
import connectors.aws.S3FileNotificationDetailsRetriever
import model.{S3ObjectLocation, UploadedFile}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{GivenWhenThen, Matchers}
import uk.gov.hmrc.play.test.UnitSpec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration._

class S3FileNotificationDetailsRetrieverSpec extends UnitSpec with Matchers with GivenWhenThen with MockitoSugar {

  "S3FileNotificationDetailsRetriever" should {

    val awsMetadataKey = "aws-metadata-key"
    val location       = S3ObjectLocation("my-bucket", "my-key")

    val config = mock[ServiceConfiguration]
    Mockito.when(config.callbackUrlMetadataKey).thenReturn(awsMetadataKey)

    "return callback URL from S3 metadata" in {
      val callbackUrl  = new URL("http://my.callback.url")
      val userMetadata = new util.TreeMap[String, String]()
      userMetadata.put(awsMetadataKey, callbackUrl.toString)

      val objectMetadata = mock[ObjectMetadata]
      Mockito.when(objectMetadata.getUserMetadata).thenReturn(userMetadata)

      val s3Client = mock[AmazonS3]
      Mockito.when(s3Client.getObjectMetadata(any(): String, any(): String)).thenReturn(objectMetadata)

      Given("a S3 file notification retriever and a valid set of retrieval details")
      val retriever = new S3FileNotificationDetailsRetriever(s3Client, config)

      When("the retrieve method is called")
      val result = Await.result(retriever.retrieveUploadedFileDetails(location), 2.seconds)

      Then("the S3 client should be called")
      Mockito.verify(s3Client).getObjectMetadata(any(): String, any(): String)

      And("the expected callback URL is returned")
      result shouldBe UploadedFile(callbackUrl, "my-key")
    }

    "return wrapped failure if S3 call errors" in {
      val s3Client = mock[AmazonS3]
      Mockito
        .when(s3Client.getObjectMetadata(any(): String, any(): String))
        .thenThrow(new AmazonServiceException("Expected exception"))

      Given("a S3 file notification retriever and a valid set of retrieval details")
      val retriever = new S3FileNotificationDetailsRetriever(s3Client, config)

      When("the retrieve method is called")
      val result = retriever.retrieveUploadedFileDetails(location)

      ScalaFutures.whenReady(result.failed) { error =>
        Then("the S3 client should be called")
        Mockito.verify(s3Client).getObjectMetadata(any(): String, any(): String)

        And("a wrapped error returned")
        error shouldBe a[AmazonServiceException]
      }
    }

    "return wrapped failure if the metadata doesn't contain callback URL" in {
      val userMetadata = new util.TreeMap[String, String]()

      val objectMetadata = mock[ObjectMetadata]
      Mockito.when(objectMetadata.getUserMetadata).thenReturn(userMetadata)

      val s3Client = mock[AmazonS3]
      Mockito.when(s3Client.getObjectMetadata(any(): String, any(): String)).thenReturn(objectMetadata)

      Given("a S3 file notification retriever and a valid set of retrieval details")
      val retriever = new S3FileNotificationDetailsRetriever(s3Client, config)

      When("the retrieve method is called")
      val result = retriever.retrieveUploadedFileDetails(location)

      ScalaFutures.whenReady(result.failed) { error =>
        Then("the S3 client should be called")
        Mockito.verify(s3Client).getObjectMetadata(any(): String, any(): String)

        And("a wrapped error returned")
        error            shouldBe a[NoSuchElementException]
        error.getMessage shouldBe s"Metadata not found: $awsMetadataKey for file: ${location.objectKey}"
      }
    }

    "return wrapped failure if the callback metadata is not a valid URL" in {
      val userMetadata = new util.TreeMap[String, String]()
      userMetadata.put(awsMetadataKey, "this-is-not-a-url")

      val objectMetadata = mock[ObjectMetadata]
      Mockito.when(objectMetadata.getUserMetadata).thenReturn(userMetadata)

      val s3Client = mock[AmazonS3]
      Mockito.when(s3Client.getObjectMetadata(any(): String, any(): String)).thenReturn(objectMetadata)

      Given("a S3 file notification retriever and a valid set of retrieval details")
      val retriever = new S3FileNotificationDetailsRetriever(s3Client, config)

      When("the retrieve method is called")
      val result = retriever.retrieveUploadedFileDetails(location)

      ScalaFutures.whenReady(result.failed) { error =>
        Then("the S3 client should be called")
        Mockito.verify(s3Client).getObjectMetadata(any(): String, any(): String)

        And("a wrapped error returned")
        error shouldBe a[IllegalArgumentException]
        error.getMessage
          .contains(s"Invalid metadata: aws-metadata-key: this-is-not-a-url for file: ${location.objectKey}") shouldBe true
      }
    }
  }
}
