/*
 * Copyright 2020 HM Revenue & Customs
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

package util.logging

import org.mockito.Mockito.when
import org.mockito.{ArgumentCaptor, Mockito}
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.LoggerLike

import scala.collection.JavaConverters._

class MockLoggerLike extends LoggerLike {
  private val warnCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])

  override val logger = mock[org.slf4j.Logger]
  when(logger.isWarnEnabled()).thenReturn(true)

  Mockito.doNothing().when(logger).warn(warnCaptor.capture())

  def getWarnMessage(): String = getMessage(warnCaptor)
  def getWarnMessages(): Seq[String] = getMessages(warnCaptor)

  private def getMessage(captor: ArgumentCaptor[String]): String = captor.getValue()
  private def getMessages(captor: ArgumentCaptor[String]): Seq[String] = captor.getAllValues().asScala
}