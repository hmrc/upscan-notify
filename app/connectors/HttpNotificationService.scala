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
import services.NotificationService
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

class HttpNotificationService @Inject()(httpClient: HttpClient)(implicit ec: ExecutionContext)
    extends NotificationService {

  override def notifySuccessfulCallback(uploadedFile: UploadedFile): Future[Unit] = {
    val callback                   = ReadyCallbackBody(uploadedFile.reference, uploadedFile.downloadUrl)
    implicit val hc: HeaderCarrier = HeaderCarrier()
    httpClient
      .POST[ReadyCallbackBody, HttpResponse](uploadedFile.callbackUrl.toString, callback)
      .map(_ => Unit)
  }

  override def notifyFailedCallback(quarantinedFile: QuarantinedFile): Future[Unit] = {
    val callback                   = FailedCallbackBody(quarantinedFile.reference, quarantinedFile.error)
    implicit val hc: HeaderCarrier = HeaderCarrier()
    httpClient
      .POST[FailedCallbackBody, HttpResponse](quarantinedFile.callbackUrl.toString, callback)
      .map(_ => Unit)
  }
}
