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
import org.mockito.Mockito.when
import uk.gov.hmrc.upscannotify.config.ServiceConfiguration
import uk.gov.hmrc.upscannotify.test.UnitSpec

class ProviderOfAWSCredentialsSpec extends UnitSpec:

  "ProviderOfAWSCredentials" should:
    "create BasicSessionCredentials if session token provided" in:
      val configuration = mock[ServiceConfiguration]
      when(configuration.accessKeyId)
        .thenReturn("KEY_ID")
      when(configuration.secretAccessKey)
        .thenReturn("ACCESS_KEY")
      when(configuration.sessionToken)
        .thenReturn(Some("SESSION_TOKEN"))

      val credentials: AWSCredentialsProvider = OldProviderOfAWSCredentials(configuration).get()

      credentials.getCredentials.getAWSAccessKeyId                                   shouldBe "KEY_ID"
      credentials.getCredentials.getAWSSecretKey                                     shouldBe "ACCESS_KEY"
      credentials.getCredentials                                                     shouldBe a[AWSSessionCredentials]
      credentials.getCredentials.asInstanceOf[AWSSessionCredentials].getSessionToken shouldBe "SESSION_TOKEN"

    "create BasicAWSCredentials in no session token provided" in:
      val configuration = mock[ServiceConfiguration]
      when(configuration.accessKeyId)
        .thenReturn("KEY_ID")
      when(configuration.secretAccessKey)
        .thenReturn("ACCESS_KEY")
      when(configuration.sessionToken)
        .thenReturn(None)

      val credentials: AWSCredentialsProvider = OldProviderOfAWSCredentials(configuration).get()

      credentials.getCredentials.getAWSAccessKeyId shouldBe "KEY_ID"
      credentials.getCredentials.getAWSSecretKey   shouldBe "ACCESS_KEY"
      credentials.getCredentials shouldNot be(a[AWSSessionCredentials])
