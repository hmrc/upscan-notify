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

package uk.gov.hmrc.upscannotify.config

import org.mockito.Mockito.when
import play.api.Configuration
import uk.gov.hmrc.upscannotify.test.UnitSpec

import scala.concurrent.duration._

class PlayBasedServiceConfigurationSpec extends UnitSpec:

  import PlayBasedServiceConfigurationSpec._

  private val mockConfiguration                  = mock[Configuration]
  private lazy val playBasedServiceConfiguration = {
    when(mockConfiguration.get[FiniteDuration](DefaultS3UrlExpirationKey))
      .thenReturn(6.hours)

    PlayBasedServiceConfiguration(mockConfiguration)
  }

  "s3UrlExpirationPeriod" should:
    "return relevant config for a translated serviceName containing the invalid '/' and '.' characters" in:
      when(mockConfiguration.getOptional[Duration](s3UrlExpirationPeriodKeyFor("Mozilla-4-0")))
        .thenReturn(Some(1.day))

      playBasedServiceConfiguration.s3UrlExpirationPeriod("Mozilla/4.0") shouldBe 1.day

    /*
     * A typesafe config path cannot contain a comma.
     * Invoking config.get("one,two") results in the exception:
     * com.typesafe.config.ConfigException$BadPath: path parameter: Invalid path 'one,two': Token not allowed in path expression: ',' (you can double-quote this token if you really want it here)
     */
    "return relevant config for a translated serviceName containing the invalid ',' character" in:
      when(mockConfiguration.getOptional[Duration](s3UrlExpirationPeriodKeyFor("serviceName-withComma")))
        .thenReturn(Some(1.day))

      playBasedServiceConfiguration.s3UrlExpirationPeriod("serviceName,withComma") shouldBe 1.day

    "return bespoke service configuration when defined and valid (at most 7 days)" in:
      when(mockConfiguration.getOptional[Duration](s3UrlExpirationPeriodKeyFor(SomeServiceName)))
        .thenReturn(Some(2.days))

      playBasedServiceConfiguration.s3UrlExpirationPeriod(SomeServiceName) shouldBe 2.days

    "return default configuration (6 hours) when valid and bespoke service configuration is not defined" in:
      when(mockConfiguration.getOptional[Duration](s3UrlExpirationPeriodKeyFor(SomeServiceName)))
        .thenReturn(None)

      playBasedServiceConfiguration.s3UrlExpirationPeriod(SomeServiceName) shouldBe 6.hours

    "return default configuration when bespoke service configuration is invalid (greater than 7 days)" in:
      when(mockConfiguration.getOptional[Duration](s3UrlExpirationPeriodKeyFor(SomeServiceName)))
        .thenReturn(Some(8.days))

      playBasedServiceConfiguration.s3UrlExpirationPeriod(SomeServiceName) shouldBe 6.hours

private object PlayBasedServiceConfigurationSpec:
  val SomeServiceName           = "business-rates-attachments"
  val DefaultS3UrlExpirationKey = "default.aws.s3.urlExpirationPeriod"

  def s3UrlExpirationPeriodKeyFor(service: String): String =
    s"consuming-services.$service.aws.s3.urlExpirationPeriod"
