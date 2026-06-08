ThisBuild / scalaVersion := "3.3.6"
ThisBuild / scalafmtOnCompile := false
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision
ThisBuild / scalafixConfig := Some(file(".scalafix.conf"))

val testcontainersScalaVersion = "0.44.1"
val catsCoreVersion = "2.12.0"
val catsEffectVersion = "3.7.0"
val smithyVersion = "1.71.0"
val munitVersion = "1.0.2"
val scalateVersion = "1.10.1"

def catsCoreDependency: ModuleID =
  "org.typelevel" %% "cats-core" % catsCoreVersion

def catsEffectDependency: ModuleID =
  "org.typelevel" %% "cats-effect" % catsEffectVersion

def smithyModelDependencies: Seq[ModuleID] =
  Seq(
    "software.amazon.smithy" % "smithy-model" % smithyVersion,
    "software.amazon.smithy" % "smithy-utils" % smithyVersion
  )

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

def unpublishedModuleSettings: Seq[Def.Setting[_]] = Seq(
  publish / skip := true,
  crossPaths := false,
  Compile / packageDoc / publishArtifact := false
)

/** Dialect IT modules: integration tests live in src/test only (not src/it or IntegrationTest). */
def dialectIntegrationTestModuleSettings: Seq[Def.Setting[_]] = Seq(
  Compile / sources := Nil,
  Compile / resources := Nil,
  publish / skip := true
)

lazy val smithySqlIr = (project in file("modules/smithy-sql-ir"))
  .settings(
    strictScala3Settings,
    unpublishedModuleSettings,
    name := "smithy-sql-ir",
    organization := "com.jacoby6000",
    version := "0.1.0",
    libraryDependencies ++= smithyModelDependencies :+ catsCoreDependency,
    libraryDependencies += "org.scalameta" %% "munit" % munitVersion % Test
  )

lazy val smithySqlServiceIr = (project in file("modules/smithy-sql-service-ir"))
  .dependsOn(smithySqlIr, smithySqlIr % "test->test")
  .settings(
    strictScala3Settings,
    unpublishedModuleSettings,
    name := "smithy-sql-service-ir",
    organization := "com.jacoby6000",
    version := "0.1.0",
    libraryDependencies ++= smithyModelDependencies :+ catsCoreDependency,
    libraryDependencies += "org.scalameta" %% "munit" % munitVersion % Test
  )

lazy val smithySqlPostgresRenderer = (project in file("modules/smithy-sql-postgres-renderer"))
  .dependsOn(smithySqlIr, smithySqlIr % "test->test")
  .settings(
    strictScala3Settings,
    unpublishedModuleSettings,
    name := "smithy-sql-postgres-renderer",
    organization := "com.jacoby6000",
    version := "0.1.0",
    libraryDependencies += catsCoreDependency,
    libraryDependencies += "org.scalameta" %% "munit" % munitVersion % Test
  )

lazy val smithySqlSqliteRenderer = (project in file("modules/smithy-sql-sqlite-renderer"))
  .dependsOn(smithySqlIr, smithySqlIr % "test->test")
  .settings(
    strictScala3Settings,
    unpublishedModuleSettings,
    name := "smithy-sql-sqlite-renderer",
    organization := "com.jacoby6000",
    version := "0.1.0",
    libraryDependencies += catsCoreDependency,
    libraryDependencies += "org.scalameta" %% "munit" % munitVersion % Test
  )

lazy val smithySqlServiceQueryRenderer = (project in file("modules/smithy-sql-service-query-renderer"))
  .dependsOn(smithySqlIr, smithySqlServiceIr)
  .settings(
    strictScala3Settings,
    unpublishedModuleSettings,
    name := "smithy-sql-service-query-renderer",
    organization := "com.jacoby6000",
    version := "0.1.0",
    libraryDependencies += catsCoreDependency,
    libraryDependencies += "org.scalameta" %% "munit" % munitVersion % Test
  )

lazy val smithySqlServiceQueryRendererPostgres = (project in file(
  "modules/smithy-sql-service-query-renderer-postgres"
))
  .dependsOn(smithySqlServiceQueryRenderer, smithySqlServiceQueryRenderer % "test->test", smithySqlIr % "test->test", smithySqlServiceIr % "test->test")
  .settings(
    strictScala3Settings,
    unpublishedModuleSettings,
    name := "smithy-sql-service-query-renderer-postgres",
    organization := "com.jacoby6000",
    version := "0.1.0",
    libraryDependencies += catsCoreDependency,
    libraryDependencies += "org.scalameta" %% "munit" % munitVersion % Test
  )

lazy val smithySqlServiceQueryRendererSqlite = (project in file(
  "modules/smithy-sql-service-query-renderer-sqlite"
))
  .dependsOn(smithySqlServiceQueryRenderer, smithySqlServiceQueryRenderer % "test->test", smithySqlIr % "test->test", smithySqlServiceIr % "test->test")
  .settings(
    strictScala3Settings,
    unpublishedModuleSettings,
    name := "smithy-sql-service-query-renderer-sqlite",
    organization := "com.jacoby6000",
    version := "0.1.0",
    libraryDependencies += catsCoreDependency,
    libraryDependencies += "org.scalameta" %% "munit" % munitVersion % Test
  )

lazy val smithySqlServiceRenderer = (project in file("modules/smithy-sql-service-renderer"))
  .dependsOn(
    smithySqlServiceIr,
    smithySqlServiceQueryRenderer,
    smithySqlPostgresRenderer % "test->compile",
    smithySqlSqliteRenderer % "test->compile",
    smithySqlServiceQueryRendererPostgres % "test->compile",
    smithySqlServiceQueryRendererSqlite % "test->compile",
    smithySqlIr % "test->test",
    smithySqlServiceIr % "test->test"
  )
  .settings(
    strictScala3Settings,
    unpublishedModuleSettings,
    name := "smithy-sql-service-renderer",
    organization := "com.jacoby6000",
    version := "0.1.0",
    libraryDependencies ++= Seq(
      catsCoreDependency,
      "org.scalatra.scalate" % "scalate-core_3" % scalateVersion,
      "org.scalameta" %% "munit" % munitVersion % Test
    ),
    Compile / unmanagedResourceDirectories +=
      (ThisBuild / baseDirectory).value / "language-templates" / "python" / "src" / "db",
    Test / unmanagedResourceDirectories ++= Seq(
      (ThisBuild / baseDirectory).value / "language-templates"
    )
  )

lazy val smithyStachePlugin = (project in file("modules/smithy-stache-plugin"))
  .dependsOn(
    smithySqlIr,
    smithySqlServiceIr,
    smithySqlPostgresRenderer,
    smithySqlSqliteRenderer,
    smithySqlServiceQueryRenderer,
    smithySqlServiceQueryRendererPostgres,
    smithySqlServiceQueryRendererSqlite,
    smithySqlServiceRenderer
  )
  .settings(
    strictScala3Settings,
    name := "smithy-stache-plugin",
    organization := "com.jacoby6000",
    version := "0.1.0",
    crossPaths := false,
    libraryDependencies ++= Seq(
      "software.amazon.smithy" % "smithy-build" % smithyVersion,
      catsCoreDependency,
      catsEffectDependency,
      "org.scalameta" %% "munit" % munitVersion % Test
    ),
    Compile / packageDoc / publishArtifact := false,
    publishM2Configuration := publishM2Configuration.value.withOverwrite(true)
  )

lazy val smithyStacheTestkit = (project in file("modules/smithy-stache-testkit"))
  .dependsOn(smithySqlIr, smithySqlServiceIr)
  .settings(
    strictScala3Settings,
    withCatsEffect,
    unpublishedModuleSettings,
    name := "smithy-stache-testkit",
    organization := "com.jacoby6000",
    version := "0.1.0"
  )

lazy val smithySqlPostgresRendererIt = (project in file("modules/smithy-sql-postgres-renderer-it"))
  .dependsOn(smithySqlPostgresRenderer, smithyStacheTestkit)
  .settings(
    strictScala3Settings,
    withCatsEffect,
    unpublishedModuleSettings,
    name := "smithy-sql-postgres-renderer-it",
    organization := "com.jacoby6000",
    version := "0.1.0",
    dialectIntegrationTestModuleSettings,
    Test / parallelExecution := false,
    Test / fork := true,
    libraryDependencies ++= Seq(
      "com.dimafeng" %% "testcontainers-scala-munit" % testcontainersScalaVersion % Test,
      "com.dimafeng" %% "testcontainers-scala-postgresql" % testcontainersScalaVersion % Test,
      "org.postgresql" % "postgresql" % "42.7.4" % Test,
      "org.scalameta" %% "munit" % munitVersion % Test
    )
  )

lazy val smithySqlSqliteRendererIt = (project in file("modules/smithy-sql-sqlite-renderer-it"))
  .dependsOn(smithySqlSqliteRenderer, smithyStacheTestkit)
  .settings(
    strictScala3Settings,
    withCatsEffect,
    unpublishedModuleSettings,
    name := "smithy-sql-sqlite-renderer-it",
    organization := "com.jacoby6000",
    version := "0.1.0",
    dialectIntegrationTestModuleSettings,
    Test / parallelExecution := false,
    Test / fork := true,
    libraryDependencies ++= Seq(
      "com.dimafeng" %% "testcontainers-scala" % testcontainersScalaVersion % Test,
      "com.dimafeng" %% "testcontainers-scala-munit" % testcontainersScalaVersion % Test,
      "org.scalameta" %% "munit" % munitVersion % Test
    )
  )

lazy val root = (project in file("."))
  .settings(
    strictScala3Settings,
    withCatsEffect,
    Compile / sources := Nil,
    Compile / resources := Nil
  )
  .aggregate(
    smithySqlIr,
    smithySqlServiceIr,
    smithySqlPostgresRenderer,
    smithySqlSqliteRenderer,
    smithySqlServiceQueryRenderer,
    smithySqlServiceQueryRendererPostgres,
    smithySqlServiceQueryRendererSqlite,
    smithySqlServiceRenderer,
    smithyStachePlugin,
    smithyStacheTestkit,
    smithySqlPostgresRendererIt,
    smithySqlSqliteRendererIt
  )
