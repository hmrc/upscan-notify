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

import cats.effect._
import cats.instances.list._
import cats.syntax.parallel._
import config.ServiceConfiguration
import fs2.Stream
import play.api.Logger

trait PollingJob[T[_]] {
  def build(): T[Unit]

  def jobName(): String = this.getClass.getName
}

class ContinuousPoller(pollingJobs: Seq[PollingJob[IO]], serviceConfiguration: ServiceConfiguration)(
  implicit timer: Timer[IO],
  contextShift: ContextShift[IO]) {

  private def buildStreamFromJob(pollingJob: PollingJob[IO]): Stream[IO, Either[Throwable, Unit]] = {

    val builtPollingJob: IO[Either[Throwable, Unit]] = pollingJob.build().attempt

    val singleStepStream = Stream.eval(builtPollingJob).flatMap {
      case result @ Right(_) =>
        for {
          _ <- Stream.eval(IO.delay(Logger.debug(s"Polling succeeded for flow: [${pollingJob.jobName()}].")))
        } yield result

      case result @ Left(error) =>
        val r = for {
          _ <- Stream.eval(IO.delay(Logger.error(s"Polling failed for flow: [${pollingJob.jobName()}].", error)))
        } yield result

        r.delayBy(serviceConfiguration.retryInterval)
    }

    singleStepStream.repeat

  }

  def run() = {

    val pollingComputations: Seq[IO[Either[Throwable, Unit]]] = for {
      pollingJob <- pollingJobs
      stream = buildStreamFromJob(pollingJob)
      io = for {
        _      <- IO.shift
        result <- stream.compile.drain.attempt
      } yield result
    } yield {
      io
    }

    pollingComputations.toList.parSequence.map(_ => ())

  }

}
