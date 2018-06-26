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

package services

import java.net.URL
import java.time.Instant

import model.S3ObjectLocation
import play.api.Logger

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

case class ReadyObjectMetadata(
  callbackUrl: URL,
  uploadedTimestamp: Instant,
  checksum: String,
  size: Long,
  requestId: Option[String],
  sessionId: Option[String])

case class FailedObjectMetadata(callbackUrl: URL, size: Long, requestId: Option[String], sessionId: Option[String])

case class ReadyObjectWithMetadata(content: String, metadata: ReadyObjectMetadata)

case class FailedObjectWithMetadata(content: String, metadata: FailedObjectMetadata)

trait FileManager {

  def retrieveReadyMetadata(objectLocation: S3ObjectLocation): Future[ReadyObjectMetadata]

  def retrieveFailedMetadata(objectLocation: S3ObjectLocation): Future[FailedObjectMetadata]

  def retrieveReadyObject(objectLocation: S3ObjectLocation): Future[ReadyObjectWithMetadata]

  def retrieveFailedObject(objectLocation: S3ObjectLocation): Future[FailedObjectWithMetadata]

}
