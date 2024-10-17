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

package uk.gov.hmrc.upscannotify.model

import uk.gov.hmrc.upscannotify.test.UnitSpec

import java.time.Instant

class FileProcessingDetailsSpec extends UnitSpec:

  private val testInstance = Checkpoints(
    Seq(
      Checkpoint("upscan-notify-received"         , Instant.parse("2018-12-19T19:01:50.601Z")),
      Checkpoint("upscan-verify-virusscan-ended"  , Instant.parse("2018-12-19T19:01:50.303Z")),
      Checkpoint("upscan-verify-received"         , Instant.parse("2018-12-19T19:01:49.768Z")),
      Checkpoint("upscan-notify-callback-ended"   , Instant.parse("2018-12-19T19:01:50.639Z")),
      Checkpoint("upscan-verify-filetype-ended"   , Instant.parse("2018-12-19T19:01:50.377Z")),
      Checkpoint("upscan-verify-outbound-queued"  , Instant.parse("2018-12-19T19:01:50.380Z")),
      Checkpoint("upscan-initiate-response"       , Instant.parse("2018-12-19T19:01:48.643Z")),
      Checkpoint("upscan-verify-virusscan-started", Instant.parse("2018-12-19T19:01:50.254Z")),
      Checkpoint("upscan-verify-filetype-started" , Instant.parse("2018-12-19T19:01:50.331Z")),
      Checkpoint("upscan-notify-callback-started" , Instant.parse("2018-12-19T19:01:50.632Z")),
      Checkpoint("upscan-initiate-received"       , Instant.parse("2018-12-19T19:01:48.565Z")),
      Checkpoint("upscan-notify-responded"        , Instant.parse("2018-12-19T19:01:50.640Z"))
    )
  )

  "UserMetadataLike" should:
    "sort checkpoints chronology" in:
      testInstance.sortedCheckpoints should contain theSameElementsInOrderAs Seq(
        Checkpoint("upscan-initiate-received"       , Instant.parse("2018-12-19T19:01:48.565Z")),
        Checkpoint("upscan-initiate-response"       , Instant.parse("2018-12-19T19:01:48.643Z")),
        Checkpoint("upscan-verify-received"         , Instant.parse("2018-12-19T19:01:49.768Z")),
        Checkpoint("upscan-verify-virusscan-started", Instant.parse("2018-12-19T19:01:50.254Z")),
        Checkpoint("upscan-verify-virusscan-ended"  , Instant.parse("2018-12-19T19:01:50.303Z")),
        Checkpoint("upscan-verify-filetype-started" , Instant.parse("2018-12-19T19:01:50.331Z")),
        Checkpoint("upscan-verify-filetype-ended"   , Instant.parse("2018-12-19T19:01:50.377Z")),
        Checkpoint("upscan-verify-outbound-queued"  , Instant.parse("2018-12-19T19:01:50.380Z")),
        Checkpoint("upscan-notify-received"         , Instant.parse("2018-12-19T19:01:50.601Z")),
        Checkpoint("upscan-notify-callback-started" , Instant.parse("2018-12-19T19:01:50.632Z")),
        Checkpoint("upscan-notify-callback-ended"   , Instant.parse("2018-12-19T19:01:50.639Z")),
        Checkpoint("upscan-notify-responded"        , Instant.parse("2018-12-19T19:01:50.640Z"))
      )
