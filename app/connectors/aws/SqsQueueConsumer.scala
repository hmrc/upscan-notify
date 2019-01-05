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

import java.time.Instant
import java.util.concurrent.Executors

import cats.effect.{Clock, ContextShift, IO}
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.{DeleteMessageRequest, ReceiveMessageRequest}
import model.Message
import play.api.Logger
import services.QueueConsumer

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.MILLISECONDS

abstract class SqsQueueConsumer(val sqsClient: AmazonSQS, queueUrl: String)(
  implicit clock: Clock[IO],
  contextShift: ContextShift[IO])
    extends QueueConsumer[IO] {

  private val sqsExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(8))

  override def poll(): IO[Seq[Message]] = {
    val receiveMessageRequest = new ReceiveMessageRequest(queueUrl)
      .withWaitTimeSeconds(20)

    for {
      result <- contextShift.evalOn(sqsExecutionContext) {
                 IO.delay {
                   sqsClient.receiveMessage(receiveMessageRequest)
                 }
               }
      receivedAt <- clock.monotonic(MILLISECONDS)
      receivedAtInstant = Instant.ofEpochMilli(receivedAt)
    } yield {
      result.getMessages.asScala.map { sqsMessage =>
        Logger.debug(
          s"Received message with id: [${sqsMessage.getMessageId}] and receiptHandle: [${sqsMessage.getReceiptHandle}].")
        Message(sqsMessage.getMessageId, sqsMessage.getBody, sqsMessage.getReceiptHandle, receivedAtInstant)
      }
    }
  }

  override def confirm(message: Message): IO[Unit] = {
    val deleteMessageRequest = new DeleteMessageRequest(queueUrl, message.receiptHandle)
    contextShift.evalOn(sqsExecutionContext) {
      IO.delay {
        sqsClient.deleteMessage(deleteMessageRequest)
        Logger.debug(s"Deleted message from Queue: [$queueUrl], for receiptHandle: [${message.receiptHandle}].")
      }
    }
  }
}
