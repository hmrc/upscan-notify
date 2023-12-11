/*
 * Copyright 2021 HM Revenue & Customs
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

package harness.model

import java.net.URL
import connectors.{FailedFileStatus, FileStatus, ReadyCallbackBody, ReadyFileStatus, UploadDetails}
import model._
import play.api.libs.json._

object JsonReads {
  implicit val urlReads: Reads[URL] = new Reads[URL] {
    override def reads(json: JsValue): JsResult[URL] =
      json match {
        case JsString(str) => JsSuccess(new URL(str))
        case _             => JsError(s"Cannot deserialize URL from json: [${json.toString}].")
      }
  }

  implicit val fileStatusReads: Reads[FileStatus] = new Reads[FileStatus] {
    override def reads(json: JsValue): JsResult[FileStatus] =
      json match {
        case JsString("READY")  => JsSuccess(ReadyFileStatus)
        case JsString("FAILED") => JsSuccess(FailedFileStatus)
        case _                  => JsError(s"Cannot deserialize FileStatus from json: [${json.toString}].")
      }
  }

  implicit val fileReferenceReads: Reads[FileReference] = new Reads[FileReference] {
    override def reads(json: JsValue): JsResult[FileReference] =
      json match {
        case JsString(reference) => JsSuccess(FileReference(reference))
        case _                   => JsError(s"Cannot deserialize FileReference from json: [${json.toString}].")
      }
  }

  private implicit val uploadDetialsReads = Json.reads[UploadDetails]
  implicit val readyCallbackBodyReads = Json.reads[ReadyCallbackBody]
}
