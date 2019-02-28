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
import java.util.Date

import com.amazonaws.services.s3.AmazonS3

import scala.concurrent.duration._
import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar.mock
import config.ServiceConfiguration
import model.{FileReference, RequestContext, S3ObjectLocation}
import services.SuccessfulFileDetails
import uk.gov.hmrc.play.test.UnitSpec

class S3DownloadUrlGeneratorSpec extends UnitSpec {

  "S3DownloadUrlGenerator.generate" should {

    "return a pre-signed URL for a known serviceName" in {

      val consumingService = "consuming-service"
      val expectedUrl      = new URL("http://www.presignedurl.com")

      val mockAmazonS3: AmazonS3 = mock[AmazonS3]
      val s3objectLocation       = S3ObjectLocation("bucket", "objectKey")
      when(
        mockAmazonS3
          .generatePresignedUrl(meq(s3objectLocation.bucket), meq(s3objectLocation.objectKey), any(classOf[Date])))
        .thenReturn(expectedUrl)

      val mockServiceConfiguration: ServiceConfiguration = mock[ServiceConfiguration]
      when(mockServiceConfiguration.s3UrlExpirationPeriod(consumingService))
        .thenReturn(1000.milliseconds)

      val readyObjectMetadata: SuccessfulFileDetails = SuccessfulFileDetails(
        fileReference    = FileReference("file-reference"),
        callbackUrl      = new URL("http://www.fakeurl.com"),
        fileName         = "fileName",
        fileMimeType     = "fileMimeType",
        uploadTimestamp  = Instant.now,
        checksum         = "checkSum",
        size             = 1L,
        requestContext   = RequestContext(Some("requestId"), Some("sessionId"), "clientIP"),
        consumingService = consumingService,
        userMetadata     = Map()
      )

      val s3DownloadUrlGenerator = new S3DownloadUrlGenerator(mockAmazonS3, mockServiceConfiguration)
      s3DownloadUrlGenerator.generate(S3ObjectLocation("bucket", "objectKey"), readyObjectMetadata) shouldBe expectedUrl
    }

  }

}
