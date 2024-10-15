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
import org.mockito.Mockito.when
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing
import uk.gov.hmrc.upscannotify.config.ServiceConfiguration
import uk.gov.hmrc.upscannotify.connector.aws.S3EventParser
import uk.gov.hmrc.upscannotify.model._
import uk.gov.hmrc.upscannotify.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import ch.qos.logback.classic.Level

class NotifyOnQuarantineFileUploadMessageProcessingJobSpec extends UnitSpec with LogCapturing:

  val consumer             = mock[QuarantineQueueConsumer]
  val parser               = S3EventParser()
  val fileRetriever        = mock[FileNotificationDetailsRetriever]
  val notificationService  = mock[NotificationService]
  val clock                = Clock.systemDefaultZone()
  val auditingService      = mock[UpscanAuditingService]
  val serviceConfiguration = mock[ServiceConfiguration]

  val metricsRegistry = mock[MetricRegistry]
  when(metricsRegistry.counter("quarantinedUploadNotificationSent"))
    .thenReturn(mock[com.codahale.metrics.Counter])

  when(serviceConfiguration.endToEndProcessingThreshold()).thenReturn(0.seconds)

  val testInstance = NotifyOnQuarantineFileUploadMessageProcessingJob(
    consumer,
    parser,
    fileRetriever,
    notificationService,
    metricsRegistry,
    clock,
    auditingService,
    serviceConfiguration
  )

  "NotifyOnQuarantineFileUploadMessageProcessingJobSpec" when:
    "collectMetricsAfterNotification" should:
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
            Checkpoint("x-amz-meta-upscan-notify-received"        , Instant.parse("2018-12-01T14:36:20Z")),
            Checkpoint("x-amz-meta-upscan-notify-callback-started", Instant.parse("2018-12-01T14:36:30Z")),
            Checkpoint("x-amz-meta-upscan-notify-callback-ended"  , Instant.parse("2018-12-01T14:36:31Z"))
          ))

        withCaptureOfLoggingFrom(testInstance.logger): logs =>
          testInstance.collectMetricsAfterNotification(notification, checkpoints)

          val warnMessages = logs.filter(_.getLevel == Level.WARN).map(_.getFormattedMessage)

          warnMessages.size shouldBe 1
          warnMessages.head should include("upscan-file-uploaded")
          warnMessages.head should include("upscan-notify-received")
          warnMessages.head should include("upscan-notify-callback-started")
          warnMessages.head should include("upscan-notify-callback-end")
          warnMessages.head should include("upscan-notify-responded")
