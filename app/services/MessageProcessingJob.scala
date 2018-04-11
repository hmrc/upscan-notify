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

import javax.inject.Inject
import model._
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

trait MessageProcessingJob extends PollingJob {

  implicit def executionContext: ExecutionContext

  def consumer: QueueConsumer

  def processMessage(message: Message): Future[Unit]

  def run(): Future[Unit] = {
    val outcomes = for {
      messages        <- consumer.poll()
      messageOutcomes <- Future.sequence { messages.map(handleMessage) }
    } yield messageOutcomes

    outcomes.map(_ => ())
  }

  private def handleMessage(message: Message): Future[Unit] = {
    val outcome = for {
      _ <- processMessage(message)
      _ <- consumer.confirm(message)
    } yield ()

    outcome.recover {
      case error =>
        Logger.warn(s"Failed to process message '${message.id}', cause ${error.getMessage}", error)
    }
  }

}

trait SuccessfulQueueConsumer extends QueueConsumer
trait QuarantineQueueConsumer extends QueueConsumer

class NotifyOnSuccessfulFileUploadMessageProcessingJob @Inject()(
  val consumer: SuccessfulQueueConsumer,
  parser: MessageParser,
  fileRetriever: FileNotificationDetailsRetriever,
  notificationService: NotificationService
)(implicit val executionContext: ExecutionContext)
    extends MessageProcessingJob {

  override def processMessage(message: Message): Future[Unit] =
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

  override def processMessage(message: Message): Future[Unit] =
    for {
      parsedMessage <- parser.parse(message)
      notification  <- fileRetriever.retrieveQuarantinedFileDetails(parsedMessage.location)
      _             <- notificationService.notifyFailedCallback(notification)
    } yield ()
}
