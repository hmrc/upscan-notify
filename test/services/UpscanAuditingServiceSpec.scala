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

import java.net.URL
import java.time.Instant

import model._
import org.mockito.{ArgumentCaptor, ArgumentMatchers, Mockito}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{GivenWhenThen, Matchers}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.{RequestId, SessionId}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class UpscanAuditingServiceSpec extends UnitSpec with Matchers with GivenWhenThen with MockitoSugar {

  "UpscanAuditingService" should {

    "properly handle FileUploadedSuccessfully events" in {

      val auditConnector = mock[AuditConnector]

      val upscanAuditingService = new UpscanAuditingService(auditConnector)

      val event: UploadedFile = UploadedFile(
        new URL("http://www.test.com"),
        FileReference("REF"),
        new URL("http://www.test.com"),
        10L,
        UploadDetails("test.pdf", "application/pdf", Instant.now(), "1a2b3c4d5e"),
        RequestContext(Some("RequestId"), Some("SessionId"))
      )

      upscanAuditingService.notifyFileUploadedSuccessfully(event)

      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      val hcCaptor    = ArgumentCaptor.forClass(classOf[HeaderCarrier])
      Mockito.verify(auditConnector).sendEvent(eventCaptor.capture())(hcCaptor.capture(), ArgumentMatchers.any())

      val dataEvent: DataEvent = eventCaptor.getValue

      dataEvent.auditSource                 shouldBe "upscan"
      dataEvent.auditType                   shouldBe "cleanFileUploaded"
      dataEvent.detail.get("fileReference") shouldBe Some("REF")
      dataEvent.detail.get("fileSize")      shouldBe Some("10")

      val headerCarrier: HeaderCarrier = hcCaptor.getValue

      headerCarrier.requestId shouldBe Some(RequestId("RequestId"))
      headerCarrier.sessionId shouldBe Some(SessionId("SessionId"))
    }

    "properly handle FileIsQuarantined events" in {
      val auditConnector = mock[AuditConnector]

      val upscanAuditingService = new UpscanAuditingService(auditConnector)

      val event: QuarantinedFile = QuarantinedFile(
        new URL("http://www.test.com"),
        FileReference("REF"),
        ErrorDetails("QUARANTINE", "1a2b3c4d5e"),
        RequestContext(Some("RequestId"), Some("SessionId"))
      )

      upscanAuditingService.notifyFileIsQuarantined(event)

      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      val hcCaptor    = ArgumentCaptor.forClass(classOf[HeaderCarrier])
      Mockito.verify(auditConnector).sendEvent(eventCaptor.capture())(hcCaptor.capture(), ArgumentMatchers.any())

      val dataEvent: DataEvent = eventCaptor.getValue

      dataEvent.auditSource                 shouldBe "upscan"
      dataEvent.auditType                   shouldBe "invalidFileUploaded"
      dataEvent.detail.get("fileReference") shouldBe Some("REF")
      dataEvent.detail.get("failureReason") shouldBe Some("QUARANTINE")

      val headerCarrier: HeaderCarrier = hcCaptor.getValue

      headerCarrier.requestId shouldBe Some(RequestId("RequestId"))
      headerCarrier.sessionId shouldBe Some(SessionId("SessionId"))
    }

  }
}
