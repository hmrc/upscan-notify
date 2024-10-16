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

import com.amazonaws.services.s3.AmazonS3
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.when
import uk.gov.hmrc.upscannotify.config.ServiceConfiguration
import uk.gov.hmrc.upscannotify.model.{FileReference, RequestContext, S3ObjectLocation}
import uk.gov.hmrc.upscannotify.service.SuccessfulFileDetails
import uk.gov.hmrc.upscannotify.test.UnitSpec

import java.net.URL
import java.time.Instant
import java.util.Date
import scala.concurrent.duration._

class S3DownloadUrlGeneratorSpec extends UnitSpec:
  "S3DownloadUrlGenerator.generate" should:
    "return a pre-signed URL for a known serviceName" in:
      val consumingService = "consuming-service"
      val expectedUrl      = URL("http://www.presignedurl.com")

      val mockAmazonS3     = mock[AmazonS3]
      val s3objectLocation = S3ObjectLocation("bucket", "objectKey")
      when(
        mockAmazonS3
          .generatePresignedUrl(eqTo(s3objectLocation.bucket), eqTo(s3objectLocation.objectKey), any[Date])
      )
        .thenReturn(expectedUrl)

      val mockServiceConfiguration: ServiceConfiguration = mock[ServiceConfiguration]
      when(mockServiceConfiguration.s3UrlExpirationPeriod(consumingService))
        .thenReturn(1000.milliseconds)

      val readyObjectMetadata: SuccessfulFileDetails = SuccessfulFileDetails(
        fileReference    = FileReference("file-reference"),
        callbackUrl      = URL("http://www.fakeurl.com"),
        fileName         = "fileName",
        fileMimeType     = "fileMimeType",
        uploadTimestamp  = Instant.now,
        checksum         = "checkSum",
        size             = 1L,
        requestContext   = RequestContext(Some("requestId"), Some("sessionId"), "clientIP"),
        consumingService = consumingService,
        userMetadata     = Map()
      )

      val s3DownloadUrlGenerator = S3DownloadUrlGenerator(mockAmazonS3, mockServiceConfiguration)
      s3DownloadUrlGenerator.generate(S3ObjectLocation("bucket", "objectKey"), readyObjectMetadata) shouldBe expectedUrl
