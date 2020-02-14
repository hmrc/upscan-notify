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
import play.api.{Configuration, Environment, Logger}
import uk.gov.hmrc.play.config.RunMode

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

class PlayBasedServiceConfiguration @Inject()(configuration: Configuration, env: Environment) extends ServiceConfiguration {
  import PlayBasedServiceConfiguration._

  private val runMode = RunMode(env.mode, configuration)

  override def outboundSuccessfulQueueUrl: String =
    getRequired(configuration.getString(_), "aws.sqs.queue.outbound.successful")

  override def outboundQuarantineQueueUrl: String =
    getRequired(configuration.getString(_), "aws.sqs.queue.outbound.quarantine")

  override def awsRegion = getRequired(configuration.getString(_), "aws.s3.region")

  override def accessKeyId = getRequired(configuration.getString(_), "aws.accessKeyId")

  override def secretAccessKey = getRequired(configuration.getString(_), "aws.secretAccessKey")

  override def sessionToken = configuration.getString("aws.sessionToken")

  override def retryInterval = getRequired(configuration.getMilliseconds, "aws.sqs.retry.interval").milliseconds

  override def s3UrlExpirationPeriod(serviceName: String): FiniteDuration = {
    val configKey = configKeyForConsumingService(serviceName, S3UrlExpirationPeriod.ConfigDescriptor)
    val maybeErrorOrExpiry = configuration.getMilliseconds(configKey).map(_.milliseconds).map(validateS3UrlExpirationPeriod(_,
      msgWhenInvalid = s"Invalid configuration found for [${S3UrlExpirationPeriod.ConfigDescriptor}] for service [$serviceName] - trying default")
    )
    if (maybeErrorOrExpiry.isEmpty) Logger.debug(s"No configuration found for [${S3UrlExpirationPeriod.ConfigDescriptor}] for service [$serviceName] - trying default")
    maybeErrorOrExpiry.foreach(_.left.foreach(msg => Logger.warn(msg)))

    val expirationPeriod = maybeErrorOrExpiry.fold(ifEmpty = defaultS3UrlExpirationPeriod()) {
      _.fold(_ => defaultS3UrlExpirationPeriod(), identity)
    }
    Logger.debug(s"Using configuration for [${S3UrlExpirationPeriod.ConfigDescriptor}] for service [$serviceName] of [$expirationPeriod]")
    expirationPeriod
  }

  override def endToEndProcessingThreshold(): Duration =
    getRequired(configuration.getMilliseconds, "upscan.endToEndProcessing.threshold").seconds

  private def getRequired[T](function: String => Option[T], key: String): T =
    function(key).getOrElse(throw new IllegalStateException(s"Configuration key not found: $key"))

  private def replaceInvalidJsonChars(serviceName: String): String =
    serviceName.replaceAll("[/.]", "-")

  private def defaultS3UrlExpirationPeriod(): FiniteDuration = {
    val errorOrExpiry = configuration.getMilliseconds(configKeyForDefault(S3UrlExpirationPeriod.ConfigDescriptor)).map(_.milliseconds).toRight(
      left = s"No default value for [${S3UrlExpirationPeriod.ConfigDescriptor}] found - using fallback"
    ).right.flatMap(
      validateS3UrlExpirationPeriod(_, s"Invalid default configuration found for [${S3UrlExpirationPeriod.ConfigDescriptor}] - using fallback")
    )
    errorOrExpiry.left.foreach(msg => Logger.warn(msg))
    errorOrExpiry.fold(_ => S3UrlExpirationPeriod.FallbackValue, identity)
  }

  private def validateS3UrlExpirationPeriod(expiry: FiniteDuration, msgWhenInvalid: => String): Either[String, FiniteDuration] =
    Either.cond(
      expiry <= S3UrlExpirationPeriod.MaxValue,
      right = expiry,
      left = msgWhenInvalid
    )

  private def configKeyForDefault(configDescriptor: String): String =
    s"${runMode.env}.upscan.default.$configDescriptor"

  private def configKeyForConsumingService(serviceName: String, configDescriptor: String): String =
    s"${runMode.env}.upscan.consuming-services.${replaceInvalidJsonChars(serviceName)}.$configDescriptor"
}

private[config] object PlayBasedServiceConfiguration {
  object S3UrlExpirationPeriod {
    val ConfigDescriptor: String = "aws.s3.urlExpirationPeriod"
    val MaxValue: FiniteDuration = 7.days
    val FallbackValue: FiniteDuration = MaxValue
  }
}