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

import model.UploadedFile
import play.api.libs.json.{Format, Json}
import services.NotificationService
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

class HttpNotificationService @Inject()(httpClient: HttpClient)(implicit ec: ExecutionContext)
    extends NotificationService {

  override def notifyCallback(notification: UploadedFile): Future[Unit] = {
    val callback                   = CallbackBody(notification.reference)
    implicit val hc: HeaderCarrier = HeaderCarrier()
    httpClient
      .POST[CallbackBody, HttpResponse](notification.url.toString, callback)
      .map(_ => Unit)
  }
}

case class CallbackBody(reference: String)

object CallbackBody {
  implicit val formats: Format[CallbackBody] = Json.format[CallbackBody]
}
