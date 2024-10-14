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

package connectors.aws

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.{DeleteMessageRequest, ReceiveMessageRequest, ReceiveMessageResult}
import model.Message
import play.api.Logging
import services.QueueConsumer

import java.time.Clock
import scala.jdk.CollectionConverters._
import scala.concurrent.{ExecutionContext, Future}

abstract class SqsQueueConsumer(
  val sqsClient      : AmazonSQS,
  queueUrl           : String,
  processingBatchSize: Int,
  clock              : Clock
)(implicit
  val ec: ExecutionContext
) extends QueueConsumer with Logging:

  override def poll(): Future[Seq[Message]] =
    val receiveMessageRequest = ReceiveMessageRequest(queueUrl)
      .withMaxNumberOfMessages(processingBatchSize)
      .withWaitTimeSeconds(20)

    val receiveMessageResult: Future[ReceiveMessageResult] =
      Future(sqsClient.receiveMessage(receiveMessageRequest))

    receiveMessageResult.map: result =>
      val receivedAt = clock.instant()

      result.getMessages.asScala
        .map: sqsMessage =>
          logger.debug(s"Received message with id: [${sqsMessage.getMessageId}] and receiptHandle: [${sqsMessage.getReceiptHandle}].")
          Message(sqsMessage.getMessageId, sqsMessage.getBody, sqsMessage.getReceiptHandle, receivedAt)
        .toSeq

  override def confirm(message: Message): Future[Unit] =
    val deleteMessageRequest = DeleteMessageRequest(queueUrl, message.receiptHandle)
    Future:
      sqsClient.deleteMessage(deleteMessageRequest)
      logger.debug(s"Deleted message from Queue: [$queueUrl], for receiptHandle: [${message.receiptHandle}].")
