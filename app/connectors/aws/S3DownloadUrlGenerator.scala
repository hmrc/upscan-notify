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

import java.net.URL
import java.util.{Calendar, Date}
import javax.inject.Inject

import com.amazonaws.services.s3.AmazonS3
import config.ServiceConfiguration
import model.S3ObjectLocation
import services.DownloadUrlGenerator

class S3DownloadUrlGenerator @Inject()(s3Client: AmazonS3, config: ServiceConfiguration) extends DownloadUrlGenerator {
  override def generate(objectLocation: S3ObjectLocation): URL =
    s3Client.generatePresignedUrl(objectLocation.bucket, objectLocation.objectKey, expirationDate())

  private def expirationDate(): Date = {
    val c = Calendar.getInstance
    c.setTime(new Date())
    c.add(Calendar.DATE, config.daysToExpiration)
    c.getTime
  }
}
