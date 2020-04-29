import sbt._

object AppDependencies {

  private val compile = Seq(
    "uk.gov.hmrc"       %% "bootstrap-backend-play-27" % "2.4.0",
    "org.typelevel"     %% "cats-core"                 % "2.1.1",
    "com.amazonaws"      % "aws-java-sdk-s3"           % "1.11.769",
    "com.amazonaws"      % "aws-java-sdk-sqs"          % "1.11.769",
    "commons-io"         % "commons-io"                % "2.6"
  )

  private val test = Seq(
    "uk.gov.hmrc"            %% "hmrctest"                    % "3.9.0-play-26"     % s"$Test,$IntegrationTest",
    "org.scalatest"          %% "scalatest"                   % "3.0.8"             % s"$Test,$IntegrationTest",
    "org.scalatestplus.play" %% "scalatestplus-play"          % "4.0.3"             % s"$Test,$IntegrationTest",
    "org.pegdown"             % "pegdown"                     % "1.6.0"             % s"$Test,$IntegrationTest",
    "org.mockito"             % "mockito-core"                % "3.3.3"             % s"$Test,$IntegrationTest",
    "com.github.tomakehurst"  % "wiremock-jre8"               % "2.26.3"            % s"$Test,$IntegrationTest"
  )

  def apply(): Seq[ModuleID] = compile ++ test
}