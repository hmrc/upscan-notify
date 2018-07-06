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

import javax.inject.Inject
import model.{QuarantinedFile, UploadedFile}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.{RequestId, SessionId}
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.DataEvent

import scala.concurrent.{ExecutionContext, Future}

class CleanFileUploaded(override val detail: Map[String, String])(implicit hc: HeaderCarrier)
    extends DataEvent(auditSource = "upscan", auditType = "cleanFileUploaded", detail = detail, tags = hc.headers.toMap)

class InvalidFileUploaded(override val detail: Map[String, String])(implicit hc: HeaderCarrier)
    extends DataEvent(
      auditSource = "upscan",
      auditType   = "invalidFileUploaded",
      detail      = detail,
      tags        = hc.headers.toMap)

class UpscanAuditingService @Inject()(auditConnector: AuditConnector)(implicit ec: ExecutionContext) {

  def notifyFileUploadedSuccessfully(notification: UploadedFile): Unit = {

    implicit val hc = HeaderCarrier(
      sessionId    = notification.requestContext.sessionId.map(SessionId),
      requestId    = notification.requestContext.requestId.map(RequestId),
      trueClientIp = Some(notification.requestContext.clientIp)
    )

    val event = new CleanFileUploaded(
      Map(
        "fileReference" -> notification.reference.reference,
        "fileSize"      -> notification.size.toString
      )
    )

    auditConnector.sendEvent(event = event)

  }

  def notifyFileIsQuarantined(notification: QuarantinedFile): Unit = {
    implicit val hc = HeaderCarrier(
      sessionId    = notification.requestContext.sessionId.map(SessionId),
      requestId    = notification.requestContext.requestId.map(RequestId),
      trueClientIp = Some(notification.requestContext.clientIp)
    )

    val event = new InvalidFileUploaded(
      Map(
        "fileReference" -> notification.reference.reference,
        "failureReason" -> notification.error.failureReason
      )
    )

    auditConnector.sendEvent(event = event)
  }

}
