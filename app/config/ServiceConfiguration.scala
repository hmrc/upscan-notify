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

package config

import javax.inject.Inject

import play.api.Configuration

import scala.concurrent.duration.{FiniteDuration, _}

trait ServiceConfiguration {
  def retryInterval: FiniteDuration

  def outboundSuccessfulQueueUrl: String

  def accessKeyId: String

  def secretAccessKey: String

  def sessionToken: Option[String]

  def awsRegion: String

  def callbackUrlMetadataKey: String

  def daysToExpiration: Int
}

class PlayBasedServiceConfiguration @Inject()(configuration: Configuration) extends ServiceConfiguration {

  override def outboundSuccessfulQueueUrl: String =
    getRequired(configuration.getString(_), "aws.sqs.queue.outbound.successful")

  override def awsRegion = getRequired(configuration.getString(_), "aws.s3.region")

  override def accessKeyId = getRequired(configuration.getString(_), "aws.accessKeyId")

  override def secretAccessKey = getRequired(configuration.getString(_), "aws.secretAccessKey")

  override def sessionToken = configuration.getString("aws.sessionToken")

  override def callbackUrlMetadataKey: String =
    getRequired(configuration.getString(_), "aws.sqs.callbackUrlMetadataKey")

  override def retryInterval = getRequired(configuration.getMilliseconds, "aws.sqs.retry.interval").milliseconds

  override def daysToExpiration: Int = getRequired(configuration.getInt, "aws.s3.daysToExpiration")

  def getRequired[T](function: String => Option[T], key: String) =
    function(key).getOrElse(throw new IllegalStateException(s"Configuration key not found: $key"))

}
