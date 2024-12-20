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

package uk.gov.hmrc.upscannotify.harness.model

import play.api.libs.json._
import uk.gov.hmrc.upscannotify.connector.{FileStatus, ReadyCallbackBody, UploadDetails}
import uk.gov.hmrc.upscannotify.model.FileReference

import java.net.URL

object JsonReads:
  private given Reads[URL] =
    (json: JsValue) =>
      json match
        case JsString(str) => JsSuccess(URL(str))
        case _             => JsError(s"Cannot deserialize URL from json: [${json.toString}].")

  private given Reads[FileStatus] =
    (json: JsValue) =>
      json match
        case JsString("READY")  => JsSuccess(FileStatus.Ready)
        case JsString("FAILED") => JsSuccess(FileStatus.Failed)
        case _                  => JsError(s"Cannot deserialize FileStatus from json: [${json.toString}].")

  private given Reads[FileReference] =
    (json: JsValue) =>
      json match
        case JsString(reference) => JsSuccess(FileReference(reference))
        case _                   => JsError(s"Cannot deserialize FileReference from json: [${json.toString}].")

  private given Reads[UploadDetails] =
    Json.reads[UploadDetails]

  given Reads[ReadyCallbackBody] =
    Json.reads[ReadyCallbackBody]
