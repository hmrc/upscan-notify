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

import software.amazon.awssdk.auth.credentials.{AwsCredentials, AwsSessionCredentials}
import software.amazon.awssdk.services.secretsmanager.model.{GetSecretValueRequest, GetSecretValueResponse}
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import uk.gov.hmrc.upscannotify.config.ServiceConfiguration
import uk.gov.hmrc.upscannotify.test.UnitSpec
import play.api.libs.json.Json

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

      val credentials: AwsCredentials = ProviderOfAwsCredentials(configuration, mock[SecretsManagerClient]).get().resolveCredentials()

      credentials.accessKeyId                                         shouldBe "KEY_ID"
      credentials.secretAccessKey                                     shouldBe "ACCESS_KEY"
      credentials                                                     shouldBe a[AwsSessionCredentials]
      credentials.asInstanceOf[AwsSessionCredentials].sessionToken shouldBe "SESSION_TOKEN"

    "create BasicAWSCredentials from Secrets Manager when no session token provided" in:
      val configuration  = mock[ServiceConfiguration]
      val secretsClient  = mock[SecretsManagerClient]
      
      when(configuration.sessionToken)
        .thenReturn(None)
      when(configuration.secretArn)
        .thenReturn("test-secret-arn")

      val secretJson = Json.obj(
        "accessKeyId"     -> "SECRET_KEY_ID", 
        "secretAccessKey" -> "SECRET_ACCESS_KEY"
      ).toString()

      when(secretsClient.getSecretValue(any[GetSecretValueRequest]))
        .thenReturn(GetSecretValueResponse.builder().secretString(secretJson).build())


      val credentials: AwsCredentials = ProviderOfAwsCredentials(configuration, secretsClient).get().resolveCredentials()

      credentials.accessKeyId     shouldBe "SECRET_KEY_ID"
      credentials.secretAccessKey shouldBe "SECRET_ACCESS_KEY"
      credentials                 shouldNot be(a[AwsSessionCredentials])
