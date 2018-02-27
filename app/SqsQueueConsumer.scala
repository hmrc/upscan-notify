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

import javax.inject.Inject

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.{DeleteMessageRequest, ReceiveMessageRequest, ReceiveMessageResult}
import helpers.ServiceConfiguration
import model.Message

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

class SqsQueueConsumer @Inject()(sqsClient: AmazonSQS, configuration: ServiceConfiguration)(
  implicit ec: ExecutionContext)
    extends QueueConsumer {

  override def poll(): Future[List[Message]] = {
    val receiveMessageRequest = new ReceiveMessageRequest(configuration.outboundSuccessfulQueueUrl)
      .withWaitTimeSeconds(20)

    val receiveMessageResult: Future[ReceiveMessageResult] =
      Future(sqsClient.receiveMessage(receiveMessageRequest))

    receiveMessageResult map { result =>
      result.getMessages.asScala.toList.map(sqsMessage => Message(sqsMessage.getBody, sqsMessage.getReceiptHandle))
    }
  }

  override def confirm(message: Message): Future[Unit] = {
    val deleteMessageRequest = new DeleteMessageRequest(configuration.outboundSuccessfulQueueUrl, message.receiptHandle)
    Future(sqsClient.deleteMessage(deleteMessageRequest))
  }
}
