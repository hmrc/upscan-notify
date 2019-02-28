/*
 * Copyright 2019 HM Revenue & Customs
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
import model._
import uk.gov.hmrc.http.HeaderNames
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.DataEvent

import scala.concurrent.ExecutionContext

object UpscanEvent {
  def requestContentTags(requestContext: RequestContext): Map[String, String] =
    Map(
      "clientIp" -> requestContext.clientIp
    ) ++
      requestContext.requestId.map(value => HeaderNames.xRequestId -> value).toMap ++
      requestContext.sessionId.map(value => HeaderNames.xSessionId -> value).toMap

}

class CleanFileUploaded(fileReference: String, fileSize: Long, requestContext: RequestContext)
    extends DataEvent(
      auditSource = "upscan",
      auditType   = "cleanFileUploaded",
      detail = Map(
        "fileReference" -> fileReference,
        "fileSize"      -> fileSize.toString
      ),
      tags = UpscanEvent.requestContentTags(requestContext) + ("transactionName" -> "clean-file-uploaded")
    )

class InvalidFileUploaded(fileReference: String, failureReason: String, requestContext: RequestContext)
    extends DataEvent(
      auditSource = "upscan",
      auditType   = "invalidFileUploaded",
      detail = Map(
        "fileReference" -> fileReference,
        "failureReason" -> failureReason
      ),
      tags = UpscanEvent.requestContentTags(requestContext) + ("transactionName" -> "invalid-file-uploaded")
    )

trait EventBuilder[T] {
  def build(input: T): DataEvent
}

class UpscanAuditingService @Inject()(auditConnector: AuditConnector)(implicit ec: ExecutionContext) {

  def notifyFileUploadedSuccessfully[T](notification: FileProcessingDetails[SucessfulResult])(): Unit = {

    val event =
      new CleanFileUploaded(notification.reference.reference, notification.result.size, notification.requestContext)
    auditConnector.sendEvent(event = event)

  }

  def notifyFileIsQuarantined(notification: FileProcessingDetails[QuarantinedResult]): Unit = {
    val event = new InvalidFileUploaded(
      notification.reference.reference,
      notification.result.error.failureReason,
      notification.requestContext)
    auditConnector.sendEvent(event = event)
  }

}
