package services

import java.net.URL
import java.time.Instant

import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.sqs.model.Message
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, post, urlEqualTo}
import com.github.tomakehurst.wiremock.verification.LoggedRequest
import connectors.ReadyCallbackBody
import harness.application.IntegrationTestsApplication
import harness.aws.Mocks
import harness.model.JsonReads._
import harness.wiremock.WithWireMock
import org.scalatest.{Matchers, WordSpecLike}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceableModuleConversions
import play.api.libs.json._

import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{Await, Future}

object TestData {
  val bucketName                       = "bucket-name-UrlExpirationIntegrationSpec"
  val objectKey                        = "object-key-UrlExpirationIntegrationSpec"
  val callbackPath                     = "/UrlExpirationIntegrationSpec-callback-url"
  val callbackUrl                      = s"http://localhost:8080$callbackPath"
  val expirationPeriod: FiniteDuration = 7.days
  val expirationUrl                    = new URL(s"https://$bucketName.$objectKey.${expirationPeriod.toMillis}.com")

  def metadata(
    fileReference: String    = "file-reference",
    callbackUrl: String      = TestData.callbackUrl,
    initiateDate: String     = Instant.now.toString,
    checksum: String         = "checksum-123",
    originalFilename: String = "original-filename123",
    mimeType: String         = "application/json",
    clientIp: String         = "127.0.0.1",
    requestId: String        = "request-id-123",
    sessionId: String        = "session-id-123",
    consumingService: String = "consuming-service-123"): ObjectMetadata = {
    val metadata = new ObjectMetadata()
    metadata.addUserMetadata("file-reference", fileReference)
    metadata.addUserMetadata("callback-url", callbackUrl)
    metadata.addUserMetadata("initiate-date", initiateDate)
    metadata.addUserMetadata("checksum", checksum)
    metadata.addUserMetadata("original-filename", originalFilename)
    metadata.addUserMetadata("mime-type", mimeType)
    metadata.addUserMetadata("client-ip", clientIp)
    metadata.addUserMetadata("request-id", requestId)
    metadata.addUserMetadata("session-id", sessionId)
    metadata.addUserMetadata("consuming-service", consumingService)
    metadata
  }

  val outboundMessage: Message = new Message()
    .withMessageId("OutboundAmazonSQS-UrlExpirationIntegrationSpec")
    .withReceiptHandle("OutboundAmazonSQS-UrlExpirationIntegrationSpec")
    .withBody(s"""
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
          """.stripMargin)
}

class UrlExpirationIntegrationSpec
    extends WordSpecLike
    with Matchers
    with GuiceOneServerPerSuite
    with GuiceableModuleConversions
    with WithWireMock {

  override lazy val app: Application = IntegrationTestsApplication.defaultApplicationBuilder().build()

  "receiveMessage" should {
    "generate pre-signed download url with expiration period derived from application.conf Test section" in {
      Mocks.setup(
        IntegrationTestsApplication.mockAmazonSQS,
        TestData.bucketName,
        TestData.objectKey,
        TestData.outboundMessage)
      Mocks.setup(
        IntegrationTestsApplication.mockAmazonS3,
        TestData.bucketName,
        TestData.objectKey,
        TestData.metadata(),
        TestData.expirationUrl)

      wireMockServer.stubFor(
        post(urlEqualTo(TestData.callbackPath))
          .willReturn(
            aResponse()
              .withStatus(204)
          ))

      val notifyOnSuccessfulFileUploadMessageProcessingJob =
        app.injector.instanceOf[NotifyOnSuccessfulFileUploadMessageProcessingJob]

      val result: Future[Unit] = notifyOnSuccessfulFileUploadMessageProcessingJob.run()

      Await.result(result, 1.seconds)

      val loggedRequests =
        wireMockServer.findAll(WireMock.postRequestedFor(WireMock.urlMatching(TestData.callbackPath)))

      if (loggedRequests.isEmpty)
        fail(s"WireMock did not receive a notification callback for url: [${TestData.callbackUrl}].")

      val loggedRequest: LoggedRequest = loggedRequests.get(0)

      val bodyAsString = loggedRequest.getBodyAsString

      val json: JsValue                                   = Json.parse(bodyAsString)
      val callbackBodyResult: JsResult[ReadyCallbackBody] = json.validate[ReadyCallbackBody]

      callbackBodyResult match {
        case JsSuccess(callbackBody, _) => callbackBody.downloadUrl shouldBe TestData.expirationUrl
        case _                          => fail(s"Failed to find sent notification for file reference: [${TestData.objectKey}].")
      }
    }
  }
}
