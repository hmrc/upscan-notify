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

import java.net.URL
import java.time.Instant

import cats.effect
import cats.effect.IO
import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import config.ServiceConfiguration
import connectors.aws.S3EventParser
import model._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar.mock
import org.scalatest.{Matchers, WordSpec}
import util.logging.MockLoggerLike

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class NotifyOnQuarantineFileUploadMessageProcessingJobSpec extends WordSpec with Matchers {

  val consumer             = mock[QuarantineQueueConsumer[IO]]
  val parser               = new S3EventParser[IO]()
  val fileRetriever        = mock[FileNotificationDetailsRetriever[IO]]
  val notificationService  = mock[NotificationService[IO]]
  val metrics              = mock[Metrics]
  val auditingService      = mock[UpscanAuditingService]
  val serviceConfiguration = mock[ServiceConfiguration]
  val mockLogger           = new MockLoggerLike()

  val timer                              = IO.timer(ExecutionContext.global)
  implicit val ioClock: effect.Clock[IO] = timer.clock

  val defaultMetricsRegistry = mock[MetricRegistry]
  when(metrics.defaultRegistry).thenReturn(defaultMetricsRegistry)
  when(defaultMetricsRegistry.counter("quarantinedUploadNotificationSent"))
    .thenReturn(mock[com.codahale.metrics.Counter])

  when(serviceConfiguration.endToEndProcessingThreshold()).thenReturn(0 seconds)

  val testInstance = new NotifyOnQuarantineFileUploadMessageProcessingJob(
    consumer,
    parser,
    fileRetriever,
    notificationService,
    metrics,
    auditingService,
    serviceConfiguration
  )

  "NotifyOnQuarantineFileUploadMessageProcessingJobSpec" when {
    "collectMetricsAfterNotification" should {
      "log all metrics" in {
        val notification = QuarantinedFile(
          new URL("http://my.callback.url"),
          FileReference("upload-file-reference"),
          ErrorDetails("bad file", "quarantined"),
          ValidUploadDetails("test.pdf", "application/pdf", Instant.parse("2018-12-01T14:30:00Z"), "1a2b3c4d5e"),
          RequestContext(Some("requestId"), Some("sessionId"), "127.0.0.1"),
          Map(
            "x-amz-meta-upscan-notify-received"         -> "2018-12-01T14:36:20Z",
            "x-amz-meta-upscan-notify-callback-started" -> "2018-12-01T14:36:30Z",
            "x-amz-meta-upscan-notify-callback-ended"   -> "2018-12-01T14:36:31Z"
          )
        )

        testInstance.collectMetricsAfterNotification(notification, mockLogger).unsafeRunSync()

        val logMessage = mockLogger.getWarnMessage()

        logMessage should include("x-amz-meta-upscan-notify-received")
        logMessage should include("x-amz-meta-upscan-notify-callback-started")
        logMessage should include("x-amz-meta-upscan-notify-callback-end")
        logMessage should include("x-amz-meta-upscan-notify-responded")
      }
    }
  }
}
