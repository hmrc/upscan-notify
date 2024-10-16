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

package uk.gov.hmrc.upscannotify.test

import java.time.{Clock, Duration, Instant, ZoneId}
import java.util.concurrent.atomic.AtomicLong

// Clock impl that increments the time by "increment" for each call to "instant".
class IncrementingClock(
  baseTimeInMillis: Long,
  increment       : Duration,
  zoneId          : ZoneId = ZoneId.systemDefault()
) extends Clock:

  private val baseMillis = AtomicLong(baseTimeInMillis)

  override def getZone: ZoneId =
    zoneId

  override def withZone(newZoneId: ZoneId): Clock =
    IncrementingClock(baseMillis.get(), increment, zoneId)

  override def instant(): Instant =
    Instant.ofEpochMilli(baseMillis.getAndAdd(increment.toMillis))
