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

package connectors.aws

import model.Message
import services.{FileUploadedEvent, UnsupportedMessage}
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import services.{MessageParser, ParsingResult}

import scala.util.{Failure, Success, Try}

object S3EventParser extends MessageParser {

  case class S3EventNotification(records: Seq[S3EventNotificationRecord])

  case class S3EventNotificationRecord(
    eventVersion: String,
    eventSource: String,
    awsRegion: String,
    eventTime: String,
    eventName: String,
    s3: S3Details)

  case class S3Details(bucketName: String, objectKey: String)

  override def parse(message: Message): ParsingResult = {

    implicit val s3reads: Reads[S3Details] =
      ((JsPath \ "bucket" \ "name").read[String] and
        (JsPath \ "object" \ "key").read[String])(S3Details.apply _)

    implicit val reads: Reads[S3EventNotificationRecord] = Json.reads[S3EventNotificationRecord]

    implicit val messageReads: Reads[S3EventNotification] =
      (JsPath \ "Records").read[Seq[S3EventNotificationRecord]].map(S3EventNotification)

    Try(Json.parse(message.body)) match {
      case Success(json) =>
        json
          .validate[S3EventNotification]
          .fold(
            errors => UnsupportedMessage(s"Cannot parse the message ${errors.toString()}"),
            result => interpretS3EventMessage(result)
          )
      case Failure(e) => UnsupportedMessage(s"Invalid JSON: ${e.getMessage}")
    }
  }

  private def interpretS3EventMessage(result: S3EventNotification): ParsingResult =
    result.records match {
      case S3EventNotificationRecord(_, "aws:s3", _, _, "ObjectCreated:Post", s3Details) :: Nil =>
        FileUploadedEvent(s3Details.bucketName, s3Details.objectKey)
      case _ => UnsupportedMessage(s"Unexpected number of records in event ${result.records.toString}")
    }

}
