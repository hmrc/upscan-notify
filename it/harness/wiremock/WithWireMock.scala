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

package harness.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.Options
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}

trait WithWireMock extends BeforeAndAfterEach with BeforeAndAfterAll {
  this: Suite =>

  val WIREMOCK_PORT = Options.DEFAULT_PORT
  lazy val wireMockServer = new WireMockServer(wireMockConfig().port(WIREMOCK_PORT))

  override def beforeAll() = {
    super.beforeAll
    wireMockServer.start
    WireMock.configureFor(WIREMOCK_PORT)
  }

  override def afterAll(): Unit = {
    wireMockServer.stop
    super.afterAll
  }

  override def beforeEach() = {
    super.beforeEach
    WireMock.reset
  }
}
