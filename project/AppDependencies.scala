import sbt._

object AppDependencies {

  private val bootstrapVersion = "5.12.0"

  private val compile = Seq(
    "uk.gov.hmrc"   %% "bootstrap-backend-play-28" % bootstrapVersion,
    "org.typelevel" %% "cats-core"                 % "2.1.1",
    "com.amazonaws" % "aws-java-sdk-s3"            % "1.11.921",
    "com.amazonaws" % "aws-java-sdk-sqs"           % "1.11.921",
    "commons-io"    % "commons-io"                 % "2.8.0",
    "com.sun.mail"  % "javax.mail"                 % "1.6.2"
  )

  private val test = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-28"  % bootstrapVersion % s"$Test,$IntegrationTest",
    "com.vladsch.flexmark"   % "flexmark-all"             % "0.35.10"        % s"$Test,$IntegrationTest",
    "org.mockito"            %% "mockito-scala-scalatest" % "1.15.1"         % s"$Test,$IntegrationTest",
    "com.github.tomakehurst" % "wiremock-standalone"      % "2.27.2"         % s"$Test,$IntegrationTest"
  )

  def apply(): Seq[ModuleID] = compile ++ test
}
