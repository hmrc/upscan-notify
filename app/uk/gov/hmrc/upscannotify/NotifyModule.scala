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

package uk.gov.hmrc.upscannotify

import javax.inject.{Inject, Provider}
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.upscannotify.config.{PlayBasedServiceConfiguration, ServiceConfiguration}
import uk.gov.hmrc.upscannotify.connector.HttpNotificationService
import uk.gov.hmrc.upscannotify.connector.aws._
import uk.gov.hmrc.upscannotify.service._

import java.time.Clock

class NotifyModule extends Module:
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] =
    Seq(
      bind[ServiceConfiguration].to[PlayBasedServiceConfiguration],
      bind[NotificationService ].to[HttpNotificationService],
      bind[MessageParser       ].to[S3EventParser],
      bind[PollingJobs         ].toProvider[SqsPollingJobsProvider],
      bind[SqsConsumer         ].toSelf.eagerly(),
      bind[Clock               ].toInstance(Clock.systemDefaultZone())
    )

class SqsPollingJobsProvider @Inject()(
  successfulFileUploadProcessingJob: NotifyOnSuccessfulFileUploadMessageProcessingJob,
  quarantineFileUploadProcessingJob: NotifyOnQuarantineFileUploadMessageProcessingJob
) extends Provider[PollingJobs]:
  override def get(): PollingJobs =
    PollingJobs(List(
      successfulFileUploadProcessingJob,
      quarantineFileUploadProcessingJob
    ))
