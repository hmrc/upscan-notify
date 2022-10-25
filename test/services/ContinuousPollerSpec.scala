/*
 * Copyright 2022 HM Revenue & Customs
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

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import config.ServiceConfiguration
import org.scalatest.concurrent.Eventually
import play.api.inject.DefaultApplicationLifecycle
import test.UnitSpec

import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{ExecutionContext, Future}

class ContinuousPollerSpec extends UnitSpec with Eventually {

  implicit def actorSystem = ActorSystem()

  val serviceConfiguration = new ServiceConfiguration {
    override def accessKeyId: String = ???

    override def awsRegion: String = ???

    override def secretAccessKey: String = ???

    override def sessionToken: Option[String] = ???

    override def retryInterval: FiniteDuration = 1 second

    override def outboundSuccessfulQueueUrl: String = ???

    override def s3UrlExpirationPeriod(serviceName: String): FiniteDuration = ???

    override def outboundQuarantineQueueUrl: String = ???

    override def endToEndProcessingThreshold(): Duration = ???
  }

  "QueuePollingJob" should {
    "continuously poll the queue" in {

      val callCount = new AtomicInteger(0)

      val orchestrator: PollingJob = new PollingJob {
        override def run() = Future.successful(callCount.incrementAndGet())
      }

      val jobs = PollingJobs(List(orchestrator))

      val serviceLifecycle = new DefaultApplicationLifecycle()

      new ContinuousPoller(jobs, serviceConfiguration)(
        actorSystem,
        serviceLifecycle,
        ExecutionContext.Implicits.global)

      eventually {
        callCount.get() > 5
      }

      serviceLifecycle.stop()
    }

    "recover from failure after some time" in {
      val callCount = new AtomicInteger(0)

      val orchestrator: PollingJob = new PollingJob {
        override def run() =
          if (callCount.get() == 1) {
            Future.failed(new RuntimeException("Planned failure"))
          } else {
            Future.successful(callCount.incrementAndGet())
          }
      }

      val jobs             = PollingJobs(List(orchestrator))
      val serviceLifecycle = new DefaultApplicationLifecycle()

      new ContinuousPoller(jobs, serviceConfiguration)(
        actorSystem,
        serviceLifecycle,
        ExecutionContext.Implicits.global)

      eventually {
        callCount.get() > 5
      }

      serviceLifecycle.stop()
    }
  }

}
