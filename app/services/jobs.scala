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

import javax.inject.{Inject, Provider}
import model.Message

import scala.concurrent.{ExecutionContext, Future}

trait SuccessfulQueueConsumer extends QueueConsumer
trait QuarantineQueueConsumer extends QueueConsumer

class SqsPollingJobsProvider @Inject()(
  successfulFileUploadProcessingJob: NotifyOnSuccessfulFileUploadMessageProcessingJob,
  quarantineFileUploadProcessingJob: NotifyOnQuarantineFileUploadMessageProcessingJob
) extends Provider[PollingJobs] {

  override def get(): PollingJobs =
    PollingJobs(List(successfulFileUploadProcessingJob, quarantineFileUploadProcessingJob))
}

class NotifyOnSuccessfulFileUploadMessageProcessingJob @Inject()(
  val consumer: SuccessfulQueueConsumer,
  parser: MessageParser,
  fileRetriever: FileNotificationDetailsRetriever,
  notificationService: NotificationService
)(implicit val executionContext: ExecutionContext)
    extends MessageProcessingJob {

  override def process(message: Message): Future[Unit] =
    for {
      parsedMessage <- parser.parse(message)
      notification  <- fileRetriever.retrieveUploadedFileDetails(parsedMessage.location)
      _             <- notificationService.notifySuccessfulCallback(notification)
    } yield ()

}

class NotifyOnQuarantineFileUploadMessageProcessingJob @Inject()(
  val consumer: QuarantineQueueConsumer,
  parser: MessageParser,
  fileRetriever: FileNotificationDetailsRetriever,
  notificationService: NotificationService
)(implicit val executionContext: ExecutionContext)
    extends MessageProcessingJob {

  override def process(message: Message): Future[Unit] =
    for {
      parsedMessage <- parser.parse(message)
      notification  <- fileRetriever.retrieveQuarantinedFileDetails(parsedMessage.location)
      _             <- notificationService.notifyFailedCallback(notification)
    } yield ()
}
