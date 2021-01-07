import play.sbt.PlayImport.ws
import sbt._

object AppDependencies {

  private val compile = Seq(
    ws,                                // needed to satisfy dependencies of uk.gov.hmrc.play.bootstrap.HttpClientModule
    "uk.gov.hmrc"       %% "bootstrap-backend-play-27" % "3.2.0",
    "org.typelevel"     %% "cats-core"                 % "2.1.1",
    "com.amazonaws"      % "aws-java-sdk-s3"           % "1.11.921",
    "com.amazonaws"      % "aws-java-sdk-sqs"          % "1.11.921",
    "commons-io"         % "commons-io"                % "2.8.0"
  )

  private val test = Seq(
    "org.scalatest"          %% "scalatest"                   % "3.1.4"             % s"$Test,$IntegrationTest",
    "org.scalatestplus.play" %% "scalatestplus-play"          % "4.0.3"             % s"$Test,$IntegrationTest",
    "com.vladsch.flexmark"    % "flexmark-all"                % "0.35.10"           % s"$Test,$IntegrationTest",
    "org.mockito"            %% "mockito-scala-scalatest"     % "1.15.1"            % s"$Test,$IntegrationTest",
    "com.github.tomakehurst"  % "wiremock-jre8"               % "2.27.2"            % s"$Test,$IntegrationTest"
  )

  def apply(): Seq[ModuleID] = compile ++ test
}