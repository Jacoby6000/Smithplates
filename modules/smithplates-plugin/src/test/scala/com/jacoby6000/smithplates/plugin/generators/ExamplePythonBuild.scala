package com.jacoby6000.smithplates.plugin.generators

import com.jacoby6000.smithplates.plugin.codegentest.PythonCodegenRuffFormatter
import com.jacoby6000.smithplates.plugin.codegentest.SmithyBuildTemplateRunner

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import scala.jdk.CollectionConverters.*

/** Runs smithy build for `example/python` and syncs plugin output into the project tree. */
object ExamplePythonBuild {
  private val PluginPrefix = s"source/${SmithyBuildTemplateRunner.PluginName}/"

  def main(args: Array[String]): Unit = {
    val repoRoot    = Paths.get(sys.props.getOrElse("user.dir", ".")).toAbsolutePath.normalize
    val exampleRoot =
      args.headOption.map(Paths.get(_)).getOrElse(repoRoot.resolve("example/python")).toAbsolutePath.normalize
    if (!Files.isRegularFile(exampleRoot.resolve("smithy-build.json"))) {
      sys.error(s"Missing smithy-build.json in $exampleRoot")
    }

    val classLoader     = getClass.getClassLoader
    val outputDirectory =
      SmithyBuildTemplateRunner
        .run(exampleRoot, classLoader)
        .fold(message => sys.error(message), identity)

    syncPluginOutput(outputDirectory, exampleRoot)
    List("src/generated", "tests").foreach { relative =>
      val formatRoot = exampleRoot.resolve(relative)
      if (java.nio.file.Files.isDirectory(formatRoot)) {
        try
          PythonCodegenRuffFormatter.formatGeneratedPython(
            formatRoot,
            repoRoot,
            s"example-python-$relative",
            System.nanoTime()
          )
        catch {
          case ex: Exception =>
            Console.err.println(s"warning: ruff formatting skipped for $formatRoot: ${ex.getMessage}")
        }
      }
    }
    Console.out.println(s"Generated Smithplates artifacts under $exampleRoot")
  }

  private def syncPluginOutput(outputDirectory: Path, exampleRoot: Path): Unit =
    Files
      .walk(outputDirectory)
      .iterator()
      .asScala
      .filter(Files.isRegularFile(_))
      .foreach { source =>
        val relativePath = outputDirectory.relativize(source).toString.replace('\\', '/')
        if (relativePath.startsWith(PluginPrefix)) {
          val targetRelative = relativePath.stripPrefix(PluginPrefix)
          val target         = exampleRoot.resolve(targetRelative)
          Files.createDirectories(target.getParent)
          Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING); ()
        }
      }
}
