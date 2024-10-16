import sbt._

object AppDependencies {

  private val bootstrapVersion = "9.5.0"

  val compile = Seq(
    "uk.gov.hmrc"   %% "bootstrap-backend-play-30" % bootstrapVersion,
    "org.typelevel" %% "cats-core"                 % "2.12.0",
    "com.amazonaws" %  "aws-java-sdk-s3"           % "1.12.606",
    "com.amazonaws" %  "aws-java-sdk-sqs"          % "1.12.606",
    "commons-io"    %  "commons-io"                % "2.15.1",
    "com.sun.mail"  %  "jakarta.mail"              % "2.0.1"
  )

  val test = Seq(
    "uk.gov.hmrc"   %% "bootstrap-test-play-30"    % bootstrapVersion % Test
  )

  def apply(): Seq[ModuleID] = compile ++ test

}
