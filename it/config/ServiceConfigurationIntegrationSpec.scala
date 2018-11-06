package config
import org.scalatest.{Matchers, WordSpec}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

import scala.concurrent.duration._

class ServiceConfigurationIntegrationSpec extends WordSpec with Matchers with GuiceOneServerPerSuite{
  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      s"Test.upscan.consuming-services.1hour-ok.aws.s3.urlExpirationPeriod"  -> "1 hour",
      s"Test.upscan.consuming-services.1days-ok.aws.s3.urlExpirationPeriod"  -> "1 day",
      s"Test.upscan.consuming-services.7days-bad.aws.s3.urlExpirationPeriod" -> "7 days"
    )
    .build()

  val testInstance = app.injector.instanceOf[ServiceConfiguration]

  "ServiceConfiguration" should {
    "return 1 hour when the configured value is less than the maximum allowed" in {
      testInstance.s3UrlExpirationPeriod("1hour-ok") shouldBe 1.hour
    }

    "return 1 day when the configured value is exactly equal to the maximum allowed" in {
      testInstance.s3UrlExpirationPeriod("1days-ok") shouldBe 1.day
    }

    "return the maximum allowed (1 day) when the configured value is greater than the maximum" in {
      testInstance.s3UrlExpirationPeriod("7days-bad") shouldBe 1.day
    }

    "throw an exception when the configured value is missing" in {
      intercept[IllegalStateException] {
        testInstance.s3UrlExpirationPeriod("missing-key")
      }
    }
  }
}
