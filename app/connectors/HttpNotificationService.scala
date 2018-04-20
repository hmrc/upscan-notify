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

import javax.inject.Inject
import model.{FailedCallbackBody, QuarantinedFile, ReadyCallbackBody, UploadedFile}
import play.api.Logger
import services.NotificationService
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails
import util.logging.LoggingDetails

import scala.concurrent.Future

class HttpNotificationService @Inject()(httpClient: HttpClient)
    extends NotificationService {

  override def notifySuccessfulCallback(uploadedFile: UploadedFile): Future[Unit] = {

    implicit val ld = LoggingDetails.fromFileReference(uploadedFile.reference)
    val callback = ReadyCallbackBody(uploadedFile.reference, uploadedFile.downloadUrl)

    httpClient
      .POST[ReadyCallbackBody, HttpResponse](uploadedFile.callbackUrl.toString, callback)
      .map { httpResponse =>
        Logger.info(
          s"""File ready notification sent to service with callbackUrl: [${uploadedFile.callbackUrl}].
             | Response status was: [${httpResponse.status}].""".stripMargin
        )
      }
  }

  override def notifyFailedCallback(quarantinedFile: QuarantinedFile): Future[Unit] = {

    implicit val ld = LoggingDetails.fromFileReference(quarantinedFile.reference)
    val callback = FailedCallbackBody(quarantinedFile.reference, quarantinedFile.error)

    httpClient
      .POST[FailedCallbackBody, HttpResponse](quarantinedFile.callbackUrl.toString, callback)
      .map { httpResponse =>
        Logger.info(
          s"""File failed notification sent to service with callbackUrl: [${quarantinedFile.callbackUrl}].
             | Response status was: [${httpResponse.status}].""".stripMargin
        )
      }
  }
}
