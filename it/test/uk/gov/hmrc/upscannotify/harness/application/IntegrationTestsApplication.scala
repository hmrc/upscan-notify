/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.upscannotify.harness.application

import com.amazonaws.services.sqs.AmazonSQS
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import software.amazon.awssdk.services.s3.S3AsyncClient
import uk.gov.hmrc.upscannotify.NotifyModule
import uk.gov.hmrc.upscannotify.harness.module.FilteredNotifyModule
import uk.gov.hmrc.upscannotify.service.ContinuousPoller

/**
  * Bootstrap logic for an integration test application.
  */
object IntegrationTestsApplication:
  val mockS3Client : S3AsyncClient = mock[S3AsyncClient]
  val mockAmazonSQS: AmazonSQS     = mock[AmazonSQS]

  def defaultApplicationBuilder(): GuiceApplicationBuilder =
    GuiceApplicationBuilder(disabled = Seq(classOf[ContinuousPoller]))
      .disable(classOf[NotifyModule])
      .overrides(FilteredNotifyModule())
      .overrides(
        bind[AmazonSQS    ].toInstance(mockAmazonSQS),
        bind[S3AsyncClient].toInstance(mockS3Client)
      )
