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

package service

import javax.inject.Inject

import model._
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

class MessageProcessingService @Inject()(
  parser: MessageParser,
  fileDetailsRetriever: FileNotificationDetailsRetriever,
  notificationService: NotificationService)(implicit ec: ExecutionContext) {

  def process(message: Message): Future[MessageProcessingResult] = {

    val parsingResult = parser.parse(message)

    parsingResult match {
      case FileUploadedEvent(bucket, objectKey) =>
        val outcome =
          for (notification <- fileDetailsRetriever.retrieveFileDetails(bucket, objectKey);
               _            <- notificationService.notifyCallback(notification))
            yield MessageProcessedSuccessfully

        outcome.recover { case e: Throwable => MessageProcessingFailed(e.getMessage) }
      case UnsupportedMessage(reason) =>
        Logger.warn(s"Retrieved unsupported message. Reason: $reason. Message body: ${message.body}")
        Future.successful(MessageProcessingFailed(reason))
    }

  }

}
