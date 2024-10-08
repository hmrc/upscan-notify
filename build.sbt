import play.sbt.PlayImport.PlayKeys.playDefaultPort
import sbt.Keys.*
import sbt.*
import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings
import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings


val appName = "upscan-notify"

ThisBuild / majorVersion := 0
ThisBuild / scalaVersion := "2.13.12"

lazy val scoverageSettings =
  Seq(
    // Semicolon-separated list of regexs matching classes to exclude
    ScoverageKeys.coverageExcludedPackages := "<empty>;Reverse.*;.*AuthService.*;models/.data/..*;view.*",
    ScoverageKeys.coverageExcludedFiles :=
      ".*/frontendGlobal.*;.*/frontendAppConfig.*;.*/frontendWiring.*;.*/views/.*_template.*;.*/govuk_wrapper.*;.*/routes_routing.*;.*/BuildInfo.*",
    // Minimum is deliberately low to avoid failures initially - please increase as we add more coverage
    ScoverageKeys.coverageMinimumStmtTotal := 25,
    ScoverageKeys.coverageFailOnMinimum := false,
    ScoverageKeys.coverageHighlighting := true
  )

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(scoverageSettings: _*)
  .settings(playDefaultPort := 9573)
  .settings(libraryDependencies ++= AppDependencies())
  .settings(scalacOptions ++= Seq(
   "-Wconf:cat=unused-imports&src=.*routes.*:s" //silence import warnings in routes generated by comments
  ,"-Wconf:cat=unused&src=.*routes.*:s" //silence  private val defaultPrefix in class Routes is never used
  ))
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(Test / parallelExecution := false)

lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test") // the "test->test" allows reusing test code and test dependencies
  .settings(DefaultBuildSettings.itSettings())
  .settings(libraryDependencies ++= AppDependencies.test)
