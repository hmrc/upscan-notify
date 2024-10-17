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

package uk.gov.hmrc.upscannotify.connector.aws

import org.scalatest.{Assertions, GivenWhenThen}
import org.scalatest.concurrent.ScalaFutures
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{verify, when}
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{DeleteMessageRequest, DeleteMessageResponse, Message => SqsMessage, OverLimitException, ReceiptHandleIsInvalidException, ReceiveMessageRequest, ReceiveMessageResponse}
import uk.gov.hmrc.upscannotify.model.Message
import uk.gov.hmrc.upscannotify.test.UnitSpec

import java.time.{Clock, Instant, ZoneId}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._

class SqsQueueConsumerSpec
  extends UnitSpec
     with Assertions
     with ScalaFutures
     with GivenWhenThen:

  private def sqsMessages(messageCount: Int): java.util.List[SqsMessage] =
    (1 to messageCount)
      .map: index =>
        SqsMessage.builder()
          .body(s"SQS message body: $index")
          .receiptHandle(s"SQS receipt handle: $index")
          .messageId(s"ID$index")
          .build()
      .asJava

  private val receivedAt = Instant.parse("2018-11-30T16:29:15Z")
  private val batchSize  = 10
  private val waitTime   = 20.seconds
  private val clock      = Clock.fixed(receivedAt, ZoneId.systemDefault())

  "SqsQueueConsumer" should:
    "call an SQS endpoint to receive messages" in:
      Given("an SQS queue consumer and a queue containing messages")
      val sqsClient: SqsAsyncClient = mock[SqsAsyncClient]
      when(sqsClient.receiveMessage(any[ReceiveMessageRequest]))
        .thenReturn:
          Future
            .successful:
              ReceiveMessageResponse.builder().messages(sqsMessages(2)).build()
            .asJava

      val consumer = new SqsQueueConsumer(sqsClient, "Test.aws.sqs.queue", batchSize, waitTime, clock) {}

      When("the consumer poll method is called")
      val messages: Seq[Message] = consumer.poll().futureValue

      Then("the SQS endpoint should be called")
      verify(sqsClient).receiveMessage(any[ReceiveMessageRequest])

      And("the list of messages should be returned")
      messages shouldBe List(
        Message("ID1", "SQS message body: 1", "SQS receipt handle: 1", receivedAt),
        Message("ID2", "SQS message body: 2", "SQS receipt handle: 2", receivedAt)
      )

    "call an SQS endpoint to receive messages for empty queue" in:
      Given("an SQS queue consumer and a queue containing NO messages")
      val sqsClient: SqsAsyncClient = mock[SqsAsyncClient]
      when(sqsClient.receiveMessage(any[ReceiveMessageRequest]))
        .thenReturn:
          Future
            .successful:
              ReceiveMessageResponse.builder().messages(sqsMessages(0)).build()
            .asJava

      val consumer = new SqsQueueConsumer(sqsClient, "Test.aws.sqs.queue", batchSize, waitTime, clock) {}

      When("the consumer poll method is called")
      val messages: Seq[Message] = consumer.poll().futureValue

      Then("the SQS endpoint should be called")
      verify(sqsClient).receiveMessage(any[ReceiveMessageRequest])

      And("an empty list should be returned")
      messages shouldBe Nil

    "handle failing SQS receive messages calls" in:
      Given("a message containing a receipt handle")
      val sqsClient: SqsAsyncClient = mock[SqsAsyncClient]
      when(sqsClient.receiveMessage(any[ReceiveMessageRequest]))
        .thenReturn:
          Future
            .failed(OverLimitException.builder().build())
            .asJava

      val consumer = new SqsQueueConsumer(sqsClient, "Test.aws.sqs.queue", batchSize, waitTime, clock) {}

      When("the consumer confirm method is called")
      val error = consumer.poll().failed.futureValue

      Then("SQS error should be wrapped in a future")
      error shouldBe a[OverLimitException]

      And("the SQS endpoint should be called")
      verify(sqsClient).receiveMessage(any[ReceiveMessageRequest])

    "call an SQS endpoint to delete a message" in:
      Given("a message containing a receipt handle")
      val sqsClient: SqsAsyncClient = mock[SqsAsyncClient]
      when(sqsClient.receiveMessage(any[ReceiveMessageRequest]))
        .thenReturn:
          Future
            .successful:
              ReceiveMessageResponse.builder().messages(sqsMessages(1)).build()
            .asJava

      when(sqsClient.deleteMessage(any[DeleteMessageRequest]))
        .thenReturn(Future.successful(DeleteMessageResponse.builder().build()).asJava)

      val consumer = new SqsQueueConsumer(sqsClient, "Test.aws.sqs.queue", batchSize, waitTime, clock) {}

      val message: Message = consumer.poll().futureValue.head

      When("the consumer confirm method is called")
      val result = consumer.confirm(message).futureValue

      Then("the SQS endpoint should be called")
      verify(sqsClient).deleteMessage(any[DeleteMessageRequest])

      And("unit should be returned")
      result shouldBe ((): Unit)

    "handle failing SQS delete calls" in:
      Given("a message containing a receipt handle")
      val sqsClient: SqsAsyncClient = mock[SqsAsyncClient]
      when(sqsClient.receiveMessage(any[ReceiveMessageRequest]))
        .thenReturn:
          Future
            .successful:
              ReceiveMessageResponse.builder().messages(sqsMessages(1)).build()
            .asJava

      val consumer = new SqsQueueConsumer(sqsClient, "Test.aws.sqs.queue", batchSize, waitTime, clock) {}

      And("an SQS endpoint which is throwing an error")
      when(sqsClient.deleteMessage(any[DeleteMessageRequest]))
        .thenReturn:
          Future
            .failed:
              ReceiptHandleIsInvalidException.builder().build()
            .asJava

      val message: Message = consumer.poll().futureValue.head

      When("the consumer confirm method is called")
      val error = consumer.confirm(message).failed.futureValue

      Then("SQS error should be wrapped in a future")
      error shouldBe a[ReceiptHandleIsInvalidException]

      And("the SQS endpoint should be called")
      verify(sqsClient).deleteMessage(any[DeleteMessageRequest])
