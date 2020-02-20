/*
 * Copyright 2020 HM Revenue & Customs
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

package config

import config.PlayBasedServiceConfiguration.S3UrlExpirationPeriod
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar.mock
import play.api.{Configuration, Environment, Mode}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.duration._

class PlayBasedServiceConfigurationSpec extends UnitSpec {

  import PlayBasedServiceConfigurationSpec._

  private val mockConfiguration = mock[Configuration]
  private val mockEnvironment = mock[Environment]
  when(mockEnvironment.mode).thenReturn(Mode.Test)
  private val playBasedServiceConfiguration = new PlayBasedServiceConfiguration(mockConfiguration, mockEnvironment)

  "s3UrlExpirationPeriod" should {

    "return relevant config for a translated serviceName with invalid chars" in {
      when(mockConfiguration.getMilliseconds(s3UrlExpirationPeriodKeyFor("Mozilla-4-0"))).thenReturn(Some(1.days.toMillis))

      playBasedServiceConfiguration.s3UrlExpirationPeriod("Mozilla/4.0") shouldBe 1.days
    }

    "return bespoke service configuration when defined and valid (at most 7 days)" in {
      when(mockConfiguration.getMilliseconds(s3UrlExpirationPeriodKeyFor(SomeServiceName))).thenReturn(Some(2.days.toMillis))

      playBasedServiceConfiguration.s3UrlExpirationPeriod(SomeServiceName) shouldBe 2.days
    }

    "return default configuration when valid (at most 7 days) and bespoke service configuration is not defined" in {
      when(mockConfiguration.getMilliseconds(s3UrlExpirationPeriodKeyFor(SomeServiceName))).thenReturn(None)
      when(mockConfiguration.getMilliseconds(DefaultS3UrlExpirationKey)).thenReturn(Some(5.days.toMillis))

      playBasedServiceConfiguration.s3UrlExpirationPeriod(SomeServiceName) shouldBe 5.days
    }

    "return default configuration when valid (at most 7 days) and bespoke service configuration is invalid (greater than 7 days)" in {
      when(mockConfiguration.getMilliseconds(s3UrlExpirationPeriodKeyFor(SomeServiceName))).thenReturn(Some(8.days.toMillis))
      when(mockConfiguration.getMilliseconds(DefaultS3UrlExpirationKey)).thenReturn(Some(5.days.toMillis))

      playBasedServiceConfiguration.s3UrlExpirationPeriod(SomeServiceName) shouldBe 5.days
    }

    "return fallback configuration when default configuration is invalid (greater than 7 days) and bespoke service configuration is not defined" in {
      when(mockConfiguration.getMilliseconds(s3UrlExpirationPeriodKeyFor(SomeServiceName))).thenReturn(None)
      when(mockConfiguration.getMilliseconds(DefaultS3UrlExpirationKey)).thenReturn(Some(8.days.toMillis))

      playBasedServiceConfiguration.s3UrlExpirationPeriod(SomeServiceName) shouldBe S3UrlExpirationPeriod.FallbackValue
    }

    "return fallback configuration when both bespoke service configuration and default configuration are invalid (greater than 7 days)" in {
      when(mockConfiguration.getMilliseconds(s3UrlExpirationPeriodKeyFor(SomeServiceName))).thenReturn(Some(8.days.toMillis))
      when(mockConfiguration.getMilliseconds(DefaultS3UrlExpirationKey)).thenReturn(Some(9.days.toMillis))

      playBasedServiceConfiguration.s3UrlExpirationPeriod(SomeServiceName) shouldBe S3UrlExpirationPeriod.FallbackValue
    }

    "return fallback configuration when neither bespoke service configuration or default configuration are defined" in {
      when(mockConfiguration.getMilliseconds(s3UrlExpirationPeriodKeyFor(SomeServiceName))).thenReturn(None)
      when(mockConfiguration.getMilliseconds(DefaultS3UrlExpirationKey)).thenReturn(None)

      playBasedServiceConfiguration.s3UrlExpirationPeriod(SomeServiceName) shouldBe S3UrlExpirationPeriod.FallbackValue
    }

    "return fallback configuration when bespoke service configuration is invalid (greater than 7 days) and default configuration is not defined" in {
      when(mockConfiguration.getMilliseconds(s3UrlExpirationPeriodKeyFor(SomeServiceName))).thenReturn(Some(8.days.toMillis))
      when(mockConfiguration.getMilliseconds(DefaultS3UrlExpirationKey)).thenReturn(None)

      playBasedServiceConfiguration.s3UrlExpirationPeriod(SomeServiceName) shouldBe S3UrlExpirationPeriod.FallbackValue
    }
  }
}

private object PlayBasedServiceConfigurationSpec {
  val SomeServiceName = "business-rates-attachments"
  val DefaultS3UrlExpirationKey = "Test.upscan.default.aws.s3.urlExpirationPeriod"

  def s3UrlExpirationPeriodKeyFor(service: String): String =
    s"Test.upscan.consuming-services.$service.aws.s3.urlExpirationPeriod"
}