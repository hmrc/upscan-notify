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

import connectors.aws.{QuarantineSqsQueueConsumer, SuccessfulSqsQueueConsumer}
import model._
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

trait NotifyOnFileProcessingEventFlow[T] extends PollingJob {
  val consumer: QueueConsumer
  val parser: MessageParser
  val retrieveEvent: (S3ObjectLocation) => Future[T]
  val notifyCallback: (T) => Future[Unit]
  implicit val ec: ExecutionContext

  def run(): Future[Unit] = {
    val outcomes = for {
      messages        <- consumer.poll()
      messageOutcomes <- Future.sequence { messages.map(processMessage) }
    } yield messageOutcomes

    outcomes.map(_ => ())
  }

  private def processMessage(message: Message): Future[Unit] = {
    val outcome = for {
      parsedMessage <- parser.parse(message)
      notification  <- retrieveEvent.apply(parsedMessage.location)
      _             <- notifyCallback.apply(notification)
    } yield ()

    outcome.onComplete {
      case Success(_) => consumer.confirm(message)
      case Failure(error) =>
        Logger.warn(s"Failed to process message '${message.id}', cause ${error.getMessage}", error)
    }

    outcome.recover { case _ => () }
  }
}

class NotifyOnSuccessfulFileUploadProcessingFlow @Inject()(
  val consumer: SuccessfulSqsQueueConsumer,
  val parser: MessageParser,
  fileRetriever: FileNotificationDetailsRetriever,
  notificationService: NotificationService
)(implicit val ec: ExecutionContext)
    extends NotifyOnFileProcessingEventFlow[UploadedFile] {

  override val retrieveEvent: (S3ObjectLocation) => Future[UploadedFile] = fileRetriever.retrieveUploadedFileDetails

  override val notifyCallback: (UploadedFile) => Future[Unit] = notificationService.notifySuccessfulCallback
}

class NotifyOnQuarantineFileUploadProcessingFlow @Inject()(
  val consumer: QuarantineSqsQueueConsumer,
  val parser: MessageParser,
  fileRetriever: FileNotificationDetailsRetriever,
  notificationService: NotificationService
)(implicit val ec: ExecutionContext)
    extends NotifyOnFileProcessingEventFlow[QuarantinedFile] {

  override val retrieveEvent: (S3ObjectLocation) => Future[QuarantinedFile] =
    fileRetriever.retrieveQuarantinedFileDetails

  override val notifyCallback: (QuarantinedFile) => Future[Unit] = notificationService.notifyFailedCallback
}
