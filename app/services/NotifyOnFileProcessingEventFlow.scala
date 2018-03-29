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

import javax.inject.{Inject, Named}

import model.Message
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

trait NotifyOnFileProcessingEventFlow extends PollingJob {
  val consumer: QueueConsumer
  val parser: MessageParser
  val fileDetailsRetriever: FileNotificationDetailsRetriever
  val notificationService: NotificationService
  implicit val ec: ExecutionContext

  def run(): Future[Unit] = {
    val outcomes = for {
      messages        <- consumer.poll()
      messageOutcomes <- Future.sequence { messages.map(processMessage) }
    } yield messageOutcomes

    outcomes.map(_ => ())
  }

  private def processMessage(message: Message): Future[Unit] = {
    val outcome = handleMessage(message)

    outcome.onFailure {
      case error: Exception =>
        Logger.warn(s"Failed to process message '${message.id}', cause ${error.getMessage}", error)
    }

    outcome.recover { case _ => () }
  }

  protected def handleMessage(message: Message): Future[Unit]
}

class NotifyOnSuccessfulUploadProcessingFlow @Inject()(
  @Named("successful") val consumer: QueueConsumer,
  val parser: MessageParser,
  val fileDetailsRetriever: FileNotificationDetailsRetriever,
  val notificationService: NotificationService)(implicit val ec: ExecutionContext)
    extends NotifyOnFileProcessingEventFlow {

  protected def handleMessage(message: Message) =
    for {
      parsedMessage <- parser.parse(message)
      notification  <- fileDetailsRetriever.retrieveUploadedFileDetails(parsedMessage.location)
      _             <- notificationService.notifySuccessfulCallback(notification)
      _             <- consumer.confirm(message)
    } yield ()
}

class NotifyOnQuarantineUploadProcessingFlow @Inject()(
  @Named("quarantine") val consumer: QueueConsumer,
  val parser: MessageParser,
  val fileDetailsRetriever: FileNotificationDetailsRetriever,
  val notificationService: NotificationService)(implicit val ec: ExecutionContext)
    extends NotifyOnFileProcessingEventFlow {

  override protected def handleMessage(message: Message): Future[Unit] =
    for {
      parsedMessage <- parser.parse(message)
      notification  <- fileDetailsRetriever.retrieveQuarantinedFileDetails(parsedMessage.location)
      _             <- notificationService.notifyFailedCallback(notification)
      _             <- consumer.confirm(message)
    } yield ()
}
