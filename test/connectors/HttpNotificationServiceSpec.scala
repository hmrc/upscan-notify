/*
 * Copyright 2022 HM Revenue & Customs
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
import java.time.{Clock, Duration, Instant, ZoneId}

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import model._
import org.scalatest.{BeforeAndAfterAll, GivenWhenThen}
import test.{IncrementingClock, UnitSpec}
import uk.gov.hmrc.http.test.HttpClientSupport

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.Try

class HttpNotificationServiceSpec extends UnitSpec with GivenWhenThen with BeforeAndAfterAll with HttpClientSupport {
  private val callbackServer = new WireMockServer(wireMockConfig().port(11111))

  private val baseTime = Instant.parse("2018-12-01T14:36:30Z")

  private val fixedClock                    = Clock.fixed(baseTime, ZoneId.systemDefault())
  private val clock                         = new IncrementingClock(fixedClock.millis(), Duration.ofSeconds(1))
  private implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  override def beforeAll(): Unit =
    callbackServer.start()

  override def afterAll(): Unit =
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
      val initiateDate = Instant.parse("2018-04-24T09:30:00Z")

      When("the service is called")
      val notification = SuccessfulProcessingDetails(
        callbackUrl     = callbackUrl,
        reference       = FileReference("upload-file-reference"),
        downloadUrl     = downloadUrl,
        size            = 123L,
        fileName        = "test.pdf",
        fileMimeType    = "application/pdf",
        uploadTimestamp = initiateDate,
        checksum        = "1a2b3c4d5e",
        requestContext  = RequestContext(Some("requestId"), Some("sessionId"), "127.0.0.1")
      )
      val service = new HttpNotificationService(httpClient, clock)
      val result  = Try(Await.result(service.notifySuccessfulCallback(notification), 30.seconds))

      Then("service should return success")
      result.isSuccess shouldBe true

      result.get should contain allOf (
        Checkpoint("x-amz-meta-upscan-notify-callback-started", baseTime),
        Checkpoint("x-amz-meta-upscan-notify-callback-ended", baseTime.plusSeconds(1))
      )

      And("callback URL is called with expected JSON body")
      callbackServer.verify(
        postRequestedFor(urlEqualTo("/myservice/123"))
          .withRequestBody(equalToJson("""
          |{ "reference" : "upload-file-reference",
          |  "downloadUrl" : "http://remotehost/bucket/123",
          |  "fileStatus": "READY",
          |  "uploadDetails": {
          |	    "uploadTimestamp": "2018-04-24T09:30:00Z",
          |	    "checksum": "1a2b3c4d5e",
          |     "fileMimeType": "application/pdf",
          |     "fileName": "test.pdf",
          |     "size": 123
          |  }
          |}
        """.stripMargin)))

    }

    "post JSON to the passed in callback URL for upload failure callback when file is quarantined" in {

      Given("there is working host that can receive callback")
      val callbackUrl = new URL("http://localhost:11111/myservice/123")
      stubCallbackReceiverToReturnValidResponse()

      When("the service is called")
      val notification =
        FailedProcessingDetails(
          callbackUrl     = callbackUrl,
          reference       = FileReference("quarantine-file-reference"),
          fileName        = "test.pdf",
          uploadTimestamp = Instant.parse("2018-04-24T09:30:00Z"),
          error           = ErrorDetails("QUARANTINE", "This file has a virus"),
          requestContext  = RequestContext(Some("requestId"), Some("sessionId"), "127.0.0.1")
        )
      val service = new HttpNotificationService(httpClient, clock)
      val result  = Try(Await.result(service.notifyFailedCallback(notification), 30.seconds))

      Then("service should return success")
      result.isSuccess shouldBe true

      And("callback URL is called with expected JSON body")

      callbackServer.verify(
        postRequestedFor(urlEqualTo("/myservice/123"))
          .withRequestBody(equalToJson("""
         | {
         |   "reference" : "quarantine-file-reference",
         |   "fileStatus" : "FAILED",
         |   "failureDetails" : {
         |     "failureReason" : "QUARANTINE",
         |     "message" : "This file has a virus"
         |   }
         | }
       """.stripMargin)))

    }

    "post JSON to the passed in callback URL for upload failure callback when file is rejected" in {

      Given("there is working host that can receive callback")
      val callbackUrl = new URL("http://localhost:11111/myservice/123")
      stubCallbackReceiverToReturnValidResponse()

      When("the service is called")
      val notification =
        FailedProcessingDetails(
          callbackUrl     = callbackUrl,
          reference       = FileReference("rejected-file-reference"),
          fileName        = "test.pdf",
          uploadTimestamp = Instant.parse("2018-04-24T09:30:00Z"),
          error           = ErrorDetails("REJECTED", "MIME type [some-type] not allowed for service [some-service]"),
          requestContext  = RequestContext(Some("requestId"), Some("sessionId"), "127.0.0.1")
        )
      val service = new HttpNotificationService(httpClient, clock)
      val result  = Try(Await.result(service.notifyFailedCallback(notification), 30.seconds))

      Then("service should return success")
      result.isSuccess shouldBe true

      And("callback URL is called with expected JSON body")
      callbackServer.verify(
        postRequestedFor(urlEqualTo("/myservice/123"))
          .withRequestBody(equalToJson("""
           | {
           |   "reference" : "rejected-file-reference",
           |   "fileStatus" : "FAILED",
           |   "failureDetails" : {
           |     "failureReason" : "REJECTED",
           |     "message" : "MIME type [some-type] not allowed for service [some-service]"
           |   }
           | }
           """.stripMargin)))
    }

    "return error when called host returns HTTP error response" in {

      Given("host that would receive callback returns errors")
      val callbackUrl = new URL("http://localhost:11111/myservice/123")
      val downloadUrl = new URL("http://remotehost/bucket/123")
      stubCallbackReceiverToReturnInvalidResponse()
      val initiateDate = Instant.parse("2018-04-24T09:30:00Z")

      When("the service is called")
      val notification =
        model.SuccessfulProcessingDetails(
          callbackUrl     = callbackUrl,
          reference       = FileReference("file-reference"),
          downloadUrl     = downloadUrl,
          size            = 0L,
          fileName        = "test.pdf",
          fileMimeType    = "application/pdf",
          uploadTimestamp = initiateDate,
          checksum        = "1a2b3c4d5e",
          requestContext  = RequestContext(Some("requestId"), Some("sessionId"), "127.0.0.1")
        )
      val service = new HttpNotificationService(httpClient, clock)
      val result  = Try(Await.result(service.notifySuccessfulCallback(notification), 30.seconds))

      Then("service should return an error")
      result.isSuccess shouldBe false

    }

    "return error when remote call fails" in {
      Given("host that would receive callback is not reachable")
      val callbackUrl = new URL("http://invalid-host-name:11111/myservice/123")
      val downloadUrl = new URL("http://remotehost/bucket/123")
      stubCallbackReceiverToReturnInvalidResponse()
      val initiateDate = Instant.parse("2018-04-24T09:30:00Z")

      When("the service is called")
      val notification =
        model.SuccessfulProcessingDetails(
          callbackUrl     = callbackUrl,
          reference       = FileReference("file-reference"),
          downloadUrl     = downloadUrl,
          size            = 0L,
          fileName        = "test/pdf",
          fileMimeType    = "application/pdf",
          uploadTimestamp = initiateDate,
          checksum        = "1a2b3c4d5e",
          requestContext  = RequestContext(Some("requestId"), Some("sessionId"), "127.0.0.1")
        )
      val service = new HttpNotificationService(httpClient, clock)
      val result  = Try(Await.result(service.notifySuccessfulCallback(notification), 30.seconds))

      Then("service should return an error")
      result.isSuccess shouldBe false
    }
  }

}
