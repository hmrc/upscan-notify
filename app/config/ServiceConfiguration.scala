/*
 * Copyright 2020 HM Revenue & Customs
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
import play.api.{Configuration, Logger}
import uk.gov.hmrc.play.bootstrap.config.RunMode

import scala.concurrent.duration._

trait ServiceConfiguration {
  def retryInterval: FiniteDuration

  def outboundSuccessfulQueueUrl: String

  def outboundQuarantineQueueUrl: String

  def accessKeyId: String

  def secretAccessKey: String

  def sessionToken: Option[String]

  def awsRegion: String

  def s3UrlExpirationPeriod(serviceName: String): FiniteDuration

  def endToEndProcessingThreshold(): Duration
}

class PlayBasedServiceConfiguration @Inject()(configuration: Configuration, runMode: RunMode) extends ServiceConfiguration {
  import PlayBasedServiceConfiguration._

  override def outboundSuccessfulQueueUrl: String =
    getRequired(configuration.getOptional[String](_), "aws.sqs.queue.outbound.successful")

  override def outboundQuarantineQueueUrl: String =
    getRequired(configuration.getOptional[String](_), "aws.sqs.queue.outbound.quarantine")

  override def awsRegion: String = getRequired(configuration.getOptional[String](_), "aws.s3.region")

  override def accessKeyId: String = getRequired(configuration.getOptional[String](_), "aws.accessKeyId")

  override def secretAccessKey: String = getRequired(configuration.getOptional[String](_), "aws.secretAccessKey")

  override def sessionToken: Option[String] = configuration.getOptional[String]("aws.sessionToken")

  override def retryInterval: FiniteDuration = getRequired(readDurationAsMillis,"aws.sqs.retry.interval").milliseconds

  override def s3UrlExpirationPeriod(serviceName: String): FiniteDuration = {
      val serviceS3UrlExpiry = validS3UrlExpirationPeriodWithKey(configKeyForConsumingService(serviceName, S3UrlExpirationPeriod.ConfigDescriptor))
      lazy val defaultS3UrlExpiry = validS3UrlExpirationPeriodWithKey(configKeyForDefault(S3UrlExpirationPeriod.ConfigDescriptor))
      lazy val fallbackS3UrlExpiry = "FallbackS3UrlExpirationPeriod" -> S3UrlExpirationPeriod.FallbackValue

      val (source, value) = serviceS3UrlExpiry.orElse(defaultS3UrlExpiry).getOrElse(fallbackS3UrlExpiry)
      Logger.debug(s"Using configuration value of [$value] for s3UrlExpirationPeriod for service [$serviceName] from config [$source]")
      value
    }

  override def endToEndProcessingThreshold(): Duration =
    getRequired(readDurationAsMillis,"upscan.endToEndProcessing.threshold").seconds

  private def getRequired[T](function: String => Option[T], key: String): T =
    function(key).getOrElse(throw new IllegalStateException(s"Configuration key not found: $key"))

  private def replaceInvalidJsonChars(serviceName: String): String =
    serviceName.replaceAll("[/.]", "-")

  private def validS3UrlExpirationPeriodWithKey(key: String): Option[(String, FiniteDuration)] =
    readDurationAsMillis(key).map(_.milliseconds)
      .filter(_ <= S3UrlExpirationPeriod.MaxValue)
      .map(key -> _)

  private def readDurationAsMillis(key: String): Option[Long] =
    configuration.getOptional[scala.concurrent.duration.Duration](key).map(_.toMillis)

  private def configKeyForDefault(configDescriptor: String): String =
    s"${runMode.env}.upscan.default.$configDescriptor"

  private def configKeyForConsumingService(serviceName: String, configDescriptor: String): String =
    s"${runMode.env}.upscan.consuming-services.${replaceInvalidJsonChars(serviceName)}.$configDescriptor"
}

private[config] object PlayBasedServiceConfiguration {
  object S3UrlExpirationPeriod {
    val ConfigDescriptor: String = "aws.s3.urlExpirationPeriod"
    val MaxValue: FiniteDuration = 7.days
    val FallbackValue: FiniteDuration = 1.day
  }
}