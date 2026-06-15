package com.jacoby6000.smithplates.plugin.codegentest

import com.jacoby6000.smithplates.plugin.SmithplatesBuildPlugin
import software.amazon.smithy.build.SmithyBuild
import software.amazon.smithy.build.SmithyBuildPlugin
import software.amazon.smithy.build.model.SmithyBuildConfig
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.loader.ModelAssembler
import software.amazon.smithy.openapi.fromsmithy.Smithy2OpenApi

import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import java.util.ServiceLoader
import java.util.function.Function
import scala.jdk.CollectionConverters.*

object SmithyBuildTemplateRunner {
  val PluginName = "smithplates"

  def run(caseDirectory: Path, classLoader: ClassLoader): Either[String, Path] = {
    val label                           = caseDirectory.getFileName.toString
    val startedAt                       = System.nanoTime()
    def logPhase(message: String): Unit =
      TemplateBuildLog.phase(label, message, startedAt)

    logPhase("starting")
    val configPath = caseDirectory.resolve("smithy-build.json")
    if (!Files.isRegularFile(configPath)) {
      return Left(s"Missing smithy-build.json in $caseDirectory")
    }

    val outputDirectory = caseDirectory.resolve("out")
    logPhase(s"preparing output directory $outputDirectory")
    deleteRecursively(outputDirectory)
    Files.createDirectories(outputDirectory)

    val pluginsByName                                                = loadPlugins(classLoader)
    val pluginFactory: Function[String, Optional[SmithyBuildPlugin]] =
      (name: String) =>
        pluginsByName.get(name) match {
          case Some(plugin) => Optional.of(plugin)
          case None         => Optional.empty()
        }

    try {
      logPhase(s"loading $configPath")
      val loadedConfig =
        SmithyBuildConfig.load(configPath)

      logPhase("running smithy build")
      val result =
        SmithyBuild
          .create(classLoader, () => modelAssemblerFor(loadedConfig, classLoader))
          .config(loadedConfig)
          .outputDirectory(outputDirectory)
          .pluginClassLoader(classLoader)
          .pluginFactory(pluginFactory)
          .build()
      logPhase("smithy build finished")

      if (result.anyBroken()) {
        Left(formatBrokenBuild(result))
      } else {
        PythonCodegenRuffFormatter.formatGeneratedPython(
          outputDirectory,
          PythonCodegenRuffFormatter.inferRepoRoot(caseDirectory),
          label,
          startedAt
        )
        logPhase("finished")
        Right(outputDirectory)
      }
    } catch {
      case ex: Exception =>
        logPhase(s"failed: ${ex.getMessage}")
        Left(s"Smithy build failed for $caseDirectory: ${ex.getMessage}")
    }
  }

  private val TraitModelResources =
    List(
      "META-INF/smithy/smithplates.codegen.sql.smithy",
      "META-INF/smithy/smithplates.codegen.sql.service.smithy",
      "META-INF/smithy/smithplates.codegen.http.smithy"
    )

  private def modelAssemblerFor(config: SmithyBuildConfig, classLoader: ClassLoader): ModelAssembler = {
    // Match SqlTestModelLoader: @sqlDerive* operations use primitive outputs rejected by stock Smithy validation.
    val assembler = Model.assembler(classLoader).discoverModels(classLoader).disableValidation()
    TraitModelResources.foreach { resource =>
      Option(classLoader.getResource(resource)).foreach { url =>
        assembler.addImport(url); ()
      }
    }
    config.getSources.forEach { source =>
      assembler.addImport(source); ()
    }
    config.getImports.forEach { importPath =>
      assembler.addImport(importPath); ()
    }
    assembler
  }

  private def loadPlugins(classLoader: ClassLoader): Map[String, SmithyBuildPlugin] = {
    val fromServiceLoader =
      ServiceLoader
        .load(classOf[SmithyBuildPlugin], classLoader)
        .iterator()
        .asScala
        .map(plugin => plugin.getName -> plugin)
        .toMap

    fromServiceLoader ++ Map(
      PluginName -> new SmithplatesBuildPlugin(),
      "openapi"  -> new Smithy2OpenApi()
    )
  }

  private def formatBrokenBuild(result: software.amazon.smithy.build.SmithyBuildResult): String =
    result.getProjectionResults.asScala
      .filter(_.isBroken)
      .map { projection =>
        val events =
          projection.getEvents.asScala
            .map(event => s"${event.getSeverity}: ${event.getMessage}")
            .mkString("\n")
        s"Projection '${projection.getProjectionName}' failed:\n$events"
      }
      .mkString("\n\n")

  private def deleteRecursively(path: Path): Unit =
    if (Files.exists(path)) {
      Files
        .walk(path)
        .sorted(java.util.Comparator.reverseOrder[Path]())
        .forEach(Files.delete(_))
    }
}
