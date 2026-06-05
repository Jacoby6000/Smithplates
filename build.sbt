ThisBuild / scalaVersion := "3.3.6"

val testcontainersScalaVersion = "0.44.1"
val catsCoreVersion = "2.12.0"
val catsEffectVersion = "3.7.0"

def catsCoreDependency: ModuleID =
  "org.typelevel" %% "cats-core" % catsCoreVersion

def catsEffectDependency: ModuleID =
  "org.typelevel" %% "cats-effect" % catsEffectVersion

def withCatsEffect: Seq[Def.Setting[_]] =
  Seq(
    libraryDependencies += catsCoreDependency,
    libraryDependencies += catsEffectDependency
  )

val strictScala3Settings: Seq[Def.Setting[_]] = Seq(
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-unchecked",
    "-Wunused:all",
    "-Wvalue-discard",
    "-Werror",
    "-no-indent"
  )
)

/** Dialect IT modules: integration tests live in src/test only (not src/it or IntegrationTest). */
def dialectIntegrationTestModuleSettings: Seq[Def.Setting[_]] = Seq(
  Compile / sources := Nil,
  Compile / resources := Nil,
  publish / skip := true
)

lazy val smithySqlPlugin = (project in file("sql-plugin"))
  .settings(
    strictScala3Settings,
    name := "smithy-stache-plugin",
    organization := "com.jacoby6000",
    version := "0.1.0",
    crossPaths := false,
    libraryDependencies ++= Seq(
      "software.amazon.smithy" % "smithy-build" % "1.71.0",
      "software.amazon.smithy" % "smithy-model" % "1.71.0",
      "software.amazon.smithy" % "smithy-utils" % "1.71.0",
      catsCoreDependency,
      catsEffectDependency,
      "org.scalatra.scalate" % "scalate-core_3" % "1.10.1",
      "org.scalameta" %% "munit" % "1.0.2" % Test
    ),
    Compile / packageDoc / publishArtifact := false,
    publishM2Configuration := publishM2Configuration.value.withOverwrite(true)
  )

lazy val smithySqlPluginCommonIt = (project in file("sql-plugin-common-it"))
  .dependsOn(smithySqlPlugin)
  .settings(
    strictScala3Settings,
    withCatsEffect,
    name := "smithy-sql-plugin-common-it",
    organization := "com.jacoby6000",
    version := "0.1.0",
    crossPaths := false,
    Compile / packageDoc / publishArtifact := false,
    publish / skip := true
  )

lazy val smithySqlPluginPostgresIt = (project in file("sql-plugin-postgres-it"))
  .dependsOn(smithySqlPlugin, smithySqlPluginCommonIt)
  .settings(
    strictScala3Settings,
    withCatsEffect,
    name := "smithy-sql-plugin-postgres-it",
    organization := "com.jacoby6000",
    version := "0.1.0",
    crossPaths := false,
    dialectIntegrationTestModuleSettings,
    Test / parallelExecution := false,
    Test / fork := true,
    libraryDependencies ++= Seq(
      "com.dimafeng" %% "testcontainers-scala-munit" % testcontainersScalaVersion % Test,
      "com.dimafeng" %% "testcontainers-scala-postgresql" % testcontainersScalaVersion % Test,
      "org.postgresql" % "postgresql" % "42.7.4" % Test,
      "org.scalameta" %% "munit" % "1.0.2" % Test
    )
  )

lazy val smithySqlPluginSqliteIt = (project in file("sql-plugin-sqlite-it"))
  .dependsOn(smithySqlPlugin, smithySqlPluginCommonIt)
  .settings(
    strictScala3Settings,
    withCatsEffect,
    name := "smithy-sql-plugin-sqlite-it",
    organization := "com.jacoby6000",
    version := "0.1.0",
    crossPaths := false,
    dialectIntegrationTestModuleSettings,
    Test / parallelExecution := false,
    Test / fork := true,
    libraryDependencies ++= Seq(
      "com.dimafeng" %% "testcontainers-scala" % testcontainersScalaVersion % Test,
      "com.dimafeng" %% "testcontainers-scala-munit" % testcontainersScalaVersion % Test,
      "org.scalameta" %% "munit" % "1.0.2" % Test
    )
  )

lazy val root = (project in file("."))
  .settings(strictScala3Settings, withCatsEffect)
  .aggregate(
    smithySqlPlugin,
    smithySqlPluginCommonIt,
    smithySqlPluginPostgresIt,
    smithySqlPluginSqliteIt
  )
