/*
 * Copyright 2019 HM Revenue & Customs
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
import cats.effect
import cats.effect.{ContextShift, IO}
import config.ServiceConfiguration
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{FiniteDuration, _}

class ContinuousPollerSpec extends UnitSpec with MockitoSugar with Eventually {

  implicit def actorSystem = ActorSystem()

  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  val timer = IO.timer(ExecutionContext.global)

  implicit val ioClock: effect.Clock[IO] = timer.clock

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

      val orchestrator: PollingJob[IO] = new PollingJob[IO] {
        override def build() = IO.delay(callCount.incrementAndGet())
      }

      val jobs = List(orchestrator)

      val cancellationToken =
        new ContinuousPoller(jobs, serviceConfiguration)(timer, contextShift).run().unsafeRunCancelable(_ => ())

      eventually {
        callCount.get() > 5
      }

      cancellationToken.unsafeRunSync()

    }

    "recover from failure after some time" in {
      val callCount = new AtomicInteger(0)

      val orchestrator: PollingJob[IO] = new PollingJob[IO] {
        override def build() =
          if (callCount.get() == 1) {
            IO.raiseError(new RuntimeException("Planned failure"))
          } else {
            IO.pure(callCount.incrementAndGet())
          }
      }

      val jobs = List(orchestrator)

      val cancellationToken =
        new ContinuousPoller(jobs, serviceConfiguration)(timer, contextShift).run().unsafeRunCancelable(_ => ())

      eventually {
        callCount.get() > 5
      }

      cancellationToken.unsafeRunSync()
    }
  }

}
