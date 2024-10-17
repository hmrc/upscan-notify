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

package uk.gov.hmrc.upscannotify.service

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, post, urlEqualTo}
import com.github.tomakehurst.wiremock.verification.LoggedRequest
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.when
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.{Application, Configuration, Environment}
import play.api.inject.{Binding, bind}
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModuleConversions}
import play.api.libs.json._
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{DeleteMessageRequest, DeleteMessageResponse, Message, ReceiveMessageRequest, ReceiveMessageResponse}
import uk.gov.hmrc.upscannotify.NotifyModule
import uk.gov.hmrc.upscannotify.connector.ReadyCallbackBody
import uk.gov.hmrc.upscannotify.connector.aws.{S3FileManager, S3ObjectMetadata}
import uk.gov.hmrc.upscannotify.harness.model.JsonReads.given
import uk.gov.hmrc.upscannotify.harness.wiremock.WithWireMock
import uk.gov.hmrc.upscannotify.model.S3ObjectLocation

import java.net.URL
import java.time.Instant
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._

object TestData:
  val bucketName       = "bucket-name-UrlExpirationIntegrationSpec"
  val objectKey        = "object-key-UrlExpirationIntegrationSpec"
  val callbackPath     = "/UrlExpirationIntegrationSpec-callback-url"
  val callbackUrl      = s"http://localhost:8080$callbackPath"
  val expirationPeriod = 7.days
  val expirationUrl    = URL(s"https://$bucketName.$objectKey.${expirationPeriod.toMillis}.com")
  val fileReference    = "file-reference-UrlExpirationIntegrationSpec"
  val fileSizeInBytes  = 12345678L

  def metadata(
    objectLocation  : S3ObjectLocation,
    fileReference   : String  = fileReference,
    callbackUrl     : String  = callbackUrl,
    initiateDate    : Instant = Instant.now(),
    checksum        : String  = "checksum-123",
    originalFilename: String  = "original-filename123",
    mimeType        : String  = "application/json",
    clientIp        : String  = "127.0.0.1",
    requestId       : String  = "request-id-123",
    sessionId       : String  = "session-id-123",
    consumingService: String  = "consuming-service-123"
  ): S3ObjectMetadata =
    S3ObjectMetadata(
      objectLocation,
      items             = Map(
                            "file-reference"    -> fileReference,
                            "callback-url"      -> callbackUrl,
                            "initiate-date"     -> initiateDate.toString,
                            "checksum"          -> checksum,
                            "original-filename" -> originalFilename,
                            "mime-type"         -> mimeType,
                            "client-ip"         -> clientIp,
                            "request-id"        -> requestId,
                            "session-id"        -> sessionId,
                            "consuming-service" -> consumingService,
                          ),
      uploadedTimestamp = initiateDate,
      getContentLength  = fileSizeInBytes
    )

  val outboundMessage: Message =
    Message.builder()
      .messageId("OutboundAmazonSQS-UrlExpirationIntegrationSpec")
      .receiptHandle("OutboundAmazonSQS-UrlExpirationIntegrationSpec")
      .body(s"""
          |{
          |  "Records": [
          |    {
          |      "eventVersion": "2.0",
          |      "eventSource": "aws:s3",
          |      "awsRegion": "eu-west-2",
          |      "eventTime": "2018-07-12T14:00:59.845Z",
          |      "eventName": "ObjectCreated:Put",
          |      "s3": {
          |        "bucket": {
          |          "name": "${TestData.bucketName}"
          |        },
          |        "object": {
          |          "key": "${TestData.objectKey}"
          |        }
          |      }
          |    }
          |  ]
          |}
            """.stripMargin
      )
      .build()

/**
  * Testing subclass of NotifyModule that disables components that should not be run during integration testing.
  */
class NotifyModuleWithoutContinuousPoller extends NotifyModule:
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] =
    super
      .bindings(environment, configuration)
      .filterNot(_.key == bind[ContinuousPoller])    // We don't want the normal poller to be invoked in the background


class UrlExpirationIntegrationSpec
  extends AnyWordSpec
     with should.Matchers
     with GuiceOneServerPerSuite
     with GuiceableModuleConversions
     with ScalaFutures
     with IntegrationPatience
     with MockitoSugar
     with WithWireMock:

  val sqsClient            = mock[SqsAsyncClient]
  val s3FileManager        = mock[S3FileManager]
  val downloadUrlGenerator = mock[DownloadUrlGenerator]

  override lazy val app: Application =
    GuiceApplicationBuilder()
      .disable(classOf[NotifyModule])
      .overrides(NotifyModuleWithoutContinuousPoller())
      .overrides(
        bind[SqsAsyncClient      ].toInstance(sqsClient),
        bind[S3FileManager       ].toInstance(s3FileManager),
        bind[DownloadUrlGenerator].toInstance(downloadUrlGenerator)
      )
      .build()

  "receiveMessage" should:
    "generate pre-signed download url with expiration period derived from application.conf Test section" in:
      when(sqsClient.receiveMessage(any[ReceiveMessageRequest]))
        .thenReturn:
          Future
            .successful(ReceiveMessageResponse.builder().messages(Seq(TestData.outboundMessage).asJava).build)
            .asJava

      when(sqsClient.deleteMessage(any[DeleteMessageRequest]))
        .thenReturn(Future.successful(DeleteMessageResponse.builder().build()).asJava)

      val objectLocation = S3ObjectLocation(TestData.bucketName, TestData.objectKey)
      when(s3FileManager.getObjectMetadata(objectLocation))
        .thenReturn:
          Future.successful(TestData.metadata(objectLocation))

      when(downloadUrlGenerator.generate(eqTo(objectLocation), any[SuccessfulFileDetails]))
        .thenReturn(TestData.expirationUrl)

      wireMockServer.stubFor:
        post(urlEqualTo(TestData.callbackPath))
          .willReturn:
            aResponse()
              .withStatus(204)

      val notifyOnSuccessfulFileUploadMessageProcessingJob =
        app.injector.instanceOf[NotifyOnSuccessfulFileUploadMessageProcessingJob]

      notifyOnSuccessfulFileUploadMessageProcessingJob.run().futureValue

      val loggedRequests =
        wireMockServer.findAll(WireMock.postRequestedFor(WireMock.urlMatching(TestData.callbackPath)))

      if loggedRequests.isEmpty then
        fail(s"WireMock did not receive a notification callback for url: [${TestData.callbackUrl}].")

      val loggedRequest: LoggedRequest = loggedRequests.get(0)

      val bodyAsString = loggedRequest.getBodyAsString

      val json: JsValue                                   = Json.parse(bodyAsString)
      val callbackBodyResult: JsResult[ReadyCallbackBody] = json.validate[ReadyCallbackBody]

      callbackBodyResult match
        case JsSuccess(callbackBody, _) =>
          callbackBody.downloadUrl shouldBe TestData.expirationUrl
          callbackBody.reference.reference shouldBe TestData.fileReference
          callbackBody.uploadDetails.size shouldBe TestData.fileSizeInBytes
        case _                          =>
          fail(s"Failed to find sent notification for file reference: [${TestData.fileReference}].")
