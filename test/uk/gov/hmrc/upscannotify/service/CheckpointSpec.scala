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

import uk.gov.hmrc.upscannotify.test.UnitSpec

import java.time.Instant

class CheckpointSpec extends UnitSpec:

  private val checkpoints =
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

  "Checkpoint.breakdown" should:
    "sort checkpoints chronology" in:
      Checkpoint.breakdown(checkpoints) shouldBe
        """upscan-initiate-received @ 2018-12-19T19:01:48.565Z
          |upscan-initiate-response @ 2018-12-19T19:01:48.643Z, took 78 ms
          |upscan-verify-received @ 2018-12-19T19:01:49.768Z, took 1125 ms
          |upscan-verify-virusscan-started @ 2018-12-19T19:01:50.254Z, took 486 ms
          |upscan-verify-virusscan-ended @ 2018-12-19T19:01:50.303Z, took 49 ms
          |upscan-verify-filetype-started @ 2018-12-19T19:01:50.331Z, took 28 ms
          |upscan-verify-filetype-ended @ 2018-12-19T19:01:50.377Z, took 46 ms
          |upscan-verify-outbound-queued @ 2018-12-19T19:01:50.380Z, took 3 ms
          |upscan-notify-received @ 2018-12-19T19:01:50.601Z, took 221 ms
          |upscan-notify-callback-started @ 2018-12-19T19:01:50.632Z, took 31 ms
          |upscan-notify-callback-ended @ 2018-12-19T19:01:50.639Z, took 7 ms
          |upscan-notify-responded @ 2018-12-19T19:01:50.640Z, took 1 ms""".stripMargin
