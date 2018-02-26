/*
 * Copyright 2018 HM Revenue & Customs
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

package service

import java.net.URL
import javax.inject.Inject

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.typesafe.config.Config
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{GivenWhenThen, Matchers}
import play.api.Configuration
import play.api.libs.json.Writes
import play.api.libs.ws.WSClient
import uk.gov.hmrc.http.hooks.HttpHook
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.AppName
import uk.gov.hmrc.play.bootstrap.http.{DefaultHttpClient, HttpClient}
import uk.gov.hmrc.play.http.ws.WSHttp
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

class HttpNotificationServiceSpec extends UnitSpec with Matchers with GivenWhenThen with MockitoSugar {
  val callbackServer = new WireMockServer(wireMockConfig().port(11111))

  callbackServer.stubFor(
    post(urlEqualTo("/myservice/123"))
      .willReturn(
        aResponse()
          .withStatus(200)
      ))

  "HttpNotificationService" should {
    "post JSON to the passed in callback URL" in {
      callbackServer.start()

      Given("a callback URL")
      val url = new URL("http://localhost:11111/myservice/123")

      When("the service is called")
      val service = new HttpNotificationService(new TestHttpClient)(ExecutionContext.Implicits.global)
      val result  = service.notifyCallback(url)

      Then("callback URL is called with an empty body (body will be generated later)")
      callbackServer.verify(postRequestedFor(urlEqualTo("/myservice/123")))

      callbackServer.stop()
    }
  }

}

class TestHttpClient extends HttpClient with WSHttp {
  override lazy val configuration: Option[Config] = None
  override val hooks                              = Seq.empty
}
