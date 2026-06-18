package com.jacoby6000.smithplates.plugin.codegentest

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** Runs the Smithy CLI for golden template fixtures (no programmatic SmithyBuild API). */
object TemplateSmithyBuild {
  val PluginName: String         = "smithplates"
  val PluginOutputPrefix: String = s"source/$PluginName/"

  def run(caseDirectory: Path): Either[String, Path] = {
    val label                           = caseDirectory.getFileName.toString
    val startedAt                       = System.nanoTime()
    def logPhase(message: String): Unit =
      TemplateBuildLog.phase(label, message, startedAt)

    logPhase("starting")
    val repoRoot      = inferRepoRoot(caseDirectory)
    val supportScript = repoRoot.resolve("scripts/lib/template-build-support.sh")
    if (!Files.isRegularFile(supportScript)) {
      return Left(s"Missing template build script at $supportScript")
    }

    logPhase(s"running smithy build via ${supportScript.getFileName}")
    val process =
      ProcessBuilder(
        "bash",
        supportScript.toString,
        "build",
        caseDirectory.toAbsolutePath.normalize.toString,
        repoRoot.toString
      ).redirectErrorStream(true)
        .start()

    val output   = new String(process.getInputStream.readAllBytes(), StandardCharsets.UTF_8)
    val exitCode = process.waitFor()
    if (exitCode != 0) {
      logPhase(s"failed: exit $exitCode")
      return Left(s"Smithy build failed for $caseDirectory:\n$output")
    }

    val smithyOutputRoot = caseDirectory.resolve("build/smithy").toAbsolutePath.normalize
    if (!Files.isDirectory(smithyOutputRoot)) {
      return Left(s"Smithy build succeeded but output directory is missing: $smithyOutputRoot")
    }

    PythonCodegenRuffFormatter.formatGeneratedPython(
      smithyOutputRoot,
      repoRoot,
      label,
      startedAt
    )
    logPhase("finished")
    Right(smithyOutputRoot)
  }

  private def inferRepoRoot(caseDirectory: Path): Path =
    caseDirectory.toAbsolutePath.normalize().resolve("../../../..").normalize()
}
