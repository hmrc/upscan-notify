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

package connectors

import java.time.Clock

import javax.inject.Inject
import model._
import play.api.Logger
import services.NotificationService
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails
import util.logging.LoggingDetails

import scala.concurrent.Future

class HttpNotificationService @Inject()(httpClient: HttpClient, clock: Clock) extends NotificationService {

  override def notifySuccessfulCallback(uploadedFile: UploadedFile): Future[UploadedFile] = {

    implicit val ld = LoggingDetails.fromFileReference(uploadedFile.reference)

    val callback = ReadyCallbackBody(
      reference     = uploadedFile.reference,
      downloadUrl   = uploadedFile.downloadUrl,
      uploadDetails = uploadedFile.uploadDetails)

    val startTime = clock.instant()

    httpClient
      .POST[ReadyCallbackBody, HttpResponse](uploadedFile.callbackUrl.toString, callback)
      .map { httpResponse => {
          val endTime = clock.instant()

          Logger.info(
            s"""File ready notification sent to service with callbackUrl: [${uploadedFile.callbackUrl}].
               | Response status was: [${httpResponse.status}].""".stripMargin
          )

          uploadedFile.copyWithUserMetadata(
            "x-amz-meta-upscan-notify-callback-started" -> startTime.toString(),
            "x-amz-meta-upscan-notify-callback-ended"   -> endTime.toString()
          )
        }
      }
  }

  override def notifyFailedCallback(quarantinedFile: QuarantinedFile): Future[QuarantinedFile] = {

    implicit val ld = LoggingDetails.fromFileReference(quarantinedFile.reference)
    val callback    = FailedCallbackBody(reference = quarantinedFile.reference, failureDetails = quarantinedFile.error)

    val startTime = clock.instant()

    httpClient
      .POST[FailedCallbackBody, HttpResponse](quarantinedFile.callbackUrl.toString, callback)
      .map { httpResponse => {
          val endTime = clock.instant()

          Logger.info(
            s"""File failed notification sent to service with callbackUrl: [${quarantinedFile.callbackUrl}].
               | Response status was: [${httpResponse.status}].""".stripMargin
          )

          quarantinedFile.copyWithUserMetadata(
            "x-amz-meta-upscan-notify-callback-started" -> startTime.toString(),
            "x-amz-meta-upscan-notify-callback-ended"   -> endTime.toString()
          )
        }
      }
  }
}
