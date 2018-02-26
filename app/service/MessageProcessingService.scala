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

import model.{Message, MessageProcessedSuccessfully, MessageProcessingFailed, MessageProcessingResult}

import scala.util.{Failure, Success, Try}

class MessageProcessingService(parser: MessageParser, notificationService: NotificationService) {

  def process(message: Message): MessageProcessingResult = {

    val parsingResult: Try[Any] = parser.parse(message)

    parsingResult match {
      case Success(_) =>
        notificationService.notifyCallback() match {
          case Success(_)         => MessageProcessedSuccessfully
          case Failure(exception) => MessageProcessingFailed(exception.getMessage)
        }

      case Failure(_) => MessageProcessingFailed("Parsing failed")
    }

  }
}
