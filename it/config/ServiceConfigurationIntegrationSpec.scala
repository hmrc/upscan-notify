package config
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

import scala.concurrent.duration._

class ServiceConfigurationIntegrationSpec extends AnyWordSpecLike with should.Matchers with GuiceOneServerPerSuite {
  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      s"consuming-services.1hour-ok.aws.s3.urlExpirationPeriod"  -> "1 hour",
      s"consuming-services.7days-ok.aws.s3.urlExpirationPeriod"  -> "7 days",
      s"consuming-services.8days-bad.aws.s3.urlExpirationPeriod" -> "8 days"
    )
    .build()

  val testInstance = app.injector.instanceOf[ServiceConfiguration]

  "ServiceConfiguration" should {
    "return 1 hour when the configured value is less than the maximum allowed" in {
      testInstance.s3UrlExpirationPeriod("1hour-ok") shouldBe 1.hour
    }

    "return 7 days when the configured value is exactly equal to the maximum allowed" in {
      testInstance.s3UrlExpirationPeriod("7days-ok") shouldBe 7.day
    }

    "return the fallback value when the configured value is greater than the maximum allowed (and a valid default value is not configured)" in {
      testInstance.s3UrlExpirationPeriod("8days-bad") shouldBe 1.day
    }

    "return the fallback value when the configured value is missing (and a valid default value is not configured)" in {
      testInstance.s3UrlExpirationPeriod("missing-key") shouldBe 1.day
    }
  }
}
