/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.upscannotify.config

import com.amazonaws.services.sqs.AmazonSQS
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.upscannotify.connector.aws.SqsQueueConsumer
import uk.gov.hmrc.upscannotify.service._

import java.time.Clock
import javax.inject.{Inject, Provider}
import scala.concurrent.ExecutionContext

class UpscanQueuesModule extends Module:
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] =
    Seq(
      bind[SuccessfulQueueConsumer].toProvider[SuccessfulSqsQueueConsumerProvider],
      bind[QuarantineQueueConsumer].toProvider[QuarantineSqsQueueConsumerProvider]
    )

class SuccessfulSqsQueueConsumerProvider @Inject()(
  sqsClient    : AmazonSQS,
  configuration: ServiceConfiguration,
  clock        : Clock
)(using
  ExecutionContext
) extends Provider[SuccessfulQueueConsumer]:
  override def get(): SuccessfulQueueConsumer =
    new SqsQueueConsumer(
      sqsClient,
      configuration.outboundSuccessfulQueueUrl,
      configuration.successfulProcessingBatchSize,
      clock
    ) with SuccessfulQueueConsumer

class QuarantineSqsQueueConsumerProvider @Inject()(
  sqsClient    : AmazonSQS,
  configuration: ServiceConfiguration,
  clock        : Clock
)(using
  ExecutionContext
)extends Provider[QuarantineQueueConsumer]:
  override def get(): QuarantineQueueConsumer =
    new SqsQueueConsumer(
      sqsClient,
      configuration.outboundQuarantineQueueUrl,
      configuration.quarantineProcessingBatchSize,
      clock
    ) with QuarantineQueueConsumer
