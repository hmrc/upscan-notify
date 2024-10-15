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

package uk.gov.hmrc.upscannotify.service

import org.apache.pekko.actor.ActorSystem
import org.scalatest.concurrent.Eventually
import play.api.inject.DefaultApplicationLifecycle
import uk.gov.hmrc.upscannotify.config.ServiceConfiguration
import uk.gov.hmrc.upscannotify.test.UnitSpec

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{ExecutionContext, Future}

class ContinuousPollerSpec extends UnitSpec with Eventually:

  given actorSystem: ActorSystem = ActorSystem()

  val serviceConfiguration =
    new ServiceConfiguration:
      override def successfulProcessingBatchSize: Int = 10

      override def quarantineProcessingBatchSize: Int = 10

      override def accessKeyId: String = ???

      override def awsRegion: String = ???

      override def secretAccessKey: String = ???

      override def sessionToken: Option[String] = ???

      override def retryInterval: FiniteDuration = 1.second

      override def outboundSuccessfulQueueUrl: String = ???

      override def s3UrlExpirationPeriod(serviceName: String): FiniteDuration = ???

      override def outboundQuarantineQueueUrl: String = ???

      override def endToEndProcessingThreshold(): Duration = ???

  "QueuePollingJob" should:
    "continuously poll the queue" in:
      val callCount = AtomicInteger(0)

      val orchestrator: PollingJob =
        new PollingJob:
          override def run() = Future.successful(callCount.incrementAndGet())

      val jobs = PollingJobs(List(orchestrator))

      val serviceLifecycle = DefaultApplicationLifecycle()

      ContinuousPoller(jobs, serviceConfiguration)(using
        actorSystem,
        serviceLifecycle,
        ExecutionContext.Implicits.global
      )

      eventually:
        callCount.get() > 5

      serviceLifecycle.stop()

    "recover from failure after some time" in:
      val callCount = AtomicInteger(0)

      val orchestrator: PollingJob =
        new PollingJob:
          override def run() =
            if callCount.get() == 1 then
              Future.failed(RuntimeException("Planned failure"))
            else
              Future.successful(callCount.incrementAndGet())

      val jobs             = PollingJobs(List(orchestrator))
      val serviceLifecycle = DefaultApplicationLifecycle()

      ContinuousPoller(jobs, serviceConfiguration)(using
        actorSystem,
        serviceLifecycle,
        ExecutionContext.Implicits.global
      )

      eventually:
        callCount.get() > 5

      serviceLifecycle.stop()
