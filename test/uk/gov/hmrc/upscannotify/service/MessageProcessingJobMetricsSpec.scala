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

import java.net.URL
import java.time.{Clock, Instant}
import com.codahale.metrics.MetricRegistry
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing
import uk.gov.hmrc.upscannotify.model._
import uk.gov.hmrc.upscannotify.test.UnitSpec

import scala.concurrent.duration._
import ch.qos.logback.classic.Level

class MessageProcessingJobMetricsSpec extends UnitSpec with LogCapturing:

  val metricsRegistry = MetricRegistry()
  val clock           = Clock.systemDefaultZone()

  "MessageProcessingJob" when:
    "collectMetricsAfterNotificationSuccess" should:
      "log all metrics" in:
        val notification = SuccessfulProcessingDetails(
          callbackUrl     = URL("http://my.callback.url"),
          reference       = FileReference("upload-file-reference"),
          downloadUrl     = URL("http://my.download.url/bucket/123"),
          size            = 0L,
          fileName        = "test.pdf",
          fileMimeType    = "application/pdf",
          uploadTimestamp = Instant.parse("2018-12-01T14:30:00Z"),
          checksum        = "1a2b3c4d5e",
          requestContext  = RequestContext(Some("requestId"), Some("sessionId"), "127.0.0.1")
        )

        val checkpoints = Checkpoints(
          Seq(
            Checkpoint("upscan-notify-received"        , Instant.parse("2018-12-01T14:36:20Z")),
            Checkpoint("upscan-notify-callback-started", Instant.parse("2018-12-01T14:36:30Z")),
            Checkpoint("upscan-notify-callback-ended"  , Instant.parse("2018-12-01T14:36:31Z"))
          )
        )

        withCaptureOfLoggingFrom(MessageProcessingJob.logger): logs =>
          MessageProcessingJob.collectMetricsAfterNotificationSuccess(notification, checkpoints, endToEndProcessingThreshold = 0.seconds)(using metricsRegistry, clock)

          val warnMessages = logs.filter(_.getLevel == Level.WARN).map(_.getFormattedMessage)

          warnMessages.size shouldBe 1
          warnMessages.head should include("upscan-file-uploaded")
          warnMessages.head should include("upscan-notify-received")
          warnMessages.head should include("upscan-notify-callback-started")
          warnMessages.head should include("upscan-notify-callback-end")
          warnMessages.head should include("upscan-notify-responded")


    "collectMetricsAfterNotificationFailed" should:
      "log all metrics" in:
        val notification = FailedProcessingDetails(
          callbackUrl     = URL("http://my.callback.url"),
          reference       = FileReference("upload-file-reference"),
          fileName        = "test.pdf",
          uploadTimestamp = Instant.parse("2018-12-01T14:30:00Z"),
          error           = ErrorDetails("bad file", "quarantined"),
          requestContext  = RequestContext(Some("requestId"), Some("sessionId"), "127.0.0.1")
        )

        val checkpoints = Checkpoints(
          Seq(
            Checkpoint("upscan-notify-received"        , Instant.parse("2018-12-01T14:36:20Z")),
            Checkpoint("upscan-notify-callback-started", Instant.parse("2018-12-01T14:36:30Z")),
            Checkpoint("upscan-notify-callback-ended"  , Instant.parse("2018-12-01T14:36:31Z"))
          ))

        withCaptureOfLoggingFrom(MessageProcessingJob.logger): logs =>
          MessageProcessingJob.collectMetricsAfterNotificationFailed(notification, checkpoints, endToEndProcessingThreshold = 0.seconds)(using metricsRegistry, clock)

          val warnMessages = logs.filter(_.getLevel == Level.WARN).map(_.getFormattedMessage)

          warnMessages.size shouldBe 1
          warnMessages.head should include("upscan-file-uploaded")
          warnMessages.head should include("upscan-notify-received")
          warnMessages.head should include("upscan-notify-callback-started")
          warnMessages.head should include("upscan-notify-callback-end")
          warnMessages.head should include("upscan-notify-responded")
