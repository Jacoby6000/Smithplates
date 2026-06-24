import sbt.*

inThisBuild(
  List(
    organization := "com.jacoby6000",
    homepage := Some(url("https://github.com/Jacoby6000/Smithplates")),
    licenses := List("MIT" -> url("https://opensource.org/licenses/MIT")),
    developers := List(
      Developer(
        "Jacoby6000",
        "Jacob Barber",
        "",
        url("https://github.com/Jacoby6000")
      )
    ),
    versionScheme := Some("early-semver"),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/Jacoby6000/Smithplates"),
        "scm:git:git@github.com:Jacoby6000/Smithplates.git"
      )
    )
  )
)

ThisBuild / scalaVersion := "3.3.6"
ThisBuild / scalafmtOnCompile := false
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision
ThisBuild / scalafixConfig := Some(file(".scalafix.conf"))

val testcontainersScalaVersion = "0.44.1"
val catsCoreVersion = "2.12.0"
val catsEffectVersion = "3.7.0"
val kittensVersion = "3.5.0"
val smithyVersion = "1.71.0"
val munitVersion = "1.0.2"
val munitScalacheckVersion = "1.0.0"
val log4jVersion = "2.24.3"
val scalateVersion = "1.10.1"

lazy val generateGoldenTemplatesFor = inputKey[Unit](
  "Generate golden template expected outputs. Usage: generateGoldenTemplatesFor <language> <case-name> [<case-name> ...]"
)

def catsCoreDependency: ModuleID =
  "org.typelevel" %% "cats-core" % catsCoreVersion

def kittensDependency: ModuleID =
  "org.typelevel" %% "kittens" % kittensVersion

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
    "-no-indent",
    "-release:17"
  ),
  javacOptions ++= Seq("--release", "17")
)

def unpublishedModuleSettings: Seq[Def.Setting[_]] = Seq(
  publish / skip := true,
  publishArtifact := false,
  crossPaths := false,
  Compile / packageBin / publishArtifact := false,
  Compile / packageSrc / publishArtifact := false,
  Compile / packageDoc / publishArtifact := false,
  Test / packageBin / publishArtifact := false,
  Test / packageSrc / publishArtifact := false,
  Test / packageDoc / publishArtifact := false
)

/**
 * Published dependency modules of `smithplates-plugin`. The plugin pom references these by Maven coordinate, so every
 * module in the plugin's transitive compile graph (including the renderer jars carrying precompiled SSP template
 * classes) must be published with the same version as the plugin (`sbt-dynver` via `sbt-ci-release`).
 */
def publishedModuleSettings: Seq[Def.Setting[_]] = Seq(
  crossPaths := false,
  Compile / packageDoc / publishArtifact := true,
  Test / packageDoc / publishArtifact := false,
  publishM2Configuration := publishM2Configuration.value.withOverwrite(true)
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

val precompiledTemplateClasses = taskKey[Seq[(File, String)]](
  "Ahead-of-time compile bundled SSP templates into JVM classes packaged into the published renderer jar."
)

def namespaceDirectory(base: File, namespace: String): File =
  namespace.split("/").filter(_.nonEmpty).foldLeft(base)(_ / _)

/**
 * Precompiles bundled Scalate SSP templates to JVM classes at build time and packages them into the renderer jar so
 * the published plugin loads precompiled templates at `smithy build` time instead of invoking the Scala compiler.
 *
 * The precompiler runs in a forked JVM on the module classpath (compiled classes + bundled template resources +
 * dependencies) and its output classes are added to `packageBin` mappings only, leaving the default compile/test flow
 * unchanged. The task is cached on the bundled template sources and the module's compiled classes.
 */
def scalateTemplatePrecompileSettings(
    precompileMainClass: String,
    templateNamespaces: Seq[String]
): Seq[Def.Setting[_]] = Seq(
  precompiledTemplateClasses := {
    val log             = streams.value.log
    val classDir        = (Compile / classDirectory).value
    val dependencyFiles = (Compile / dependencyClasspath).value.files
    val precompiledBase = target.value / "scalate-precompiled"
    val outputDir       = precompiledBase / "classes"
    val generatedSrcDir = precompiledBase / "src"
    val cacheBaseDir    = streams.value.cacheDirectory / "scalate-precompiled"
    // Force module classes and bundled template resources onto disk/classpath before precompiling.
    val _compiled       = (Compile / compile).value
    val _copiedRes      = (Compile / copyResources).value

    val templateSourceFiles =
      templateNamespaces.flatMap(namespace => (namespaceDirectory(classDir, namespace) ** "*.ssp").get)
    val moduleClassFiles    = (classDir ** "*.class").get
    val cacheInputs         = (templateSourceFiles ++ moduleClassFiles).toSet

    val runPrecompile = FileFunction.cached(cacheBaseDir, FilesInfo.hash) { _ =>
      IO.delete(outputDir)
      IO.delete(generatedSrcDir)
      IO.createDirectory(outputDir)
      IO.createDirectory(generatedSrcDir)
      val arguments    =
        Seq(outputDir.getAbsolutePath, generatedSrcDir.getAbsolutePath) ++
          templateNamespaces.flatMap(namespace =>
            Seq(namespace, namespaceDirectory(classDir, namespace).getAbsolutePath)
          )
      val runClasspath = classDir +: dependencyFiles
      log.info(s"Precompiling bundled SSP templates via $precompileMainClass")
      new ForkRun(ForkOptions()).run(precompileMainClass, runClasspath, arguments, log).get
      (outputDir ** "*.class").get.toSet
    }

    runPrecompile(cacheInputs).toSeq.map { classFile =>
      classFile -> outputDir.toPath.relativize(classFile.toPath).toString.replace('\\', '/')
    }
  },
  Compile / packageBin / mappings ++= precompiledTemplateClasses.value,
  // Make the module's own tests exercise the precompiled classes (production behavior) instead of compiling templates
  // at runtime. This keeps test output free of Scalate's runtime-compiler diagnostics and validates the precompiled
  // artifacts end to end.
  Test / unmanagedClasspath += {
    val _ = precompiledTemplateClasses.value
    Attributed.blank(target.value / "scalate-precompiled" / "classes")
  }
)

/** Dialect IT modules: integration tests live in src/test only (not src/it or IntegrationTest). */
def dialectIntegrationTestModuleSettings: Seq[Def.Setting[_]] = Seq(
  Compile / sources := Nil,
  Compile / resources := Nil,
  publish / skip := true
)

lazy val smithplatesCodegenCore = (project in file("modules/smithplates-codegen-core"))
  .settings(
    strictScala3Settings,
    publishedModuleSettings,
    name := "smithplates-codegen-core",
    organization := "com.jacoby6000",
    libraryDependencies ++= Seq(
      catsCoreDependency,
      kittensDependency,
      "org.scalameta" %% "munit"            % munitVersion           % Test,
      "org.scalameta" %% "munit-scalacheck" % munitScalacheckVersion % Test
    )
  )

lazy val smithplatesSmithyNeutral = (project in file("modules/smithplates-smithy-neutral"))
  .dependsOn(smithplatesCodegenCore, smithplatesCodegenCore % "test->test")
  .settings(
    strictScala3Settings,
    publishedModuleSettings,
    name := "smithplates-smithy-neutral",
    organization := "com.jacoby6000",
    libraryDependencies ++= smithyModelDependencies :+ catsCoreDependency,
    libraryDependencies += "org.scalameta" %% "munit" % munitVersion % Test
  )

lazy val smithplatesSqlIr = (project in file("modules/smithplates-sql-ir"))
  .settings(
    strictScala3Settings,
    publishedModuleSettings,
    name := "smithplates-sql-ir",
    organization := "com.jacoby6000",
    libraryDependencies ++= smithyModelDependencies :+ catsCoreDependency,
    libraryDependencies += "org.scalameta" %% "munit" % munitVersion % Test
  )

lazy val smithplatesSqlServiceIr = (project in file("modules/smithplates-sql-service-ir"))
  .dependsOn(
    smithplatesSqlIr,
    smithplatesSqlIr % "test->test",
    smithplatesCodegenCore,
    smithplatesSmithyNeutral,
    smithplatesSmithyNeutral % "test->test"
  )
  .settings(
    strictScala3Settings,
    publishedModuleSettings,
    name := "smithplates-sql-service-ir",
    organization := "com.jacoby6000",
    libraryDependencies ++= smithyModelDependencies :+ catsCoreDependency,
    libraryDependencies += "org.scalameta" %% "munit" % munitVersion % Test
  )

lazy val smithplatesSqlDdlRendererCommon = (project in file("modules/smithplates-sql-ddl-renderer-common"))
  .dependsOn(smithplatesSqlIr)
  .settings(
    strictScala3Settings,
    publishedModuleSettings,
    name := "smithplates-sql-ddl-renderer-common",
    organization := "com.jacoby6000",
    libraryDependencies += catsCoreDependency,
    libraryDependencies += "org.scalameta" %% "munit" % munitVersion % Test
  )

lazy val smithplatesSqlDdlRendererPostgres = (project in file("modules/smithplates-sql-ddl-renderer-postgres"))
  .dependsOn(smithplatesSqlDdlRendererCommon, smithplatesSqlIr % "test->test")
  .settings(
    strictScala3Settings,
    publishedModuleSettings,
    name := "smithplates-sql-ddl-renderer-postgres",
    organization := "com.jacoby6000",
    libraryDependencies += catsCoreDependency,
    libraryDependencies += "org.scalameta" %% "munit" % munitVersion % Test
  )

lazy val smithplatesSqlDdlRendererSqlite = (project in file("modules/smithplates-sql-ddl-renderer-sqlite"))
  .dependsOn(smithplatesSqlDdlRendererCommon, smithplatesSqlIr % "test->test")
  .settings(
    strictScala3Settings,
    publishedModuleSettings,
    name := "smithplates-sql-ddl-renderer-sqlite",
    organization := "com.jacoby6000",
    libraryDependencies += catsCoreDependency,
    libraryDependencies += "org.scalameta" %% "munit" % munitVersion % Test
  )

lazy val smithplatesSqlServiceQueryRenderer = (project in file("modules/smithplates-sql-service-query-renderer"))
  .dependsOn(smithplatesSqlIr, smithplatesSqlServiceIr, smithplatesSqlDdlRendererCommon)
  .settings(
    strictScala3Settings,
    publishedModuleSettings,
    name := "smithplates-sql-service-query-renderer",
    organization := "com.jacoby6000",
    libraryDependencies += catsCoreDependency,
    libraryDependencies += "org.scalameta" %% "munit" % munitVersion % Test
  )

lazy val smithplatesSqlServiceQueryRendererCommon = (project in file(
  "modules/smithplates-sql-service-query-renderer-common"
))
  .dependsOn(smithplatesSqlServiceQueryRenderer, smithplatesSqlIr, smithplatesSqlServiceIr)
  .settings(
    strictScala3Settings,
    publishedModuleSettings,
    name := "smithplates-sql-service-query-renderer-common",
    organization := "com.jacoby6000",
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
    publishedModuleSettings,
    name := "smithplates-sql-service-query-renderer-postgres",
    organization := "com.jacoby6000",
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
    publishedModuleSettings,
    name := "smithplates-sql-service-query-renderer-sqlite",
    organization := "com.jacoby6000",
    libraryDependencies += catsCoreDependency,
    libraryDependencies += "org.scalameta" %% "munit" % munitVersion % Test
  )

lazy val smithplatesScalatePrecompiler = (project in file("modules/smithplates-scalate-precompiler"))
  .settings(
    strictScala3Settings,
    publishedModuleSettings,
    name := "smithplates-scalate-precompiler",
    organization := "com.jacoby6000",
    libraryDependencies += "org.scalatra.scalate" % "scalate-core_3" % scalateVersion,
    libraryDependencies += "org.scalameta" %% "munit" % munitVersion % Test
  )

lazy val smithplatesHttpIr = (project in file("modules/smithplates-http-ir"))
  .dependsOn(smithplatesSmithyNeutral, smithplatesSmithyNeutral % "test->test")
  .settings(
    strictScala3Settings,
    publishedModuleSettings,
    name := "smithplates-http-ir",
    organization := "com.jacoby6000",
    libraryDependencies ++= smithyModelDependencies :+ catsCoreDependency,
    libraryDependencies += "software.amazon.smithy" % "smithy-build" % smithyVersion,
    libraryDependencies += "software.amazon.smithy" % "smithy-aws-traits" % smithyVersion,
    libraryDependencies += "org.scalameta" %% "munit" % munitVersion % Test
  )

lazy val smithplatesHttpServiceRenderer = (project in file("modules/smithplates-http-service-renderer"))
  .dependsOn(smithplatesHttpIr, smithplatesScalatePrecompiler, smithplatesHttpIr % "test->test")
  .settings(
    strictScala3Settings,
    publishedModuleSettings,
    name := "smithplates-http-service-renderer",
    organization := "com.jacoby6000",
    libraryDependencies ++= Seq(
      catsCoreDependency,
      "org.scalatra.scalate" % "scalate-core_3" % scalateVersion,
      "org.scalameta" %% "munit" % munitVersion % Test
    ),
    pythonNamespacedTemplateResources("common", "http"),
    scalateTemplatePrecompileSettings(
      "com.jacoby6000.smithplates.http.service.renderer.HttpTemplatePrecompilerMain",
      Seq("python/src/http/server", "python/src/http/client", "python/src/http/models")
    ),
    Test / unmanagedResourceDirectories ++= Seq(
      (ThisBuild / baseDirectory).value / "templates"
    )
  )

lazy val smithplatesSqlServiceRenderer = (project in file("modules/smithplates-sql-service-renderer"))
  .dependsOn(
    smithplatesSqlServiceIr,
    smithplatesSqlServiceQueryRenderer,
    smithplatesScalatePrecompiler,
    smithplatesSqlDdlRendererPostgres % "test->compile",
    smithplatesSqlDdlRendererSqlite % "test->compile",
    smithplatesSqlServiceQueryRendererPostgres % "test->compile",
    smithplatesSqlServiceQueryRendererSqlite % "test->compile",
    smithplatesSqlIr % "test->test",
    smithplatesSqlServiceIr % "test->test"
  )
  .settings(
    strictScala3Settings,
    publishedModuleSettings,
    name := "smithplates-sql-service-renderer",
    organization := "com.jacoby6000",
    libraryDependencies ++= Seq(
      catsCoreDependency,
      "org.scalatra.scalate" % "scalate-core_3" % scalateVersion,
      "org.scalameta" %% "munit" % munitVersion % Test
    ),
    pythonNamespacedTemplateResources("common", "db"),
    scalateTemplatePrecompileSettings(
      "com.jacoby6000.smithplates.sql.service.renderer.SqlTemplatePrecompilerMain",
      Seq("python/src/db")
    ),
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
    crossPaths := false,
    libraryDependencies ++= Seq(
      "software.amazon.smithy" % "smithy-build" % smithyVersion,
      catsCoreDependency,
      catsEffectDependency,
      "org.scalameta" %% "munit" % munitVersion % Test,
      "org.apache.logging.log4j" % "log4j-api" % log4jVersion % Test,
      "org.apache.logging.log4j" % "log4j-core" % log4jVersion % Test,
      "software.amazon.smithy" % "smithy-openapi" % smithyVersion % Test
    ),
    Test / logBuffered := false,
    Compile / packageDoc / publishArtifact := true,
    Test / packageDoc / publishArtifact := false,
    publishM2Configuration := publishM2Configuration.value.withOverwrite(true),
    Test / unmanagedResourceDirectories += (ThisBuild / baseDirectory).value / "templates",
    // The codegen golden suites render bundled templates through the renderer engines. Put each renderer's precompiled
    // template classes on the test classpath so those renders load precompiled classes instead of compiling templates
    // at runtime (avoiding Scalate runtime-compiler diagnostics and exercising the published behavior).
    Test / unmanagedClasspath ++= {
      val _ = (
        (smithplatesSqlServiceRenderer / precompiledTemplateClasses).value,
        (smithplatesHttpServiceRenderer / precompiledTemplateClasses).value
      )
      Seq(
        Attributed.blank((smithplatesSqlServiceRenderer / target).value / "scalate-precompiled" / "classes"),
        Attributed.blank((smithplatesHttpServiceRenderer / target).value / "scalate-precompiled" / "classes")
      )
    },
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
    organization := "com.jacoby6000"
  )

lazy val smithplatesSqlDdlRendererPostgresIt = (project in file("modules/smithplates-sql-ddl-renderer-postgres-it"))
  .dependsOn(smithplatesSqlDdlRendererPostgres, smithplatesTestkit)
  .settings(
    strictScala3Settings,
    withCatsEffect,
    unpublishedModuleSettings,
    name := "smithplates-sql-ddl-renderer-postgres-it",
    organization := "com.jacoby6000",
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
    unpublishedModuleSettings,
    name := "smithplates",
    Compile / sources := Nil,
    Compile / resources := Nil,
    generateGoldenTemplatesFor := (smithplatesPlugin / generateGoldenTemplatesFor).evaluated
  )
  .aggregate(
    smithplatesCodegenCore,
    smithplatesSmithyNeutral,
    smithplatesSqlIr,
    smithplatesHttpIr,
    smithplatesScalatePrecompiler,
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
