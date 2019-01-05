/*
 * Copyright 2019 HM Revenue & Customs
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

package connectors.aws

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

import java.time.{Clock, Instant, ZoneId}
import java.util
import java.util.{List => JList}

import cats.effect
import cats.effect.{ContextShift, IO}
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.{Message => SqsMessage, _}
import model.Message
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Assertions, GivenWhenThen, Matchers}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.TimeUnit
import scala.util.Try

class SqsQueueConsumerSpec extends UnitSpec with Matchers with Assertions with GivenWhenThen with MockitoSugar {

  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  private def sqsMessages(messageCount: Int): JList[SqsMessage] = {
    val messages: JList[SqsMessage] = new util.ArrayList[SqsMessage]()

    (1 to messageCount).foreach { index =>
      val message = new SqsMessage()
      message.setBody(s"SQS message body: $index")
      message.setReceiptHandle(s"SQS receipt handle: $index")
      message.setMessageId(s"ID$index")
      messages.add(message)
    }

    messages
  }

  private val receivedAt = Instant.parse("2018-11-30T16:29:15Z")

  implicit val ioClock: effect.Clock[IO] = new effect.Clock[IO] {
    override def realTime(unit: TimeUnit): IO[Long] = IO.pure(receivedAt.toEpochMilli)

    override def monotonic(unit: TimeUnit): IO[Long] = IO.pure(receivedAt.toEpochMilli)
  }

  "SqsQueueConsumer" should {
    "call an SQS endpoint to receive messages" in {
      Given("an SQS queue consumer and a queue containing messages")
      val messageResult: ReceiveMessageResult = mock[ReceiveMessageResult]
      Mockito.when(messageResult.getMessages).thenReturn(sqsMessages(2))

      val sqsClient: AmazonSQS = mock[AmazonSQS]
      Mockito.when(sqsClient.receiveMessage(any(): ReceiveMessageRequest)).thenReturn(messageResult)
      val consumer = new SqsQueueConsumer(sqsClient, "Test.aws.sqs.queue") {}

      When("the consumer poll method is called")
      val messages: Seq[Message] = consumer.poll().unsafeRunSync()

      Then("the SQS endpoint should be called")
      Mockito.verify(sqsClient).receiveMessage(any(): ReceiveMessageRequest)

      And("the list of messages should be returned")
      messages shouldBe List(
        Message("ID1", "SQS message body: 1", "SQS receipt handle: 1", receivedAt),
        Message("ID2", "SQS message body: 2", "SQS receipt handle: 2", receivedAt))
    }

    "call an SQS endpoint to receive messages for empty queue" in {
      Given("an SQS queue consumer and a queue containing NO messages")
      val messageResult: ReceiveMessageResult = mock[ReceiveMessageResult]
      Mockito.when(messageResult.getMessages).thenReturn(sqsMessages(0))

      val sqsClient: AmazonSQS = mock[AmazonSQS]
      Mockito.when(sqsClient.receiveMessage(any(): ReceiveMessageRequest)).thenReturn(messageResult)
      val consumer = new SqsQueueConsumer(sqsClient, "Test.aws.sqs.queue") {}

      When("the consumer poll method is called")
      val messages: Seq[Message] = consumer.poll().unsafeRunSync()

      Then("the SQS endpoint should be called")
      Mockito.verify(sqsClient).receiveMessage(any(): ReceiveMessageRequest)

      And("an empty list should be returned")
      messages shouldBe Nil
    }

    "handle failing SQS receive messages calls" in {
      Given("a message containing a receipt handle")
      val sqsClient: AmazonSQS = mock[AmazonSQS]
      Mockito
        .when(sqsClient.receiveMessage(any(): ReceiveMessageRequest))
        .thenThrow(new OverLimitException(""))

      val consumer = new SqsQueueConsumer(sqsClient, "Test.aws.sqs.queue") {}

      When("the consumer confirm method is called")
      val result = Try {
        consumer.poll().unsafeRunSync()
      }

      Then("the SQS endpoint should be called")
      Mockito.verify(sqsClient).receiveMessage(any(): ReceiveMessageRequest)

      And("SQS error should be returned")
      result.failed.get shouldBe a[OverLimitException]
    }

    "call an SQS endpoint to delete a message" in {
      Given("a message containing a receipt handle")
      val messageResult: ReceiveMessageResult = mock[ReceiveMessageResult]
      Mockito.when(messageResult.getMessages).thenReturn(sqsMessages(1))

      val sqsClient: AmazonSQS = mock[AmazonSQS]
      Mockito.when(sqsClient.receiveMessage(any(): ReceiveMessageRequest)).thenReturn(messageResult)
      val consumer = new SqsQueueConsumer(sqsClient, "Test.aws.sqs.queue") {}

      val message: Message = consumer.poll().unsafeRunSync().head

      When("the consumer confirm method is called")
      val result = consumer.confirm(message).unsafeRunSync()

      Then("the SQS endpoint should be called")
      Mockito.verify(sqsClient).deleteMessage(any())

      And("unit should be returned")
      result shouldBe ((): Unit)
    }

    "handle failing SQS delete calls" in {
      Given("a message containing a receipt handle")
      val messageResult: ReceiveMessageResult = mock[ReceiveMessageResult]
      Mockito.when(messageResult.getMessages).thenReturn(sqsMessages(1))

      val sqsClient: AmazonSQS = mock[AmazonSQS]
      Mockito.when(sqsClient.receiveMessage(any(): ReceiveMessageRequest)).thenReturn(messageResult)
      val consumer = new SqsQueueConsumer(sqsClient, "Test.aws.sqs.queue") {}

      And("an SQS endpoint which is throwing an error")
      Mockito
        .when(sqsClient.deleteMessage(any()))
        .thenThrow(new ReceiptHandleIsInvalidException(""))

      val message: Message = consumer.poll().unsafeRunSync().head

      When("the consumer confirm method is called")
      val result = Try {
        consumer.confirm(message).unsafeRunSync()
      }

      Then("the SQS endpoint should be called")
      Mockito.verify(sqsClient).deleteMessage(any())

      And("SQS error should be returned")
      result.failed.get shouldBe a[ReceiptHandleIsInvalidException]
    }
  }
}
