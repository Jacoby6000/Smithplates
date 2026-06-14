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

lazy val generateGoldenTemplatesFor = inputKey[Unit](
  "Generate golden template expected outputs. Usage: generateGoldenTemplatesFor <language> <case-name> [<case-name> ...]"
)

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

def pythonTemplateNamespace(feature: String): String =
  s"python/src/$feature"

/** Package each template tree under its own classpath namespace (e.g. python/src/http). */
def pythonNamespacedTemplateResources(features: String*): Seq[Def.Setting[_]] = Seq(
  Compile / resourceGenerators += Def.task {
    val repoRoot     = (ThisBuild / baseDirectory).value
    val resourceRoot = (Compile / resourceManaged).value
    val templateSrc  = repoRoot / "templates" / "python" / "src"

    features.flatMap { feature =>
      val sourceRoot = templateSrc / feature
      (sourceRoot ** "*")
        .filter(_.isFile)
        .get
        .map { source =>
          val relative = source.relativeTo(sourceRoot).get.getPath
          val target   = resourceRoot / pythonTemplateNamespace(feature) / relative
          IO.createDirectories(Seq(target.getParentFile))
          IO.copyFile(source, target, preserveLastModified = true)
          target
        }
    }
  }
)

/** Dialect IT modules: integration tests live in src/test only (not src/it or IntegrationTest). */
def dialectIntegrationTestModuleSettings: Seq[Def.Setting[_]] = Seq(
  Compile / sources := Nil,
  Compile / resources := Nil,
  publish / skip := true
)

lazy val smithplatesSqlIr = (project in file("modules/smithplates-sql-ir"))
  .settings(
    strictScala3Settings,
    unpublishedModuleSettings,
    name := "smithplates-sql-ir",
    organization := "com.jacoby6000",
    version := "0.1.0",
    libraryDependencies ++= smithyModelDependencies :+ catsCoreDependency,
    libraryDependencies += "org.scalameta" %% "munit" % munitVersion % Test
  )

lazy val smithplatesSqlServiceIr = (project in file("modules/smithplates-sql-service-ir"))
  .dependsOn(smithplatesSqlIr, smithplatesSqlIr % "test->test")
  .settings(
    strictScala3Settings,
    unpublishedModuleSettings,
    name := "smithplates-sql-service-ir",
    organization := "com.jacoby6000",
    version := "0.1.0",
    libraryDependencies ++= smithyModelDependencies :+ catsCoreDependency,
    libraryDependencies += "org.scalameta" %% "munit" % munitVersion % Test
  )

lazy val smithplatesSqlDdlRendererCommon = (project in file("modules/smithplates-sql-ddl-renderer-common"))
  .dependsOn(smithplatesSqlIr)
  .settings(
    strictScala3Settings,
    unpublishedModuleSettings,
    name := "smithplates-sql-ddl-renderer-common",
    organization := "com.jacoby6000",
    version := "0.1.0",
    libraryDependencies += catsCoreDependency,
    libraryDependencies += "org.scalameta" %% "munit" % munitVersion % Test
  )

lazy val smithplatesSqlDdlRendererPostgres = (project in file("modules/smithplates-sql-ddl-renderer-postgres"))
  .dependsOn(smithplatesSqlDdlRendererCommon, smithplatesSqlIr % "test->test")
  .settings(
    strictScala3Settings,
    unpublishedModuleSettings,
    name := "smithplates-sql-ddl-renderer-postgres",
    organization := "com.jacoby6000",
    version := "0.1.0",
    libraryDependencies += catsCoreDependency,
    libraryDependencies += "org.scalameta" %% "munit" % munitVersion % Test
  )

lazy val smithplatesSqlDdlRendererSqlite = (project in file("modules/smithplates-sql-ddl-renderer-sqlite"))
  .dependsOn(smithplatesSqlDdlRendererCommon, smithplatesSqlIr % "test->test")
  .settings(
    strictScala3Settings,
    unpublishedModuleSettings,
    name := "smithplates-sql-ddl-renderer-sqlite",
    organization := "com.jacoby6000",
    version := "0.1.0",
    libraryDependencies += catsCoreDependency,
    libraryDependencies += "org.scalameta" %% "munit" % munitVersion % Test
  )

lazy val smithplatesSqlServiceQueryRenderer = (project in file("modules/smithplates-sql-service-query-renderer"))
  .dependsOn(smithplatesSqlIr, smithplatesSqlServiceIr, smithplatesSqlDdlRendererCommon)
  .settings(
    strictScala3Settings,
    unpublishedModuleSettings,
    name := "smithplates-sql-service-query-renderer",
    organization := "com.jacoby6000",
    version := "0.1.0",
    libraryDependencies += catsCoreDependency,
    libraryDependencies += "org.scalameta" %% "munit" % munitVersion % Test
  )

lazy val smithplatesSqlServiceQueryRendererCommon = (project in file(
  "modules/smithplates-sql-service-query-renderer-common"
))
  .dependsOn(smithplatesSqlServiceQueryRenderer, smithplatesSqlIr, smithplatesSqlServiceIr)
  .settings(
    strictScala3Settings,
    unpublishedModuleSettings,
    name := "smithplates-sql-service-query-renderer-common",
    organization := "com.jacoby6000",
    version := "0.1.0",
    libraryDependencies += catsCoreDependency
  )

lazy val smithplatesSqlServiceQueryRendererPostgres = (project in file(
  "modules/smithplates-sql-service-query-renderer-postgres"
))
  .dependsOn(
    smithplatesSqlServiceQueryRenderer,
    smithplatesSqlServiceQueryRendererCommon,
    smithplatesSqlServiceQueryRenderer % "test->test",
    smithplatesSqlIr % "test->test",
    smithplatesSqlServiceIr % "test->test"
  )
  .settings(
    strictScala3Settings,
    unpublishedModuleSettings,
    name := "smithplates-sql-service-query-renderer-postgres",
    organization := "com.jacoby6000",
    version := "0.1.0",
    libraryDependencies += catsCoreDependency,
    libraryDependencies += "org.scalameta" %% "munit" % munitVersion % Test
  )

lazy val smithplatesSqlServiceQueryRendererSqlite = (project in file(
  "modules/smithplates-sql-service-query-renderer-sqlite"
))
  .dependsOn(
    smithplatesSqlServiceQueryRenderer,
    smithplatesSqlServiceQueryRendererCommon,
    smithplatesSqlServiceQueryRenderer % "test->test",
    smithplatesSqlIr % "test->test",
    smithplatesSqlServiceIr % "test->test"
  )
  .settings(
    strictScala3Settings,
    unpublishedModuleSettings,
    name := "smithplates-sql-service-query-renderer-sqlite",
    organization := "com.jacoby6000",
    version := "0.1.0",
    libraryDependencies += catsCoreDependency,
    libraryDependencies += "org.scalameta" %% "munit" % munitVersion % Test
  )

lazy val smithplatesHttpIr = (project in file("modules/smithplates-http-ir"))
  .settings(
    strictScala3Settings,
    unpublishedModuleSettings,
    name := "smithplates-http-ir",
    organization := "com.jacoby6000",
    version := "0.1.0",
    libraryDependencies ++= smithyModelDependencies :+ catsCoreDependency,
    libraryDependencies += "software.amazon.smithy" % "smithy-build" % smithyVersion,
    libraryDependencies += "org.scalameta" %% "munit" % munitVersion % Test
  )

lazy val smithplatesHttpServiceRenderer = (project in file("modules/smithplates-http-service-renderer"))
  .dependsOn(smithplatesHttpIr, smithplatesHttpIr % "test->test")
  .settings(
    strictScala3Settings,
    unpublishedModuleSettings,
    name := "smithplates-http-service-renderer",
    organization := "com.jacoby6000",
    version := "0.1.0",
    libraryDependencies ++= Seq(
      catsCoreDependency,
      "org.scalatra.scalate" % "scalate-core_3" % scalateVersion,
      "org.scalameta" %% "munit" % munitVersion % Test
    ),
    pythonNamespacedTemplateResources("common", "http"),
    Test / unmanagedResourceDirectories ++= Seq(
      (ThisBuild / baseDirectory).value / "templates"
    )
  )

lazy val smithplatesSqlServiceRenderer = (project in file("modules/smithplates-sql-service-renderer"))
  .dependsOn(
    smithplatesSqlServiceIr,
    smithplatesSqlServiceQueryRenderer,
    smithplatesSqlDdlRendererPostgres % "test->compile",
    smithplatesSqlDdlRendererSqlite % "test->compile",
    smithplatesSqlServiceQueryRendererPostgres % "test->compile",
    smithplatesSqlServiceQueryRendererSqlite % "test->compile",
    smithplatesSqlIr % "test->test",
    smithplatesSqlServiceIr % "test->test"
  )
  .settings(
    strictScala3Settings,
    unpublishedModuleSettings,
    name := "smithplates-sql-service-renderer",
    organization := "com.jacoby6000",
    version := "0.1.0",
    libraryDependencies ++= Seq(
      catsCoreDependency,
      "org.scalatra.scalate" % "scalate-core_3" % scalateVersion,
      "org.scalameta" %% "munit" % munitVersion % Test
    ),
    pythonNamespacedTemplateResources("common", "db"),
    Test / unmanagedResourceDirectories ++= Seq(
      (ThisBuild / baseDirectory).value / "templates"
    )
  )

lazy val smithplatesPlugin = (project in file("modules/smithplates-plugin"))
  .dependsOn(
    smithplatesSqlIr,
    smithplatesSqlServiceIr,
    smithplatesSqlDdlRendererPostgres,
    smithplatesSqlDdlRendererSqlite,
    smithplatesSqlServiceQueryRenderer,
    smithplatesSqlServiceQueryRendererPostgres,
    smithplatesSqlServiceQueryRendererSqlite,
    smithplatesSqlServiceRenderer,
    smithplatesSqlServiceRenderer % "test->test",
    smithplatesHttpIr,
    smithplatesHttpServiceRenderer,
    smithplatesHttpServiceRenderer % "test->test"
  )
  .settings(
    strictScala3Settings,
    name := "smithplates-plugin",
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
    publishM2Configuration := publishM2Configuration.value.withOverwrite(true),
    Test / unmanagedResourceDirectories += (ThisBuild / baseDirectory).value / "templates",
    generateGoldenTemplatesFor := {
      import sbt.internal.util.complete.DefaultParsers.*
      Def.inputTaskDyn {
        val args = spaceDelimited("<arg> *").parsed
        if (args.isEmpty) {
          throw new IllegalArgumentException(
            "Usage: generateGoldenTemplatesFor <language> <case-name> [<case-name> ...]"
          )
        }
        (Test / runMain).toTask(
          s" com.jacoby6000.smithplates.plugin.generators.SmithplatesGenerators golden-templates ${args.mkString(" ")}"
        )
      }.evaluated
    }
  )

lazy val smithplatesTestkit = (project in file("modules/smithplates-testkit"))
  .dependsOn(smithplatesSqlIr, smithplatesSqlServiceIr, smithplatesSqlDdlRendererCommon)
  .settings(
    strictScala3Settings,
    withCatsEffect,
    unpublishedModuleSettings,
    name := "smithplates-testkit",
    organization := "com.jacoby6000",
    version := "0.1.0"
  )

lazy val smithplatesSqlDdlRendererPostgresIt = (project in file("modules/smithplates-sql-ddl-renderer-postgres-it"))
  .dependsOn(smithplatesSqlDdlRendererPostgres, smithplatesTestkit)
  .settings(
    strictScala3Settings,
    withCatsEffect,
    unpublishedModuleSettings,
    name := "smithplates-sql-ddl-renderer-postgres-it",
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

lazy val smithplatesSqlDdlRendererSqliteIt = (project in file("modules/smithplates-sql-ddl-renderer-sqlite-it"))
  .dependsOn(smithplatesSqlDdlRendererSqlite, smithplatesTestkit)
  .settings(
    strictScala3Settings,
    withCatsEffect,
    unpublishedModuleSettings,
    name := "smithplates-sql-ddl-renderer-sqlite-it",
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
    Compile / resources := Nil,
    generateGoldenTemplatesFor := (smithplatesPlugin / generateGoldenTemplatesFor).evaluated
  )
  .aggregate(
    smithplatesSqlIr,
    smithplatesHttpIr,
    smithplatesSqlDdlRendererCommon,
    smithplatesSqlServiceIr,
    smithplatesSqlDdlRendererPostgres,
    smithplatesSqlDdlRendererSqlite,
    smithplatesSqlServiceQueryRenderer,
    smithplatesSqlServiceQueryRendererCommon,
    smithplatesSqlServiceQueryRendererPostgres,
    smithplatesSqlServiceQueryRendererSqlite,
    smithplatesSqlServiceRenderer,
    smithplatesHttpServiceRenderer,
    smithplatesPlugin,
    smithplatesTestkit,
    smithplatesSqlDdlRendererPostgresIt,
    smithplatesSqlDdlRendererSqliteIt
  )
