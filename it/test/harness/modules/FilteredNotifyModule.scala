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

package harness.modules

import modules.NotifyModule
import play.api.inject.Binding
import play.api.{Configuration, Environment}
import services.ContinuousPoller

/**
  * Testing subclass of NotifyModule that disables components that should not be run during integration testing.
  */
class FilteredNotifyModule extends NotifyModule {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] =
    super.bindings(environment, configuration)
         .filterNot(_.key == bind[ContinuousPoller])    // We don't want the normal poller to be invoked in the background
}
