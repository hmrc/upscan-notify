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

import model.Message
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

class SuccessfulUploadNotificationProcessingFlow @Inject()(
  consumer: QueueConsumer,
  parser: MessageParser,
  fileDetailsRetriever: FileNotificationDetailsRetriever,
  notificationService: NotificationService)(implicit ec: ExecutionContext)
    extends PollingJob {
  def run(): Future[Unit] = {
    val outcomes = for {
      messages        <- consumer.poll()
      messageOutcomes <- Future.sequence { messages.map(processMessage) }
    } yield messageOutcomes

    outcomes.map(_ => ())
  }

  def processMessage(message: Message): Future[Unit] = {
    val outcome =
      for {
        parsedMessage <- parser.parse(message)
        notification  <- fileDetailsRetriever.lookupDetails(parsedMessage.location)
        _             <- notificationService.notifyCallback(notification)
        _             <- consumer.confirm(message)
      } yield ()

    outcome.onFailure {
      case error: Exception => Logger.warn(s"Failed to process message '${message.id}'. Cause: $error")
    }

    outcome.recover { case _ => () }

  }

}
