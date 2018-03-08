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

import java.net.URL
import javax.inject.Inject

import model.UploadedFile
import play.api.libs.json._
import services.NotificationService
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

class HttpNotificationService @Inject()(httpClient: HttpClient)(implicit ec: ExecutionContext)
    extends NotificationService {

  override def notifyCallback(uploadedFile: UploadedFile): Future[Unit] = {
    val callback                   = CallbackBody.fromUploadedFile(uploadedFile)
    implicit val hc: HeaderCarrier = HeaderCarrier()
    httpClient
      .POST[CallbackBody, HttpResponse](uploadedFile.callbackUrl.toString, callback)
      .map(_ => Unit)
  }
}

case class CallbackBody(reference: String, downloadUrl: URL)

object CallbackBody {
  def fromUploadedFile(uploadedFile: UploadedFile) =
    new CallbackBody(uploadedFile.reference, uploadedFile.downloadUrl)

  implicit val urlFormats: Writes[URL] = new Writes[URL] {
    override def writes(o: URL): JsValue = JsString(o.toString)
  }
  implicit val formats: Writes[CallbackBody] = Json.writes[CallbackBody]
}
