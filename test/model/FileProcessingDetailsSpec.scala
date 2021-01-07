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

package model

import java.time.Instant

import test.UnitSpec

class FileProcessingDetailsSpec extends UnitSpec {

  private val testInstance = Checkpoints(
    Seq(
      Checkpoint("x-amz-meta-upscan-notify-received", Instant.parse("2018-12-19T19:01:50.601Z")),
      Checkpoint("x-amz-meta-upscan-verify-virusscan-ended", Instant.parse("2018-12-19T19:01:50.303Z")),
      Checkpoint("x-amz-meta-upscan-verify-received", Instant.parse("2018-12-19T19:01:49.768Z")),
      Checkpoint("x-amz-meta-upscan-notify-callback-ended", Instant.parse("2018-12-19T19:01:50.639Z")),
      Checkpoint("x-amz-meta-upscan-verify-filetype-ended", Instant.parse("2018-12-19T19:01:50.377Z")),
      Checkpoint("x-amz-meta-upscan-verify-outbound-queued", Instant.parse("2018-12-19T19:01:50.380Z")),
      Checkpoint("x-amz-meta-upscan-initiate-response", Instant.parse("2018-12-19T19:01:48.643Z")),
      Checkpoint("x-amz-meta-upscan-verify-virusscan-started", Instant.parse("2018-12-19T19:01:50.254Z")),
      Checkpoint("x-amz-meta-upscan-verify-filetype-started", Instant.parse("2018-12-19T19:01:50.331Z")),
      Checkpoint("x-amz-meta-upscan-notify-callback-started", Instant.parse("2018-12-19T19:01:50.632Z")),
      Checkpoint("x-amz-meta-upscan-initiate-received", Instant.parse("2018-12-19T19:01:48.565Z")),
      Checkpoint("x-amz-meta-upscan-notify-responded", Instant.parse("2018-12-19T19:01:50.640Z"))
    )
  )

  "UserMetadataLike" should {
    "sort checkpoints chronology" in {
      testInstance.sortedCheckpoints should contain theSameElementsInOrderAs Seq(
        Checkpoint("x-amz-meta-upscan-initiate-received", Instant.parse("2018-12-19T19:01:48.565Z")),
        Checkpoint("x-amz-meta-upscan-initiate-response", Instant.parse("2018-12-19T19:01:48.643Z")),
        Checkpoint("x-amz-meta-upscan-verify-received", Instant.parse("2018-12-19T19:01:49.768Z")),
        Checkpoint("x-amz-meta-upscan-verify-virusscan-started", Instant.parse("2018-12-19T19:01:50.254Z")),
        Checkpoint("x-amz-meta-upscan-verify-virusscan-ended", Instant.parse("2018-12-19T19:01:50.303Z")),
        Checkpoint("x-amz-meta-upscan-verify-filetype-started", Instant.parse("2018-12-19T19:01:50.331Z")),
        Checkpoint("x-amz-meta-upscan-verify-filetype-ended", Instant.parse("2018-12-19T19:01:50.377Z")),
        Checkpoint("x-amz-meta-upscan-verify-outbound-queued", Instant.parse("2018-12-19T19:01:50.380Z")),
        Checkpoint("x-amz-meta-upscan-notify-received", Instant.parse("2018-12-19T19:01:50.601Z")),
        Checkpoint("x-amz-meta-upscan-notify-callback-started", Instant.parse("2018-12-19T19:01:50.632Z")),
        Checkpoint("x-amz-meta-upscan-notify-callback-ended", Instant.parse("2018-12-19T19:01:50.639Z")),
        Checkpoint("x-amz-meta-upscan-notify-responded", Instant.parse("2018-12-19T19:01:50.640Z"))
      )
    }
  }
}
