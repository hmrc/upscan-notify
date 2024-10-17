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

import play.api.Logging
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{DeleteMessageRequest, ReceiveMessageRequest}
import uk.gov.hmrc.upscannotify.model.Message
import uk.gov.hmrc.upscannotify.service.QueueConsumer

import java.time.Clock
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.Duration

abstract class SqsQueueConsumer(
  val sqsClient      : SqsAsyncClient,
  queueUrl           : String,
  processingBatchSize: Int,
  waitTime           : Duration,
  clock              : Clock
)(using
  val ec: ExecutionContext
) extends QueueConsumer with Logging:

  override def poll(): Future[Seq[Message]] =
    sqsClient
      .receiveMessage:
        ReceiveMessageRequest.builder()
          .queueUrl(queueUrl)
          .maxNumberOfMessages(processingBatchSize)
          .waitTimeSeconds(waitTime.toSeconds.toInt)
          .build
      .asScala
      .map: result =>
        val receivedAt = clock.instant()
        result.messages.asScala
          .map: sqsMessage =>
            logger.debug(s"Received message with id: [${sqsMessage.messageId}] and receiptHandle: [${sqsMessage.receiptHandle}].")
            Message(sqsMessage.messageId, sqsMessage.body, sqsMessage.receiptHandle, receivedAt)
          .toSeq

  override def confirm(message: Message): Future[Unit] =
    sqsClient
      .deleteMessage:
        DeleteMessageRequest.builder()
          .queueUrl(queueUrl)
          .receiptHandle(message.receiptHandle)
          .build()
      .asScala
      .map: _ =>
        logger.debug(s"Deleted message from Queue: [$queueUrl], for receiptHandle: [${message.receiptHandle}].")
