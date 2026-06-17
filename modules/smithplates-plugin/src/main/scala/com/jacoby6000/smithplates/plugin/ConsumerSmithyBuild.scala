package com.jacoby6000.smithplates.plugin

import software.amazon.smithy.build.SmithyBuild
import software.amazon.smithy.build.SmithyBuildPlugin
import software.amazon.smithy.build.model.SmithyBuildConfig

import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import java.util.function.Function
import scala.jdk.CollectionConverters.*

object ConsumerSmithyBuild {
  def run(projectDirectory: Path, classLoader: ClassLoader): Either[String, Unit] = {
    val configPath = projectDirectory.resolve("smithy-build.json")
    if (!Files.isRegularFile(configPath)) {
      return Left(s"Missing smithy-build.json in $projectDirectory")
    }

    val outputDirectory = projectDirectory.resolve("build/smithy")
    if (Files.exists(outputDirectory)) {
      deleteRecursively(outputDirectory)
    }
    Files.createDirectories(outputDirectory)

    val pluginsByName                                                = SmithyBuildModelSupport.loadPlugins(classLoader)
    val pluginFactory: Function[String, Optional[SmithyBuildPlugin]] =
      (name: String) =>
        pluginsByName.get(name) match {
          case Some(plugin) => Optional.of(plugin)
          case None         => Optional.empty()
        }

    try {
      val loadedConfig = SmithyBuildConfig.load(configPath)
      val result       =
        SmithyBuild
          .create(classLoader, () => SmithyBuildModelSupport.modelAssemblerFor(loadedConfig, classLoader))
          .config(loadedConfig)
          .outputDirectory(outputDirectory)
          .pluginClassLoader(classLoader)
          .pluginFactory(pluginFactory)
          .build()

      if (result.anyBroken()) {
        Left(formatBrokenBuild(result))
      } else {
        Right(())
      }
    } catch {
      case ex: Exception =>
        Left(s"Smithy build failed for $projectDirectory: ${ex.getMessage}")
    }
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
