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

import model._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class MessageProcessingService(parser: MessageParser, notificationService: NotificationService)(
  implicit ec: ExecutionContext) {

  def process(message: Message): Future[MessageProcessingResult] = {

    val parsingResult = parser.parse(message)

    parsingResult match {
      case Success(notification) =>
        notificationService
          .notifyCallback(notification)
          .map(_ => MessageProcessedSuccessfully)
          .recover { case e: Throwable => MessageProcessingFailed(e.getMessage) }
      case Failure(error) =>
        Future.successful(MessageProcessingFailed(s"Parsing failed, reason: ${error.getMessage}"))
    }

  }
}
