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

import model.S3ObjectLocation

import scala.concurrent.Future

case class ObjectMetadata(userMetadata: Map[String, String], size: Long)

case class ObjectWithMetadata(content: String, metadata: ObjectMetadata)

trait FileManager {

  def retrieveMetadata(objectLocation: S3ObjectLocation): Future[ObjectMetadata]

  def retrieveObject(objectLocation: S3ObjectLocation): Future[ObjectWithMetadata]

}
