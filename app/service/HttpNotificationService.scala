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

package service

import java.net.URL
import javax.inject.Inject

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import scala.util.Try

class HttpNotificationService @Inject()(httpClient: HttpClient)(implicit ec: ExecutionContext)
    extends NotificationService {
  override def notifyCallback(url: URL): Try[Any] =
    Try {
      val callback    = Callback("12345")
      implicit val hc = HeaderCarrier()
      Await.result(httpClient.POST[Callback, String](url.toString, callback), 30.seconds)
    }
}

case class Callback(reference: String)

object Callback {
  implicit val formats: Format[Callback] = Json.format[Callback]
}
