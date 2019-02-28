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

package model

import java.net.URL
import java.time.Instant

import org.scalatest.{Matchers, WordSpec}

class FileProcessingDetailsSpec extends WordSpec with Matchers {

  private val testInstance = FileProcessingDetails(
    new URL("http://localhost"),
    FileReference("test"),
    SucessfulResult(new URL("http://localhost"), 1024L, "fileName", "mime-type", Instant.now, "TEST"),
    RequestContext(None, None, "localhost"),
    Map(
      "x-amz-meta-upscan-notify-received"          -> "2018-12-19T19:01:50.601Z",
      "x-amz-meta-upscan-verify-virusscan-ended"   -> "2018-12-19T19:01:50.303Z",
      "x-amz-meta-upscan-verify-received"          -> "2018-12-19T19:01:49.768Z",
      "x-amz-meta-upscan-notify-callback-ended"    -> "2018-12-19T19:01:50.639Z",
      "x-amz-meta-upscan-verify-filetype-ended"    -> "2018-12-19T19:01:50.377Z",
      "x-amz-meta-upscan-verify-outbound-queued"   -> "2018-12-19T19:01:50.380Z",
      "x-amz-meta-upscan-initiate-response"        -> "2018-12-19T19:01:48.643Z",
      "x-amz-meta-upscan-verify-virusscan-started" -> "2018-12-19T19:01:50.254Z",
      "x-amz-meta-upscan-verify-filetype-started"  -> "2018-12-19T19:01:50.331Z",
      "x-amz-meta-upscan-notify-callback-started"  -> "2018-12-19T19:01:50.632Z",
      "x-amz-meta-upscan-initiate-received"        -> "2018-12-19T19:01:48.565Z",
      "x-amz-meta-upscan-notify-responded"         -> "2018-12-19T19:01:50.640Z"
    )
  )

  "UserMetadataLike" should {
    "sort checkpoints chronology" in {
      testInstance
        .checkpoints()
        .toSeq
        .sortBy(UserMetadataLike.sortChronologically) should contain theSameElementsInOrderAs Seq(
        "x-amz-meta-upscan-initiate-received"        -> "2018-12-19T19:01:48.565Z",
        "x-amz-meta-upscan-initiate-response"        -> "2018-12-19T19:01:48.643Z",
        "x-amz-meta-upscan-verify-received"          -> "2018-12-19T19:01:49.768Z",
        "x-amz-meta-upscan-verify-virusscan-started" -> "2018-12-19T19:01:50.254Z",
        "x-amz-meta-upscan-verify-virusscan-ended"   -> "2018-12-19T19:01:50.303Z",
        "x-amz-meta-upscan-verify-filetype-started"  -> "2018-12-19T19:01:50.331Z",
        "x-amz-meta-upscan-verify-filetype-ended"    -> "2018-12-19T19:01:50.377Z",
        "x-amz-meta-upscan-verify-outbound-queued"   -> "2018-12-19T19:01:50.380Z",
        "x-amz-meta-upscan-notify-received"          -> "2018-12-19T19:01:50.601Z",
        "x-amz-meta-upscan-notify-callback-started"  -> "2018-12-19T19:01:50.632Z",
        "x-amz-meta-upscan-notify-callback-ended"    -> "2018-12-19T19:01:50.639Z",
        "x-amz-meta-upscan-notify-responded"         -> "2018-12-19T19:01:50.640Z"
      )
    }
  }
}
