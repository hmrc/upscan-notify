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
import org.mockito.Mockito
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{GivenWhenThen, Matchers}
import service.MessageProcessingService
import uk.gov.hmrc.play.test.UnitSpec
import org.mockito.ArgumentMatchers.any

class QueueOrchestratorSpec extends UnitSpec with Matchers with GivenWhenThen with MockitoSugar {

  "QueueOrchestrator" should {
    "get messages from the queue consumer, and call notification service for valid messages" in {
      Given("there are only valid messages in a message queue")
      val validMessage = new Message("VALID-BODY")

      val queueConsumer = mock[QueueConsumer]
      Mockito.when(queueConsumer.poll()).thenReturn(List(validMessage))

      val processor = mock[MessageProcessingService]
      Mockito.when(processor.process(validMessage)).thenReturn(MessageProcessedSuccessfully)

      val queueOrchestrator = new QueueOrchestrator(queueConsumer, processor)

      When("the orchestrator is called")
      queueOrchestrator.handleQueue()

      Then("the queue consumer should poll for messages")
      Mockito.verify(queueConsumer).poll()

      And("the messages are sent to the processing service")
      Mockito.verify(processor).process(any())

      And("successfully processed messages are confirmed")
      Mockito.verify(queueConsumer).confirm(validMessage)
    }

    "get messages from the queue consumer, and call notification service for valid messages and ignore invalid messages" in {
      Given("there are only valid messages in a message queue")
      val validMessage   = Message("VALID-BODY")
      val invalidMessage = Message("INVALID-BODY")

      val queueConsumer = mock[QueueConsumer]
      Mockito.when(queueConsumer.poll()).thenReturn(List(validMessage, invalidMessage))

      val processor = mock[MessageProcessingService]
      Mockito.when(processor.process(validMessage)).thenReturn(MessageProcessedSuccessfully)
      Mockito.when(processor.process(invalidMessage)).thenReturn(MessageProcessingFailed("This is an invalid message"))

      val queueOrchestrator = new QueueOrchestrator(queueConsumer, processor)

      When("the orchestrator is called")
      queueOrchestrator.handleQueue()

      Then("the queue consumer should poll for messages")
      Mockito.verify(queueConsumer).poll()

      And("the each message is sent to the processing service")
      Mockito.verify(processor, Mockito.times(2)).process(any())

      And("successfully processed messages are confirmed")
      Mockito.verify(queueConsumer).confirm(validMessage)

      And("invalid messages are not confirmed")
      Mockito.verifyNoMoreInteractions(queueConsumer)
    }
  }

}
