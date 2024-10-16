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

package uk.gov.hmrc.upscannotify.service

import com.codahale.metrics.MetricRegistry
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, verifyNoMoreInteractions, when}
import org.scalatest.GivenWhenThen
import uk.gov.hmrc.play.bootstrap.metrics.Metrics
import uk.gov.hmrc.upscannotify.config.ServiceConfiguration
import uk.gov.hmrc.upscannotify.model._
import uk.gov.hmrc.upscannotify.test.UnitSpec

import java.net.URL
import java.time._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class NotifyOnFileProcessingEventFlowSpec extends UnitSpec with GivenWhenThen:

  val messageParser =
    new MessageParser:
      override def parse(message: Message) =
        message.body match
          case "VALID-BODY" => Future.successful(FileUploadEvent(S3ObjectLocation("bucket", message.id)))
          case _            => Future.failed(Exception("Invalid body"))

  val startTime   = Instant.parse("2018-04-24T09:45:10Z")
  val currentTime = Instant.parse("2018-04-24T09:45:15Z")

  val clock = Clock.fixed(currentTime, ZoneOffset.UTC)

  val callbackUrl = URL("http://localhost:8080")
  val downloadUrl = URL("http://remotehost/bucket/123")

  def metricsStub() = new Metrics:
    override val defaultRegistry: MetricRegistry = MetricRegistry()

  val sampleRequestContext = RequestContext(Some("REQUEST_ID"), Some("SESSION_ID"), "127.0.0.1")

  def sampleUploadedFile(objectLocation: S3ObjectLocation) =
    SuccessfulProcessingDetails(
      callbackUrl     = callbackUrl,
      reference       = FileReference(objectLocation.objectKey),
      downloadUrl     = downloadUrl,
      size            = 10L,
      fileName        = "test.pdf",
      fileMimeType    = "application/pdf",
      uploadTimestamp = startTime,
      checksum        = "1a2b3c4d5e",
      requestContext  = sampleRequestContext
    )

  val fileDetailsRetriever =
    new FileNotificationDetailsRetriever:
      override def retrieveUploadedFileDetails(objectLocation: S3ObjectLocation) =
        Future.successful(
          WithCheckpoints(
            sampleUploadedFile(objectLocation),
            Checkpoints(Seq(Checkpoint("x-amz-meta-upscan-notify-received", Instant.parse("2018-04-24T09:45:15Z"))))))

      override def retrieveQuarantinedFileDetails(objectLocation: S3ObjectLocation) = ???

  val serviceConfiguration = mock[ServiceConfiguration]
  when(serviceConfiguration.endToEndProcessingThreshold())
    .thenReturn(1.minute)

  "SuccessfulUploadNotificationProcessingFlow" should:
    "get messages from the queue consumer, and call notification service for valid messages" in:
      Given("there are only valid messages in a message queue")
      val validMessage = Message("ID", "VALID-BODY", "RECEIPT-1", clock.instant())

      val queueConsumer = mock[SuccessfulQueueConsumer]
      when(queueConsumer.poll())
        .thenReturn(Future.successful(List(validMessage)))
      when(queueConsumer.confirm(any[Message]))
        .thenReturn(Future.unit)

      val notificationService = mock[NotificationService]
      when(notificationService.notifySuccessfulCallback(any[SuccessfulProcessingDetails]))
        .thenReturn(Future.successful(Nil))

      val metrics = metricsStub()

      val auditingService = mock[UpscanAuditingService]

      val queueOrchestrator = NotifyOnSuccessfulFileUploadMessageProcessingJob(
        queueConsumer,
        messageParser,
        fileDetailsRetriever,
        notificationService,
        metrics,
        clock,
        auditingService,
        serviceConfiguration
      )

      When("the orchestrator is called")
      Await.result(queueOrchestrator.run(), 30.seconds)

      Then("the queue consumer should poll for messages")
      verify(queueConsumer).poll()

      And("callback recipient is notified")
      verify(notificationService).notifySuccessfulCallback(any[SuccessfulProcessingDetails])

      And("successfully processed messages are confirmed")
      verify(queueConsumer).confirm(validMessage)

      And("counter of successful processed messages is incremented")
      metrics.defaultRegistry.counter("successfulUploadNotificationSent").getCount shouldBe 1
      metrics.defaultRegistry.histogram("fileSize").getSnapshot.getValues          shouldBe Array(10L)
      metrics.defaultRegistry
        .timer("totalFileProcessingTime")
        .getSnapshot
        .getValues shouldBe Array((5.seconds).toNanos)

      metrics.defaultRegistry
        .timer("fileProcessingTimeExcludingNotification")
        .getSnapshot
        .getValues shouldBe Array((5.seconds).toNanos)

      And("audit events are emitted for all the events")

      verify(auditingService)
        .notifyFileUploadedSuccessfully(sampleUploadedFile(S3ObjectLocation("bucket", "ID")))

    "get messages from the queue consumer, and call notification service for valid messages and ignore invalid messages" in:
      Given("a mix of valid & invalid messages in a message queue")
      val validMessage1  = Message("ID1", "VALID-BODY", "RECEIPT-1", clock.instant())
      val invalidMessage = Message("ID2", "INVALID-BODY", "RECEIPT-2", clock.instant())
      val validMessage2  = Message("ID3", "VALID-BODY", "RECEIPT-3", clock.instant())

      val queueConsumer = mock[SuccessfulQueueConsumer]
      when(queueConsumer.poll())
        .thenReturn(Future.successful(List(validMessage1, invalidMessage, validMessage2)))

      when(queueConsumer.confirm(any[Message]))
        .thenReturn(Future.unit)

      val notificationService = mock[NotificationService]
      when(notificationService.notifySuccessfulCallback(any[SuccessfulProcessingDetails]))
        .thenReturn(Future.successful(Nil))

      val metrics = metricsStub()

      val auditingService = mock[UpscanAuditingService]

      val queueOrchestrator = NotifyOnSuccessfulFileUploadMessageProcessingJob(
        queueConsumer,
        messageParser,
        fileDetailsRetriever,
        notificationService,
        metrics,
        clock,
        auditingService,
        serviceConfiguration
      )

      When("the orchestrator is called")
      Await.result(queueOrchestrator.run(), 30.seconds)

      Then("the queue consumer should poll for messages")
      verify(queueConsumer).poll()

      And("notification service is called only for valid messages")
      verify(notificationService, times(2)).notifySuccessfulCallback(any[SuccessfulProcessingDetails])

      And("auditing service is called only for valid messages")
      verify(auditingService, times(2)).notifyFileUploadedSuccessfully(any[SuccessfulProcessingDetails])

      And("successfully processed messages are confirmed")
      verify(queueConsumer).confirm(validMessage1)
      verify(queueConsumer).confirm(validMessage2)

      And("invalid messages are not confirmed")
      verifyNoMoreInteractions(queueConsumer)

      And("counter of successful processed messages is incremented by count of successfully processed messages")
      metrics.defaultRegistry.counter("successfulUploadNotificationSent").getCount shouldBe 2
      metrics.defaultRegistry.histogram("fileSize").getSnapshot.getValues          shouldBe Array(10L, 10L)

    "do not confirm valid messages for which notification has failed" in:
      Given("there are only valid messages in a message queue")
      val validMessage1 = Message("ID1", "VALID-BODY", "RECEIPT-1", clock.instant())
      val validMessage2 = Message("ID2", "VALID-BODY", "RECEIPT-2", clock.instant())
      val validMessage3 = Message("ID3", "VALID-BODY", "RECEIPT-3", clock.instant())

      val queueConsumer = mock[SuccessfulQueueConsumer]
      when(queueConsumer.poll())
        .thenReturn(Future.successful(List(validMessage1, validMessage2, validMessage3)))
      when(queueConsumer.confirm(any[Message]))
        .thenReturn(Future.unit)

      val notificationService = mock[NotificationService]
      when(
        notificationService.notifySuccessfulCallback(SuccessfulProcessingDetails(
          callbackUrl     = callbackUrl,
          reference       = FileReference("ID1"),
          downloadUrl     = downloadUrl,
          size            = 10L,
          fileName        = "test.pdf",
          fileMimeType    = "application/pdf",
          uploadTimestamp = startTime,
          checksum        = "1a2b3c4d5e",
          requestContext  = sampleRequestContext
        ))
      )
        .thenReturn(Future.successful(Nil))

      when(
        notificationService.notifySuccessfulCallback(SuccessfulProcessingDetails(
          callbackUrl     = callbackUrl,
          reference       = FileReference("ID2"),
          downloadUrl     = downloadUrl,
          size            = 10L,
          fileName        = "test.pdf",
          fileMimeType    = "application/pdf",
          uploadTimestamp = startTime,
          checksum        = "1a2b3c4d5e",
          requestContext  = sampleRequestContext
        ))
      )
        .thenReturn(Future.failed(Exception("Planned exception")))

      when(
        notificationService.notifySuccessfulCallback(SuccessfulProcessingDetails(
          callbackUrl     = callbackUrl,
          reference       = FileReference("ID3"),
          downloadUrl     = downloadUrl,
          size            = 10L,
          fileName        = "test.pdf",
          fileMimeType    = "application/pdf",
          uploadTimestamp = startTime,
          checksum        = "1a2b3c4d5e",
          requestContext  = sampleRequestContext
        ))
      )
        .thenReturn(Future.successful(Nil))

      val metrics = metricsStub()

      val auditingService = mock[UpscanAuditingService]

      val queueOrchestrator = NotifyOnSuccessfulFileUploadMessageProcessingJob(
        queueConsumer,
        messageParser,
        fileDetailsRetriever,
        notificationService,
        metrics,
        clock,
        auditingService,
        serviceConfiguration
      )

      When("the orchestrator is called")
      Await.result(queueOrchestrator.run(), 30.seconds)

      Then("the queue consumer should poll for messages")
      verify(queueConsumer).poll()

      And("notification service is called for all valid messages")
      verify(notificationService, times(3)).notifySuccessfulCallback(any[SuccessfulProcessingDetails])

      And("auditing service is called for all valid messages")
      verify(auditingService, times(3)).notifyFileUploadedSuccessfully(any[SuccessfulProcessingDetails])

      And("successfully processed messages are confirmed")
      verify(queueConsumer).confirm(validMessage1)
      verify(queueConsumer).confirm(validMessage3)

      And("invalid messages are not confirmed")
      verifyNoMoreInteractions(queueConsumer)

      And("counter of successful processed messages is incremented by count of successfully processed messages")
      metrics.defaultRegistry.counter("successfulUploadNotificationSent").getCount shouldBe 2
      metrics.defaultRegistry.histogram("fileSize").getSnapshot.getValues          shouldBe Array(10L, 10L)
