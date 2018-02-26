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

import model.{Message, MessageProcessedSuccessfully, MessageProcessingFailed, MessageProcessingResult}
import org.mockito.Mockito
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{GivenWhenThen, Matchers}
import service.{MessageParser, MessageProcessingService, NotificationService}
import uk.gov.hmrc.play.test.UnitSpec

import scala.util.{Failure, Success}

class MessageProcessingServiceSpec extends UnitSpec with Matchers with GivenWhenThen with MockitoSugar {

  val parser = new MessageParser {
    override def parse(message: Message) = message.body match {
      case "VALID-BODY" => Success(())
      case _            => Failure(new Exception("Parsing failed"))
    }
  }

  "Message processing service" should {
    "successfully process valid message if notification service succeeds" in {

      val notificationService = mock[NotificationService]

      val messageProcessingService = new MessageProcessingService(parser, notificationService)

      Given("there is a message with valid body")
      val message = model.Message("VALID-BODY")

      And("notification service can successfuly processs this message")
      Mockito.when(notificationService.notifyCallback()).thenReturn(Success())

      When("message processing service is called")
      val result: MessageProcessingResult = messageProcessingService.process(message)

      Then("notification service is called")
      Mockito.verify(notificationService).notifyCallback()

      And("successful result is returned")
      result shouldBe MessageProcessedSuccessfully

    }

    "fail if the message has invalid content and there should be no notification" in {

      val notificationService = mock[NotificationService]

      val messageProcessingService = new MessageProcessingService(parser, notificationService)

      Given(" there is a message with invalid body")
      val message = model.Message("INVALID-BODY")

      When("message processing service is called")
      val result: MessageProcessingResult = messageProcessingService.process(message)

      Then("notification service is not called")
      Mockito.verifyZeroInteractions(notificationService)

      And("failure result is returned")
      result shouldBe MessageProcessingFailed("Parsing failed")
    }

    "fail when processing valid message if notification service fails" in {

      val notificationService = mock[NotificationService]

      val messageProcessingService = new MessageProcessingService(parser, notificationService)

      Given("there is a message with valid body")
      val message = model.Message("VALID-BODY")

      And("notification service fails when processing this message")
      Mockito.when(notificationService.notifyCallback()).thenReturn(Failure(new Exception("Notification failed")))

      When("message processing service is called")
      val result: MessageProcessingResult = messageProcessingService.process(message)

      Then("notification service is called")
      Mockito.verify(notificationService).notifyCallback()

      And("failure result is returned")
      result shouldBe MessageProcessingFailed("Notification failed")
    }

  }

}
