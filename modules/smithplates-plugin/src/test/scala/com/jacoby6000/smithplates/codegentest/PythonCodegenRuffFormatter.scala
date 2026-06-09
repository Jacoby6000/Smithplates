package com.jacoby6000.smithplates.codegentest

import java.nio.file.Files
import java.nio.file.Path

/** Normalizes generated Python sources so golden tests and language-test-harness linters agree. */
object PythonCodegenRuffFormatter {
  def formatGeneratedPython(root: Path, repoRoot: Path): Unit = {
    val harnessDir = repoRoot.resolve("language-test-harnesses/python").toAbsolutePath.normalize()
    val pyproject  = harnessDir.resolve("pyproject.toml")
    if (!Files.isRegularFile(pyproject)) {
      return
    }

    val formatRoot = resolveFormatRoot(root.toAbsolutePath.normalize())
    if (!Files.isDirectory(formatRoot) || !containsPythonFiles(formatRoot)) {
      return
    }

    val command  =
      s"""cd '${harnessDir}' && uv run ruff check --fix --config pyproject.toml '${formatRoot}' && uv run ruff format --config pyproject.toml '${formatRoot}'"""
    val process  = new ProcessBuilder("bash", "-c", command).redirectErrorStream(true).start()
    val output   = new String(process.getInputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
    val exitCode = process.waitFor()
    if (exitCode != 0) {
      sys.error(
        s"Failed to ruff-format generated Python under $formatRoot (exit $exitCode):\n$output"
      )
    }
  }

  def inferRepoRoot(caseDirectory: Path): Path =
    caseDirectory.toAbsolutePath.normalize().resolve("../../../..").normalize()

  private def resolveFormatRoot(root: Path): Path = {
    val pluginOutput = root.resolve(s"source/${SmithyBuildTemplateRunner.PluginName}")
    if (Files.isDirectory(pluginOutput)) {
      pluginOutput
    } else {
      root
    }
  }

  private def containsPythonFiles(root: Path): Boolean = {
    val iterator = Files.walk(root).iterator()
    while (iterator.hasNext) {
      val path = iterator.next()
      if (Files.isRegularFile(path) && path.getFileName.toString.endsWith(".py")) {
        return true
      }
    }
    false
  }
}
