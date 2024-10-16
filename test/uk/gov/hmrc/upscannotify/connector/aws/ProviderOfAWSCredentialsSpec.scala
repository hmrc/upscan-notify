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

import com.amazonaws.auth.{AWSCredentialsProvider, AWSSessionCredentials}
import uk.gov.hmrc.upscannotify.config.ServiceConfiguration
import uk.gov.hmrc.upscannotify.test.UnitSpec

import scala.concurrent.duration.{Duration, FiniteDuration}

class ProviderOfAWSCredentialsSpec extends UnitSpec:

  "ProviderOfAWSCredentials" should:
    "create BasicSessionCredentials if session token provided" in:
      val configuration =
        new ServiceConfiguration:
          override def successfulProcessingBatchSize: Int = ???

          override def quarantineProcessingBatchSize: Int = ???


          override def accessKeyId: String = "KEY_ID"

          override def awsRegion: String = ???

          override def secretAccessKey: String = "ACCESS_KEY"

          override def sessionToken: Option[String] = Some("SESSION_TOKEN")

          override def outboundSuccessfulQueueUrl: String = ???

          override def retryInterval = ???

          override def s3UrlExpirationPeriod(serviceName: String): FiniteDuration = ???

          override def outboundQuarantineQueueUrl: String = ???

          override def endToEndProcessingThreshold(): Duration = ???

      val credentials: AWSCredentialsProvider = ProviderOfAWSCredentials(configuration).get()

      credentials.getCredentials.getAWSAccessKeyId                                   shouldBe "KEY_ID"
      credentials.getCredentials.getAWSSecretKey                                     shouldBe "ACCESS_KEY"
      credentials.getCredentials                                                     shouldBe a[AWSSessionCredentials]
      credentials.getCredentials.asInstanceOf[AWSSessionCredentials].getSessionToken shouldBe "SESSION_TOKEN"

    "create BasicAWSCredentials in no session token provided" in:
      val configuration =
        new ServiceConfiguration:
          override def successfulProcessingBatchSize: Int = ???

          override def quarantineProcessingBatchSize: Int = ???


          override def accessKeyId: String = "KEY_ID"

          override def awsRegion: String = ???

          override def secretAccessKey: String = "ACCESS_KEY"

          override def sessionToken: Option[String] = None

          override def outboundSuccessfulQueueUrl: String = ???

          override def retryInterval = ???

          override def s3UrlExpirationPeriod(serviceName: String): FiniteDuration = ???

          override def outboundQuarantineQueueUrl: String = ???

          override def endToEndProcessingThreshold(): Duration = ???

      val credentials: AWSCredentialsProvider = ProviderOfAWSCredentials(configuration).get()

      credentials.getCredentials.getAWSAccessKeyId shouldBe "KEY_ID"
      credentials.getCredentials.getAWSSecretKey   shouldBe "ACCESS_KEY"
      credentials.getCredentials shouldNot be(a[AWSSessionCredentials])
