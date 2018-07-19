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
