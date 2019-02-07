/*
 * Copyright 2019 HM Revenue & Customs
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

import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar.mock
import play.api.{Configuration, Environment, Mode}

import scala.concurrent.duration._
import uk.gov.hmrc.play.test.UnitSpec

class PlayBasedServiceConfigurationSpec extends UnitSpec {

  val mockConfiguration = mock[Configuration]
  val mockEnvironment = mock[Environment]

  when(mockEnvironment.mode).thenReturn(Mode.Test)

  val playBasedServiceConfiguration = new PlayBasedServiceConfiguration(mockConfiguration, mockEnvironment)

  "s3UrlExpirationPeriod" should {

    "return relevant config for a valid serviceName" in {
      when(mockConfiguration.getMilliseconds("Test.upscan.consuming-services.business-rates-attachments.aws.s3.urlExpirationPeriod"))
        .thenReturn(Some(1.days.toMillis))

      playBasedServiceConfiguration.s3UrlExpirationPeriod("business-rates-attachments") shouldBe 1.days
    }

    "return relevant config for a translated serviceName with invalid chars" in {
      when(mockConfiguration.getMilliseconds("Test.upscan.consuming-services.Mozilla-4-0.aws.s3.urlExpirationPeriod"))
        .thenReturn(Some(1.days.toMillis))

      playBasedServiceConfiguration.s3UrlExpirationPeriod("Mozilla/4.0") shouldBe 1.days
    }


  }

}