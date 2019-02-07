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
import java.time.{Clock, Instant}

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import config.ServiceConfiguration
import connectors.aws.S3EventParser
import model._
import org.mockito.Mockito._
import org.scalatest.{Matchers, WordSpec}
import org.scalatest.mockito.MockitoSugar.mock
import util.logging.MockLoggerLike

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class NotifyOnSuccessfulFileUploadMessageProcessingJobSpec extends WordSpec with Matchers {

  val consumer = mock[SuccessfulQueueConsumer]
  val parser   = new S3EventParser()
  val fileRetriever = mock[FileNotificationDetailsRetriever]
  val notificationService = mock[NotificationService]
  val metrics = mock[Metrics]
  val clock = Clock.systemDefaultZone()
  val auditingService = mock[UpscanAuditingService]
  val serviceConfiguration = mock[ServiceConfiguration]
  val mockLogger = new MockLoggerLike()

  val defaultMetricsRegistry = mock[MetricRegistry]
  when(metrics.defaultRegistry).thenReturn(defaultMetricsRegistry)
  when(defaultMetricsRegistry.timer("totalFileProcessingTime")).thenReturn(mock[com.codahale.metrics.Timer])
  when(defaultMetricsRegistry.histogram("fileSize")).thenReturn(mock[com.codahale.metrics.Histogram])
  when(defaultMetricsRegistry.counter("successfulUploadNotificationSent")).thenReturn(mock[com.codahale.metrics.Counter])

  when(serviceConfiguration.endToEndProcessingThreshold()).thenReturn(0 seconds)


  val testInstance = new NotifyOnSuccessfulFileUploadMessageProcessingJob(
    consumer, parser, fileRetriever, notificationService, metrics, clock, auditingService, serviceConfiguration
  )

  "NotifyOnSuccessfulFileUploadMessageProcessingJobSpec" when {
    "collectMetricsAfterNotification" should {
      "log all metrics" in {
        val notification = UploadedFile(
          new URL("http://my.callback.url"),
          FileReference("upload-file-reference"),
          new URL("http://my.download.url/bucket/123"),
          0L,
          ValidUploadDetails("test.pdf", "application/pdf", Instant.parse("2018-12-01T14:30:00Z"), "1a2b3c4d5e"),
          RequestContext(Some("requestId"), Some("sessionId"), "127.0.0.1"),
          Map(
            "x-amz-meta-upscan-notify-received"         -> "2018-12-01T14:36:20Z",
            "x-amz-meta-upscan-notify-callback-started" -> "2018-12-01T14:36:30Z",
            "x-amz-meta-upscan-notify-callback-ended"   -> "2018-12-01T14:36:31Z"
          )
        )

        testInstance.collectMetricsAfterNotification(notification, mockLogger)

        val logMessage = mockLogger.getWarnMessage()

        logMessage should include ("x-amz-meta-upscan-notify-received")
        logMessage should include ("x-amz-meta-upscan-notify-callback-started")
        logMessage should include ("x-amz-meta-upscan-notify-callback-end")
        logMessage should include ("x-amz-meta-upscan-notify-responded")
      }
    }
  }
}
