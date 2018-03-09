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

package services

import java.net.URL

import model._
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{GivenWhenThen, Matchers}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class NotifyOnSuccessfulUploadProcessingFlowSpec extends UnitSpec with Matchers with GivenWhenThen with MockitoSugar {

  val parser = new MessageParser {
    override def parse(message: Message) = message.body match {
      case "VALID-BODY" => Future.successful(FileUploadEvent(S3ObjectLocation("bucket", message.id)))
      case _            => Future.failed(new Exception("Invalid body"))
    }
  }

  val callbackUrl = new URL("http://localhost:8080")
  val downloadUrl = new URL("http://remotehost/bucket/123")

  val fileDetailsRetriever = new FileNotificationDetailsRetriever {
    override def retrieveUploadedFileDetails(objectLocation: S3ObjectLocation): Future[UploadedFile] =
      Future.successful(UploadedFile(callbackUrl, objectLocation.objectKey, downloadUrl))
  }

  "SuccessfulUploadNotificationProcessingFlow" should {
    "get messages from the queue consumer, and call notification service for valid messages" in {
      Given("there are only valid messages in a message queue")
      val validMessage = Message("ID", "VALID-BODY", "RECEIPT-1")

      val queueConsumer = mock[QueueConsumer]
      Mockito.when(queueConsumer.poll()).thenReturn(List(validMessage))
      Mockito.when(queueConsumer.confirm(any())).thenReturn(Future.successful(()))

      val notificationService = mock[NotificationService]
      Mockito.when(notificationService.notifyCallback(any())).thenReturn(Future.successful(()))

      val queueOrchestrator =
        new NotifyOnSuccessfulUploadProcessingFlow(queueConsumer, parser, fileDetailsRetriever, notificationService)

      When("the orchestrator is called")
      Await.result(queueOrchestrator.run(), 30 seconds)

      Then("the queue consumer should poll for messages")
      Mockito.verify(queueConsumer).poll()

      And("callback recipient is notified")
      Mockito.verify(notificationService).notifyCallback(any())

      And("successfully processed messages are confirmed")
      Mockito.verify(queueConsumer).confirm(validMessage)
    }

    "get messages from the queue consumer, and call notification service for valid messages and ignore invalid messages" in {
      Given("there are only valid messages in a message queue")
      val validMessage1  = Message("ID1", "VALID-BODY", "RECEIPT-1")
      val invalidMessage = Message("ID2", "INVALID-BODY", "RECEIPT-2")
      val validMessage2  = Message("ID3", "VALID-BODY", "RECEIPT-3")

      val queueConsumer = mock[QueueConsumer]
      Mockito.when(queueConsumer.poll()).thenReturn(List(validMessage1, invalidMessage, validMessage2))
      Mockito
        .when(queueConsumer.confirm(any()))
        .thenReturn(Future.successful(()))
        .thenReturn(Future.successful(()))

      val notificationService = mock[NotificationService]
      Mockito
        .when(notificationService.notifyCallback(any()))
        .thenReturn(Future.successful(()))
        .thenReturn(Future.successful(()))

      val queueOrchestrator =
        new NotifyOnSuccessfulUploadProcessingFlow(queueConsumer, parser, fileDetailsRetriever, notificationService)

      When("the orchestrator is called")
      Await.result(queueOrchestrator.run(), 30 seconds)

      Then("the queue consumer should poll for messages")
      Mockito.verify(queueConsumer).poll()

      And("notification service is called only for valid messages")
      Mockito.verify(notificationService, Mockito.times(2)).notifyCallback(any())

      And("successfully processed messages are confirmed")
      Mockito.verify(queueConsumer).confirm(validMessage1)
      Mockito.verify(queueConsumer).confirm(validMessage2)

      And("invalid messages are not confirmed")
      Mockito.verifyNoMoreInteractions(queueConsumer)
    }

    "do not confirm valid messages for which notification has failed" in {

      Given("there are only valid messages in a message queue")
      val validMessage1 = Message("ID1", "VALID-BODY", "RECEIPT-1")
      val validMessage2 = Message("ID2", "VALID-BODY", "RECEIPT-2")
      val validMessage3 = Message("ID3", "VALID-BODY", "RECEIPT-3")

      val queueConsumer = mock[QueueConsumer]
      Mockito.when(queueConsumer.poll()).thenReturn(List(validMessage1, validMessage2, validMessage3))
      Mockito
        .when(queueConsumer.confirm(any()))
        .thenReturn(Future.successful(()))
        .thenReturn(Future.successful(()))

      val notificationService = mock[NotificationService]
      Mockito
        .when(notificationService.notifyCallback(UploadedFile(callbackUrl, "ID1", downloadUrl)))
        .thenReturn(Future.successful(()))

      Mockito
        .when(notificationService.notifyCallback(UploadedFile(callbackUrl, "ID2", downloadUrl)))
        .thenReturn(Future.failed(new Exception("Planned exception")))

      Mockito
        .when(notificationService.notifyCallback(UploadedFile(callbackUrl, "ID3", downloadUrl)))
        .thenReturn(Future.successful(()))

      val queueOrchestrator =
        new NotifyOnSuccessfulUploadProcessingFlow(queueConsumer, parser, fileDetailsRetriever, notificationService)

      When("the orchestrator is called")
      Await.result(queueOrchestrator.run(), 30 seconds)

      Then("the queue consumer should poll for messages")
      Mockito.verify(queueConsumer).poll()

      And("notification service is called for all valid messages")
      Mockito.verify(notificationService, Mockito.times(3)).notifyCallback(any())

      And("successfully processed messages are confirmed")
      Mockito.verify(queueConsumer).confirm(validMessage1)
      Mockito.verify(queueConsumer).confirm(validMessage3)

      And("invalid messages are not confirmed")
      Mockito.verifyNoMoreInteractions(queueConsumer)

    }
  }

}
