package com.jacoby6000.smithplates.plugin.generators

import com.jacoby6000.smithplates.plugin.codegentest.PythonCodegenRuffFormatter
import com.jacoby6000.smithplates.plugin.codegentest.SmithyBuildTemplateRunner

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import scala.jdk.CollectionConverters.*
import scala.sys.process.*

/** Runs smithy build for `example/python` and syncs plugin output into the project tree. */
object ExamplePythonBuild {
  private val PluginPrefix            = s"source/${SmithyBuildTemplateRunner.PluginName}/"
  private val OpenApiGeneratorVersion = "7.12.0"

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
    ensureHttpPackageNamespace(exampleRoot)

    val openApiBuildRoot = exampleRoot.resolve("openapi")
    if (Files.isRegularFile(openApiBuildRoot.resolve("smithy-build.json"))) {
      val openApiOutput =
        SmithyBuildTemplateRunner
          .run(openApiBuildRoot, classLoader)
          .fold(message => sys.error(message), identity)
      val openApiSpec   = syncOpenApiSpec(openApiOutput, exampleRoot)
      generateOpenApiPythonClient(exampleRoot, openApiSpec)
    }

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

  /** Map `generated.petstore_api` imports to the generated `api/` tree under `src/generated/`. */
  private def ensureHttpPackageNamespace(exampleRoot: Path): Unit = {
    val namespaceRoot = exampleRoot.resolve("src/generated/generated")
    val apiRoot       = exampleRoot.resolve("src/generated/api")
    if (!Files.isDirectory(apiRoot)) {
      return
    }
    Files.createDirectories(namespaceRoot)
    val namespaceInit = namespaceRoot.resolve("__init__.py")
    if (!Files.isRegularFile(namespaceInit)) {
      Files.writeString(namespaceInit, ""); ()
    }
    val packageLink   = namespaceRoot.resolve("petstore_api")
    Files.deleteIfExists(packageLink)
    Files.createSymbolicLink(packageLink, apiRoot.toAbsolutePath.normalize); ()
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

    val formatRoot = clientOutput.resolve("petstore_client")
    if (Files.isDirectory(formatRoot)) {
      try
        PythonCodegenRuffFormatter.formatGeneratedPython(
          formatRoot,
          exampleRoot.getParent.getParent,
          "example-python-openapi-client",
          System.nanoTime()
        )
      catch {
        case ex: Exception =>
          Console.err.println(s"warning: ruff formatting skipped for $formatRoot: ${ex.getMessage}")
      }
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
