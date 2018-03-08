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

import config.{PlayBasedServiceConfiguration, ServiceConfiguration}
import connectors.HttpNotificationService
import connectors.aws.{S3EventParser, S3FileNotificationDetailsRetriever, SqsQueueConsumer}
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}
import services._

class NotifyModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] =
    Seq(
      bind[ServiceConfiguration].to[PlayBasedServiceConfiguration].eagerly(),
      bind[FileNotificationDetailsRetriever].to[S3FileNotificationDetailsRetriever],
      bind[NotificationService].to[HttpNotificationService],
      bind[MessageParser].to[S3EventParser],
      bind[QueueConsumer].to[SqsQueueConsumer],
      bind[PollingJob].to[SuccessfulUploadNotificationProcessingFlow],
      bind[ContinousPoller].toSelf.eagerly()
    )
}