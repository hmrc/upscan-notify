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

import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import uk.gov.hmrc.upscannotify.config.ServiceConfiguration
import uk.gov.hmrc.upscannotify.model.S3ObjectLocation
import uk.gov.hmrc.upscannotify.service.{DownloadUrlGenerator, SuccessfulFileDetails}

import java.net.URL
import javax.inject.Inject

class S3DownloadUrlGenerator @Inject()(
  config: ServiceConfiguration,
  awsCredentialsProvider: AwsCredentialsProvider
) extends DownloadUrlGenerator:

  private val presigner = S3Presigner
    .builder()
    .region(Region.of(config.awsRegion))
    .credentialsProvider(awsCredentialsProvider)
    .build()

  override def generate(objectLocation: S3ObjectLocation, metadata: SuccessfulFileDetails): URL =
    val expirationPeriod = config.s3UrlExpirationPeriod(metadata.consumingService)

    val objectRequest =
      GetObjectRequest.builder()
        .bucket(objectLocation.bucket)
        .key(objectLocation.objectKey)
        .build()

    play.api.Logger(getClass).info(s"Setting signature duration for ${metadata.consumingService} to ${expirationPeriod.toMinutes} seconds")

    val presignRequest =
      GetObjectPresignRequest.builder()
        .signatureDuration(java.time.Duration.ofSeconds(expirationPeriod.toSeconds))
        .getObjectRequest(objectRequest)
        .build()

    presigner
      .presignGetObject(presignRequest)
      .url()
