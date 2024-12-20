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

package uk.gov.hmrc.upscannotify.connector

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.scalatest.{BeforeAndAfterAll, GivenWhenThen}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import uk.gov.hmrc.http.test.HttpClientV2Support
import uk.gov.hmrc.upscannotify.model._
import uk.gov.hmrc.upscannotify.test.UnitSpec

import java.net.URL
import java.time.Instant
import scala.concurrent.ExecutionContext

class HttpNotificationServiceSpec
  extends UnitSpec
     with GivenWhenThen
     with ScalaFutures
     with IntegrationPatience
     with BeforeAndAfterAll
     with HttpClientV2Support:

  private val callbackServer = WireMockServer(wireMockConfig().port(11111))

  import ExecutionContext.Implicits.global

  override def beforeAll(): Unit =
    callbackServer.start()

  override def afterAll(): Unit =
    callbackServer.stop()

  private def stubCallbackReceiverToReturnValidResponse(): Unit =
    callbackServer.stubFor:
      post(urlEqualTo("/myservice/123"))
        .willReturn:
          aResponse()
            .withStatus(204)

  private def stubCallbackReceiverToReturnInvalidResponse(): Unit =
    callbackServer.stubFor:
      post(urlEqualTo("/myservice/123"))
        .willReturn:
          aResponse()
            .withStatus(503)

  "HttpNotificationService" should:
    "post JSON to the passed in callback URL for upload success callback" in:

      Given("there is working host that can receive callback")
      val callbackUrl = URL("http://localhost:11111/myservice/123")
      val downloadUrl = URL("http://remotehost/bucket/123")
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
      val service = HttpNotificationService(httpClientV2)
      val result  = service.notifySuccessfulCallback(notification)

      Then("service should return success")
      result.futureValue

      And("callback URL is called with expected JSON body")
      callbackServer.verify:
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
            """.stripMargin))

    "post JSON to the passed in callback URL for upload failure callback when file is quarantined" in:
      Given("there is working host that can receive callback")
      val callbackUrl = URL("http://localhost:11111/myservice/123")
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
      val service = HttpNotificationService(httpClientV2)
      service.notifyFailedCallback(notification).futureValue

      And("callback URL is called with expected JSON body")
      callbackServer.verify:
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
          """.stripMargin))

    "post JSON to the passed in callback URL for upload failure callback when file is rejected" in:
      Given("there is working host that can receive callback")
      val callbackUrl = URL("http://localhost:11111/myservice/123")
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
      val service = HttpNotificationService(httpClientV2)
      service.notifyFailedCallback(notification).futureValue

      And("callback URL is called with expected JSON body")
      callbackServer.verify:
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
            """.stripMargin))

    "return error when called host returns HTTP error response" in:
      Given("host that would receive callback returns errors")
      val callbackUrl = URL("http://localhost:11111/myservice/123")
      val downloadUrl = URL("http://remotehost/bucket/123")
      stubCallbackReceiverToReturnInvalidResponse()
      val initiateDate = Instant.parse("2018-04-24T09:30:00Z")

      When("the service is called")
      val notification =
        SuccessfulProcessingDetails(
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
      val service = HttpNotificationService(httpClientV2)
      val result = service.notifySuccessfulCallback(notification)

      Then("service should return an error")
      result.failed.futureValue

    "return error when remote call fails" in:
      Given("host that would receive callback is not reachable")
      val callbackUrl = URL("http://invalid-host-name:11111/myservice/123")
      val downloadUrl = URL("http://remotehost/bucket/123")
      stubCallbackReceiverToReturnInvalidResponse()
      val initiateDate = Instant.parse("2018-04-24T09:30:00Z")

      When("the service is called")
      val notification =
        SuccessfulProcessingDetails(
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
      val service = HttpNotificationService(httpClientV2)
      val result  = service.notifySuccessfulCallback(notification)

      Then("service should return an error")
      result.failed.futureValue
