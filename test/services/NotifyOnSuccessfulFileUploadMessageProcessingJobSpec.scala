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

package services

import ch.qos.logback.classic.Level
import com.codahale.metrics.MetricRegistry
import config.ServiceConfiguration
import connectors.aws.S3EventParser
import model._
import org.mockito.Mockito.when
import test.UnitSpec
import uk.gov.hmrc.play.bootstrap.metrics.Metrics
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing

import java.net.URL
import java.time.{Clock, Instant}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class NotifyOnSuccessfulFileUploadMessageProcessingJobSpec extends UnitSpec with LogCapturing:

  val consumer             = mock[SuccessfulQueueConsumer]
  val parser               = S3EventParser()
  val fileRetriever        = mock[FileNotificationDetailsRetriever]
  val notificationService  = mock[NotificationService]
  val metrics              = mock[Metrics]
  val clock                = Clock.systemDefaultZone()
  val auditingService      = mock[UpscanAuditingService]
  val serviceConfiguration = mock[ServiceConfiguration]

  val defaultMetricsRegistry = mock[MetricRegistry]
  when(metrics.defaultRegistry)
    .thenReturn(defaultMetricsRegistry)
  when(defaultMetricsRegistry.timer("totalFileProcessingTime"))
    .thenReturn(mock[com.codahale.metrics.Timer])
  when(defaultMetricsRegistry.histogram("fileSize"))
    .thenReturn(mock[com.codahale.metrics.Histogram])
  when(defaultMetricsRegistry.counter("successfulUploadNotificationSent"))
    .thenReturn(mock[com.codahale.metrics.Counter])

  when(serviceConfiguration.endToEndProcessingThreshold())
    .thenReturn(0.seconds)

  val testInstance = NotifyOnSuccessfulFileUploadMessageProcessingJob(
    consumer,
    parser,
    fileRetriever,
    notificationService,
    metrics,
    clock,
    auditingService,
    serviceConfiguration
  )

  "NotifyOnSuccessfulFileUploadMessageProcessingJobSpec" when:
    "collectMetricsAfterNotification" should:
      "log all metrics" in:
        val notification = model.SuccessfulProcessingDetails(
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
            Checkpoint("x-amz-meta-upscan-notify-received", Instant.parse("2018-12-01T14:36:20Z")),
            Checkpoint("x-amz-meta-upscan-notify-callback-started", Instant.parse("2018-12-01T14:36:30Z")),
            Checkpoint("x-amz-meta-upscan-notify-callback-ended", Instant.parse("2018-12-01T14:36:31Z"))
          )
        )

        withCaptureOfLoggingFrom(testInstance.logger): logs =>
          testInstance.collectMetricsAfterNotification(notification, checkpoints)

          val warnMessages = logs.filter(_.getLevel == Level.WARN).map(_.getFormattedMessage)

          warnMessages.size shouldBe 1
          warnMessages.head should include("upscan-file-uploaded")
          warnMessages.head should include("upscan-notify-received")
          warnMessages.head should include("upscan-notify-callback-started")
          warnMessages.head should include("upscan-notify-callback-end")
          warnMessages.head should include("upscan-notify-responded")
