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

import model.{Message, MessageProcessedSuccessfully, MessageProcessingFailed}
import play.api.Logger
import service.MessageProcessingService

class QueueOrchestrator(consumer: QueueConsumer, processor: MessageProcessingService) {
  def handleQueue() = {
    val messages: List[Message] = consumer.poll()
    messages.foreach { message =>
      processor.process(message) match {
        case MessageProcessedSuccessfully => consumer.confirm(message)
        case MessageProcessingFailed(error) =>
          Logger.warn(s"Failed to process message in queue. Message body: ${message.body}, error: $error")
      }
    }
  }
}
