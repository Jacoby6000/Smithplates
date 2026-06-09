package com.jacoby6000.smithplates.generators

import com.jacoby6000.smithplates.codegentest.CodegenTemplateTestDiscovery
import com.jacoby6000.smithplates.codegentest.PythonCodegenRuffFormatter
import com.jacoby6000.smithplates.codegentest.SmithyBuildTemplateRunner

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import scala.jdk.CollectionConverters.*

/** Runs `smithy build` for each case and syncs `out/` into `expected/`. */
object GoldenTemplateOutputGenerator {
  def generate(language: String, caseNames: Seq[String], repoRoot: Path = defaultRepoRoot): Unit = {
    val classLoader = getClass.getClassLoader
    val discovered  =
      CodegenTemplateTestDiscovery
        .discover(repoRoot, language, Set.empty)
        .map(testCase => testCase.name -> testCase)
        .toMap

    caseNames.foreach { caseName =>
      val testCase =
        discovered.getOrElse(
          caseName,
          throw new IllegalArgumentException(
            s"Unknown golden case '$caseName' for language '$language'. " +
              s"Known cases: ${discovered.keys.toList.sorted.mkString(", ")}"
          )
        )

      val outputDirectory =
        SmithyBuildTemplateRunner
          .run(testCase.caseDirectory, classLoader)
          .fold(message => sys.error(message), identity)

      val expectedDirectory = testCase.caseDirectory.resolve("expected")
      syncOutputToExpected(outputDirectory, expectedDirectory)
      PythonCodegenRuffFormatter.formatGeneratedPython(expectedDirectory, repoRoot)
      Console.out.println(s"Wrote golden outputs for '$caseName' ($language)")
    }
  }

  private def syncOutputToExpected(outputDirectory: Path, expectedDirectory: Path): Unit = {
    deleteRecursively(expectedDirectory)
    Files.createDirectories(expectedDirectory)

    val pluginPrefix = s"source/${SmithyBuildTemplateRunner.PluginName}/"

    Files
      .walk(outputDirectory)
      .iterator()
      .asScala
      .filter(Files.isRegularFile(_))
      .foreach { source =>
        val relativePath =
          outputDirectory.relativize(source).toString.replace('\\', '/')
        if (!relativePath.startsWith(pluginPrefix)) {
          ()
        } else {
          val expectedRelativePath = relativePath.stripPrefix(pluginPrefix)
          val target               = expectedDirectory.resolve(expectedRelativePath)
          Files.createDirectories(target.getParent)
          Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
      }
  }

  private def deleteRecursively(path: Path): Unit =
    if (Files.exists(path)) {
      Files
        .walk(path)
        .sorted(java.util.Comparator.reverseOrder[Path]())
        .forEach(Files.delete(_))
    }

  private def defaultRepoRoot: Path =
    Paths.get(sys.props.getOrElse("user.dir", ".")).toAbsolutePath.normalize
}
