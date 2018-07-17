package harness.application

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.sqs.AmazonSQS
import harness.modules.FilteredNotifyModule
import javax.inject.Provider
import modules.NotifyModule
import org.scalatest.mockito.MockitoSugar.mock
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import services.ContinuousPoller

/**
  * Bootstrap logic for an integration test application.
  */
object IntegrationTestsApplication {
  val mockAmazonS3: AmazonS3 = mock[AmazonS3]
  val mockAmazonSQS: AmazonSQS = mock[AmazonSQS]

  def defaultApplicationBuilder(): GuiceApplicationBuilder = {
    new GuiceApplicationBuilder(disabled = Seq(classOf[ContinuousPoller]))
      .disable(classOf[NotifyModule])
      .overrides(new FilteredNotifyModule())
      .overrides(
        bind[AmazonSQS].to(new SingletonProvider(mockAmazonSQS)),
        bind[AmazonS3].to(new SingletonProvider(mockAmazonS3))
      )
  }

  private class SingletonProvider[T](singleton: T) extends Provider[T] {
    override def get(): T = singleton
  }
}
