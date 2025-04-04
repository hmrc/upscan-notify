import sbt._

object AppDependencies {

  private val bootstrapVersion = "9.10.0"

  val compile = Seq(
    "uk.gov.hmrc"            %% "bootstrap-backend-play-30" % bootstrapVersion,
    "org.typelevel"          %% "cats-core"                 % "2.13.0",
    "software.amazon.awssdk" %  "sqs"                       % "2.30.30",
    "software.amazon.awssdk" %  "s3"                        % "2.30.30",
    "software.amazon.awssdk" %  "secretsmanager"            % "2.30.30",
    "jakarta.mail"           %  "jakarta.mail-api"          % "2.1.3",
    "org.eclipse.angus"      %  "jakarta.mail"              % "2.0.3"
  )

  val test = Seq(
    "uk.gov.hmrc"   %% "bootstrap-test-play-30"    % bootstrapVersion % Test
  )

  def apply(): Seq[ModuleID] = compile ++ test

}
