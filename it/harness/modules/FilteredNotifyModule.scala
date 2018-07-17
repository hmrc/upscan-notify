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
