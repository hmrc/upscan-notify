/*
 * Copyright 2021 HM Revenue & Customs
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

import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

import scala.concurrent.duration.DurationInt

class ServiceConfigurationIntegrationSpec extends AnyWordSpec with should.Matchers with GuiceOneServerPerSuite:
  override implicit lazy val app: Application = GuiceApplicationBuilder()
    .configure(
      s"consuming-services.1hour-ok.aws.s3.urlExpirationPeriod"  -> "1 hour",
      s"consuming-services.7days-ok.aws.s3.urlExpirationPeriod"  -> "7 days",
      s"consuming-services.8days-bad.aws.s3.urlExpirationPeriod" -> "8 days"
    )
    .build()

  val testInstance = app.injector.instanceOf[ServiceConfiguration]

  "ServiceConfiguration" should:
    "return 1 hour when the configured value is less than the maximum allowed" in:
      testInstance.s3UrlExpirationPeriod("1hour-ok") shouldBe 1.hour

    "return 7 days when the configured value is exactly equal to the maximum allowed" in:
      testInstance.s3UrlExpirationPeriod("7days-ok") shouldBe 7.day

    "return the fallback value when the configured value is greater than the maximum allowed (and a valid default value is not configured)" in:
      testInstance.s3UrlExpirationPeriod("8days-bad") shouldBe 1.day

    "return the fallback value when the configured value is missing (and a valid default value is not configured)" in:
      testInstance.s3UrlExpirationPeriod("missing-key") shouldBe 1.day
