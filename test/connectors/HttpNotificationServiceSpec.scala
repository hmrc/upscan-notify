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

package connectors

import java.net.URL

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.typesafe.config.Config
import model.{QuarantinedFile, UploadedFile}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, GivenWhenThen, Matchers}
import play.api.libs.ws.ahc.AhcWSClient
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.ws.WSHttp
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.Try

class HttpNotificationServiceSpec
    extends UnitSpec
    with Matchers
    with GivenWhenThen
    with MockitoSugar
    with BeforeAndAfterAll {
  val callbackServer = new WireMockServer(wireMockConfig().port(11111))

  override def beforeAll() =
    callbackServer.start()

  override def afterAll() =
    callbackServer.stop()

  private def stubCallbackReceiverToReturnValidResponse(): Unit =
    callbackServer.stubFor(
      post(urlEqualTo("/myservice/123"))
        .willReturn(
          aResponse()
            .withStatus(204)
        ))

  private def stubCallbackReceiverToReturnInvalidResponse(): Unit =
    callbackServer.stubFor(
      post(urlEqualTo("/myservice/123"))
        .willReturn(
          aResponse()
            .withStatus(503)
        ))

  "HttpNotificationService" should {
    "post JSON to the passed in callback URL for upload success callback" in {

      Given("there is working host that can receive callback")
      val callbackUrl = new URL("http://localhost:11111/myservice/123")
      val downloadUrl = new URL("http://remotehost/bucket/123")
      stubCallbackReceiverToReturnValidResponse()

      When("the service is called")
      val notification = UploadedFile(callbackUrl, "upload-file-reference", downloadUrl, 0L)
      val service      = new HttpNotificationService(new TestHttpClient)(ExecutionContext.Implicits.global)
      val result       = Try(Await.result(service.notifySuccessfulCallback(notification), 30.seconds))

      Then("service should return success")
      result.isSuccess shouldBe true

      And("callback URL is called with expected JSON body")
      callbackServer.verify(
        postRequestedFor(urlEqualTo("/myservice/123"))
          .withRequestBody(equalToJson("""
          |{ "reference" : "upload-file-reference",
          |  "downloadUrl" : "http://remotehost/bucket/123",
          |  "fileStatus": "READY"
          |}
        """.stripMargin)))

    }

    "post JSON to the passed in callback URL for upload failure callback" in {

      Given("there is working host that can receive callback")
      val callbackUrl = new URL("http://localhost:11111/myservice/123")
      val downloadUrl = new URL("http://remotehost/bucket/123")
      stubCallbackReceiverToReturnValidResponse()

      When("the service is called")
      val notification = QuarantinedFile(callbackUrl, "quarantine-file-reference", "This file has a virus")
      val service      = new HttpNotificationService(new TestHttpClient)(ExecutionContext.Implicits.global)
      val result       = Try(Await.result(service.notifyFailedCallback(notification), 30.seconds))

      Then("service should return success")
      result.isSuccess shouldBe true

      And("callback URL is called with expected JSON body")
      callbackServer.verify(
        postRequestedFor(urlEqualTo("/myservice/123"))
          .withRequestBody(equalToJson("""
         |{ "reference" : "quarantine-file-reference",
         |  "details" : "This file has a virus",
         |  "fileStatus": "FAILED"
         |}
       """.stripMargin)))

    }

    "return error when called host returns HTTP error response" in {

      Given("host that would receive callback returns errors")
      val callbackUrl = new URL("http://localhost:11111/myservice/123")
      val downloadUrl = new URL("http://remotehost/bucket/123")
      stubCallbackReceiverToReturnInvalidResponse()

      When("the service is called")
      val notification = UploadedFile(callbackUrl, "file-reference", downloadUrl, 0L)
      val service      = new HttpNotificationService(new TestHttpClient)(ExecutionContext.Implicits.global)
      val result       = Try(Await.result(service.notifySuccessfulCallback(notification), 30.seconds))

      Then("service should return an error")
      result.isSuccess shouldBe false

    }

    "return error when remote call fails" in {
      Given("host that would receive callback is not reachable")
      val callbackUrl = new URL("http://invalid-host-name:11111/myservice/123")
      val downloadUrl = new URL("http://remotehost/bucket/123")
      stubCallbackReceiverToReturnInvalidResponse()

      When("the service is called")
      val notification = UploadedFile(callbackUrl, "file-reference", downloadUrl, 0L)
      val service      = new HttpNotificationService(new TestHttpClient)(ExecutionContext.Implicits.global)
      val result       = Try(Await.result(service.notifySuccessfulCallback(notification), 30.seconds))

      Then("service should return an error")
      result.isSuccess shouldBe false
    }
  }

}

class TestHttpClient extends HttpClient with WSHttp {
  implicit val system                             = ActorSystem()
  implicit val materializer                       = ActorMaterializer()
  override val wsClient                           = AhcWSClient()
  override lazy val configuration: Option[Config] = None
  override val hooks                              = Seq.empty

}
