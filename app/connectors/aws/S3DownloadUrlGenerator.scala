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

import com.amazonaws.services.s3.AmazonS3
import config.ServiceConfiguration
import model.S3ObjectLocation
import services.{DownloadUrlGenerator, SuccessfulFileDetails}

import java.net.URL
import java.time.Instant
import java.util.Date
import javax.inject.Inject

class S3DownloadUrlGenerator @Inject()(
  s3Client: AmazonS3,
  config: ServiceConfiguration
) extends DownloadUrlGenerator:
  override def generate(objectLocation: S3ObjectLocation, metadata: SuccessfulFileDetails): URL =
    s3Client
      .generatePresignedUrl(objectLocation.bucket, objectLocation.objectKey, expirationDate(metadata.consumingService))

  private def expirationDate(serviceName: String): Date =
    val now         = Instant.now()
    val lifetimeEnd = now.plusSeconds(config.s3UrlExpirationPeriod(serviceName).toSeconds)
    Date.from(lifetimeEnd)
