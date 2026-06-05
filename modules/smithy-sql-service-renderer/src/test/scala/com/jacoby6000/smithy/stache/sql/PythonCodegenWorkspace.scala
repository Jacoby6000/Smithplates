package com.jacoby6000.smithy.stache.sql.codegen

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import scala.jdk.CollectionConverters.*
import scala.sys.process.*

object PythonCodegenWorkspace {
  private val lock                       = new Object()
  @volatile private var environmentReady = false

  private val workspaceDirectoryName = "sql-service-codegen-python-workspace"
  private val resourcePrefix         = "sql-service-codegen-python-workspace"

  def ensureEnvironment(): Unit =
    lock.synchronized {
      if (!environmentReady) {
        initializeWorkspace()
        runUvCommand(Seq("sync", "--all-groups", "--frozen")).fold(
          message => throw new IllegalStateException(message),
          _ => ()
        )
        environmentReady = true
      }
    }

  def writeCase(caseName: String, artifacts: List[SqlCodegenArtifact]): Path = {
    val caseRoot  = workspaceRoot.resolve("cases").resolve(caseName)
    val keepPaths = artifacts.map(artifact => caseRoot.resolve(artifact.relativePath)).toSet

    if (Files.isDirectory(caseRoot)) {
      Files
        .walk(caseRoot)
        .iterator()
        .asScala
        .filter(Files.isRegularFile(_))
        .filter(path => path.toString.endsWith(".py"))
        .filter(path => !keepPaths.contains(path))
        .foreach(Files.delete)
    }

    artifacts.foreach { artifact =>
      val outputPath = caseRoot.resolve(artifact.relativePath)
      Files.createDirectories(outputPath.getParent)
      Files.writeString(outputPath, artifact.content, StandardCharsets.UTF_8)
    }

    caseRoot
  }

  def assertStrictTypecheck(caseRoot: Path, artifacts: List[SqlCodegenArtifact]): Unit = {
    val importPath = pythonImportPath(caseRoot, artifacts)
    val srcPaths   = srcArtifactPaths(caseRoot, artifacts)
    runMypy(srcPaths, importPath)
    writePyrightConfig(caseRoot, artifacts)
    runPyright(caseRoot, srcPaths)
  }

  def assertGeneratedIntegrationTests(
      caseRoot: Path,
      artifacts: List[SqlCodegenArtifact],
      dialectKey: String
  ): Unit = {
    val testArtifacts =
      artifacts.filter(_.kind == SqlServiceCodegenArtifactKind.Test)
    if (testArtifacts.isEmpty) {
      throw new AssertionError(s"No generated integration test artifacts under $caseRoot")
    }

    dialectKey match {
      case "postgres" if !DockerSupport.isAvailable =>
        throw new AssertionError(
          "Postgres generated integration tests require Docker (testcontainers). " +
            "Install Docker and ensure `docker info` succeeds, then re-run the test suite."
        )
      case _                                        =>
        ()
    }

    val testFiles = testArtifacts.map(artifact => caseRoot.resolve(artifact.relativePath))
    runPytest(testFiles, pythonImportPath(caseRoot, artifacts))
  }

  private def srcArtifactPaths(caseRoot: Path, artifacts: List[SqlCodegenArtifact]): List[Path] =
    artifacts
      .filter(_.kind == SqlServiceCodegenArtifactKind.Src)
      .map(artifact => caseRoot.resolve(artifact.relativePath))

  private def pythonImportPath(caseRoot: Path, artifacts: List[SqlCodegenArtifact]): String =
    artifacts
      .filter(_.kind == SqlServiceCodegenArtifactKind.Src)
      .map(artifact => caseRoot.resolve(artifact.relativePath).getParent)
      .distinct
      .sortBy(_.getNameCount)
      .map(_.toString)
      .mkString(java.io.File.pathSeparator)

  def validateCase(
      caseName: String,
      artifacts: List[SqlCodegenArtifact],
      dialectKey: String
  ): Unit = {
    ensureEnvironment()
    val caseRoot = writeCase(caseName, artifacts)
    assertStrictTypecheck(caseRoot, artifacts)
    if (artifacts.exists(_.kind == SqlServiceCodegenArtifactKind.Test)) {
      assertGeneratedIntegrationTests(caseRoot, artifacts, dialectKey)
    }
  }

  private def workspaceRoot: Path = {
    val workingDirectory = Paths.get(System.getProperty("user.dir"))
    val candidates       =
      List(
        workingDirectory.resolve(s"sql-plugin/target/$workspaceDirectoryName"),
        workingDirectory.resolve(s"target/$workspaceDirectoryName")
      )
    candidates
      .find(candidate => Files.isRegularFile(candidate.resolve("pyproject.toml")))
      .getOrElse(candidates.head)
  }

  private def initializeWorkspace(): Unit = {
    Files.createDirectories(workspaceRoot)
    copyResource("pyproject.toml", workspaceRoot.resolve("pyproject.toml"))
    copyResource("uv.lock", workspaceRoot.resolve("uv.lock"))
  }

  private def copyResource(resourceName: String, destination: Path): Unit = {
    val resourcePath = s"/$resourcePrefix/$resourceName"
    val stream       =
      Option(getClass.getResourceAsStream(resourcePath)).getOrElse {
        throw new IllegalStateException(s"Workspace resource not found on classpath: $resourcePath")
      }
    try {
      Files.copy(stream, destination, StandardCopyOption.REPLACE_EXISTING)
      ()
    } finally stream.close()
  }

  private def runMypy(srcPaths: List[Path], importPath: String): Unit =
    runUvCommand(
      Seq("run", "mypy") ++ srcPaths.map(_.toString),
      environment = Seq("MYPYPATH" -> importPath)
    ).fold(
      message => throw new AssertionError(s"mypy failed:\n$message"),
      _ => ()
    )

  private def writePyrightConfig(caseRoot: Path, artifacts: List[SqlCodegenArtifact]): Unit = {
    val extraPaths =
      artifacts
        .filter(_.kind == SqlServiceCodegenArtifactKind.Src)
        .map(artifact => caseRoot.relativize(caseRoot.resolve(artifact.relativePath).getParent))
        .distinct
        .sortBy(_.getNameCount)
        .map(_.toString.replace('\\', '/'))
        .map(path => s""""$path"""")
        .mkString(", ")

    val venvPath =
      (1 to workspaceRoot.relativize(caseRoot).getNameCount)
        .map(_ => "..")
        .mkString("/")

    val config =
      s"""{
  "venvPath": "$venvPath",
  "venv": ".venv",
  "extraPaths": [$extraPaths]
}
"""
    Files.writeString(caseRoot.resolve("pyrightconfig.json"), config, StandardCharsets.UTF_8)
    ()
  }

  private def runPyright(caseRoot: Path, srcPaths: List[Path]): Unit =
    runUvCommand(
      Seq(
        "run",
        "pyright",
        "--warnings",
        "--project",
        caseRoot.toString
      ) ++ srcPaths.map(_.toString)
    ).fold(
      message => throw new AssertionError(s"pyright failed:\n$message"),
      _ => ()
    )

  private def runPytest(testFiles: List[Path], importPath: String): Unit =
    runUvCommand(
      Seq(
        "run",
        "pytest",
        "-m",
        "integration",
        "-v",
        "--tb=short"
      ) ++ testFiles.map(_.toString),
      environment = Seq("PYTHONPATH" -> importPath)
    ).fold(
      message => throw new AssertionError(s"Generated integration tests failed:\n$message"),
      _ => ()
    )

  private def runUvCommand(
      args: Seq[String],
      environment: Seq[(String, String)] = Nil
  ): Either[String, Unit] = {
    val command  = Seq("uv") ++ args
    val stdout   = new StringBuilder
    val stderr   = new StringBuilder
    val exitCode =
      Process(
        command,
        cwd = workspaceRoot.toFile,
        extraEnv = environment*
      ).!(
        ProcessLogger(
          line => { stdout.append(line).append('\n'); () },
          line => { stderr.append(line).append('\n'); () }
        )
      )

    if (exitCode == 0) {
      Right(())
    } else {
      Left(formatProcessFailure(command, exitCode, stdout.toString(), stderr.toString()))
    }
  }

  private def formatProcessFailure(
      command: Seq[String],
      exitCode: Int,
      stdout: String,
      stderr: String
  ): String = {
    val sections =
      List(
        Some(s"command: ${command.mkString(" ")}"),
        Some(s"workspace: $workspaceRoot"),
        Some(s"exit code: $exitCode"),
        nonEmptySection("stdout", stdout),
        nonEmptySection("stderr", stderr)
      ).flatten

    sections.mkString("\n")
  }

  private def nonEmptySection(label: String, value: String): Option[String] =
    if (value.trim.isEmpty) {
      None
    } else {
      Some(s"$label:\n${value.trim}")
    }
}
