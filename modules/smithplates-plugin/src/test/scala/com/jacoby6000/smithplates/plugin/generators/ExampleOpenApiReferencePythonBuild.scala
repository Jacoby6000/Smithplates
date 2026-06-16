package com.jacoby6000.smithplates.plugin.generators

import com.jacoby6000.smithplates.plugin.codegentest.PythonCodegenRuffFormatter
import com.jacoby6000.smithplates.plugin.codegentest.SmithyBuildTemplateRunner

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import scala.jdk.CollectionConverters.*
import scala.sys.process.*

/** Runs Smithy OpenAPI export and OpenAPI Generator for `example/openapi-reference-python`. */
object ExampleOpenApiReferencePythonBuild {
  private val OpenApiGeneratorVersion = "7.12.0"

  def main(args: Array[String]): Unit = {
    val repoRoot    = Paths.get(sys.props.getOrElse("user.dir", ".")).toAbsolutePath.normalize
    val exampleRoot =
      args.headOption
        .map(Paths.get(_))
        .getOrElse(repoRoot.resolve("example/openapi-reference-python"))
        .toAbsolutePath
        .normalize
    val smithyBuild = exampleRoot.resolve("smithy-build.json")
    if (!Files.isRegularFile(smithyBuild)) {
      sys.error(
        s"Missing smithy-build.json in $exampleRoot (run ./example/openapi-reference-python/render-smithy-build.sh after publishM2)"
      )
    }

    val classLoader     = getClass.getClassLoader
    val outputDirectory =
      SmithyBuildTemplateRunner
        .run(exampleRoot, classLoader)
        .fold(message => sys.error(message), identity)

    val openApiSpec = syncOpenApiSpec(outputDirectory, exampleRoot)
    generateOpenApiPythonClient(exampleRoot, openApiSpec)

    val formatRoot = exampleRoot.resolve("src/generated/client/petstore_client")
    if (Files.isDirectory(formatRoot)) {
      try
        PythonCodegenRuffFormatter.formatGeneratedPython(
          formatRoot,
          repoRoot,
          "example-openapi-reference-python-client",
          System.nanoTime()
        )
      catch {
        case ex: Exception =>
          Console.err.println(s"warning: ruff formatting skipped for $formatRoot: ${ex.getMessage}")
      }
    }
    Console.out.println(s"Generated OpenAPI reference client under $exampleRoot")
  }

  private def syncOpenApiSpec(outputDirectory: Path, exampleRoot: Path): Path = {
    val sourceSpec = findOpenApiSpec(outputDirectory)
    val targetSpec = exampleRoot.resolve("openapi/openapi.json")
    Files.createDirectories(targetSpec.getParent)
    Files.copy(sourceSpec, targetSpec, StandardCopyOption.REPLACE_EXISTING)
    targetSpec
  }

  private def findOpenApiSpec(outputDirectory: Path): Path =
    Files
      .walk(outputDirectory)
      .iterator()
      .asScala
      .filter(Files.isRegularFile(_))
      .find { path =>
        val fileName = path.getFileName.toString.toLowerCase
        fileName.endsWith(".openapi.json") || fileName == "openapi.json" || fileName == "openapi.yaml"
      }
      .getOrElse(sys.error(s"No OpenAPI spec found under $outputDirectory"))

  private def generateOpenApiPythonClient(exampleRoot: Path, openApiSpec: Path): Unit = {
    val generatorJar = resolveOpenApiGeneratorJar()
    val clientOutput = exampleRoot.resolve("src/generated/client")
    deleteRecursively(clientOutput)
    Files.createDirectories(clientOutput)

    val command = Seq(
      "java",
      "-jar",
      generatorJar.toString,
      "generate",
      "-i",
      openApiSpec.toString,
      "-g",
      "python",
      "-o",
      clientOutput.toString,
      "--package-name",
      "petstore_client",
      "--additional-properties",
      "generateSourceCodeOnly=true,library=asyncio,hideGenerationTimestamp=true"
    )
    if (command.! != 0) {
      sys.error(s"OpenAPI Generator failed: ${command.mkString(" ")}")
    }
  }

  private def resolveOpenApiGeneratorJar(): Path = {
    val cacheDirectory =
      Option(System.getenv("XDG_CACHE_HOME"))
        .map(Paths.get(_))
        .getOrElse(Paths.get(System.getProperty("user.home")).resolve(".cache"))
        .resolve("smithystache")
    Files.createDirectories(cacheDirectory)
    val jarPath        = cacheDirectory.resolve(s"openapi-generator-cli-$OpenApiGeneratorVersion.jar")
    if (!Files.isRegularFile(jarPath)) {
      val downloadUrl     =
        s"https://repo1.maven.org/maven2/org/openapitools/openapi-generator-cli/$OpenApiGeneratorVersion/openapi-generator-cli-$OpenApiGeneratorVersion.jar"
      val downloadCommand = Seq("curl", "-fsSL", downloadUrl, "-o", jarPath.toString)
      if (downloadCommand.! != 0) {
        sys.error(s"Failed to download OpenAPI Generator CLI from $downloadUrl")
      }
    }
    jarPath
  }

  private def deleteRecursively(path: Path): Unit =
    if (Files.exists(path)) {
      Files
        .walk(path)
        .sorted(java.util.Comparator.reverseOrder[Path]())
        .forEach(Files.delete(_))
    }
}
