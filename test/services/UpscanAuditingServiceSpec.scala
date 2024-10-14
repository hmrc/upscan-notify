/*
 * Copyright 2023 HM Revenue & Customs
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
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.verify
import org.scalatest.GivenWhenThen
import test.UnitSpec
import uk.gov.hmrc.http.{HeaderCarrier, HeaderNames}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.DataEvent

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

class UpscanAuditingServiceSpec extends UnitSpec with GivenWhenThen {

  "UpscanAuditingService" should {

    "properly handle FileUploadedSuccessfully events" in {

      val auditConnector = mock[AuditConnector]

      val upscanAuditingService = new UpscanAuditingService(auditConnector)

      val event = SuccessfulProcessingDetails(
        callbackUrl     = new URL("http://www.test.com"),
        reference       = FileReference("REF"),
        downloadUrl     = new URL("http://www.test.com"),
        size            = 10L,
        fileName        = "test.pdf",
        fileMimeType    = "application/pdf",
        uploadTimestamp = Instant.now(),
        checksum        = "1a2b3c4d5e",
        requestContext  = RequestContext(Some("RequestId"), Some("SessionId"), "127.0.0.1")
      )

      upscanAuditingService.notifyFileUploadedSuccessfully(event)

      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(auditConnector).sendEvent(eventCaptor.capture())(any[HeaderCarrier], any[ExecutionContext])

      val dataEvent: DataEvent = eventCaptor.getValue

      dataEvent.auditSource                 shouldBe "upscan"
      dataEvent.auditType                   shouldBe "cleanFileUploaded"
      dataEvent.detail.get("fileReference") shouldBe Some("REF")
      dataEvent.detail.get("fileSize")      shouldBe Some("10")

      dataEvent.tags.get("transactionName")      shouldBe Some("clean-file-uploaded")
      dataEvent.tags.get(HeaderNames.xSessionId) shouldBe Some("SessionId")
      dataEvent.tags.get(HeaderNames.xRequestId) shouldBe Some("RequestId")
      dataEvent.tags.get("clientIp")             shouldBe Some("127.0.0.1")

    }

    "properly handle FileIsQuarantined events" in {
      val auditConnector = mock[AuditConnector]

      val upscanAuditingService = new UpscanAuditingService(auditConnector)

      val event = FailedProcessingDetails(
        callbackUrl     = new URL("http://www.test.com"),
        reference       = FileReference("REF"),
        fileName        = "test.pdf",
        uploadTimestamp = Instant.now(),
        error           = ErrorDetails("QUARANTINE", "1a2b3c4d5e"),
        requestContext  = RequestContext(Some("RequestId"), Some("SessionId"), "127.0.0.1")
      )

      upscanAuditingService.notifyFileIsQuarantined(event)

      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(auditConnector).sendEvent(eventCaptor.capture())(any[HeaderCarrier], any[ExecutionContext])

      val dataEvent: DataEvent = eventCaptor.getValue

      dataEvent.auditSource                 shouldBe "upscan"
      dataEvent.auditType                   shouldBe "invalidFileUploaded"
      dataEvent.detail.get("fileReference") shouldBe Some("REF")
      dataEvent.detail.get("failureReason") shouldBe Some("QUARANTINE")

      dataEvent.tags.get("transactionName")      shouldBe Some("invalid-file-uploaded")
      dataEvent.tags.get(HeaderNames.xSessionId) shouldBe Some("SessionId")
      dataEvent.tags.get(HeaderNames.xRequestId) shouldBe Some("RequestId")
      dataEvent.tags.get("clientIp")             shouldBe Some("127.0.0.1")
    }
  }
}
