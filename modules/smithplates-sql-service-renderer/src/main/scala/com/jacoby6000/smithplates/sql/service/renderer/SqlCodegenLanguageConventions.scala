package com.jacoby6000.smithplates.sql.service.renderer

import com.jacoby6000.smithplates.codegen.core.CodegenValidated
import com.jacoby6000.smithplates.codegen.core.CodegenValidated.*
import com.jacoby6000.smithplates.codegen.core.InvalidLanguageBaseConfig
import com.jacoby6000.smithplates.codegen.core.planning.CodegenSettings
import com.jacoby6000.smithplates.codegen.core.strategy.config.LanguageBaseConfig
import com.jacoby6000.smithplates.codegen.core.strategy.config.LanguageBaseConfigLoader

object SqlCodegenLanguageConventions {
  def loadBaseConfig(settings: SqlServiceCodegenSettings): CodegenValidated[LanguageBaseConfig] = {
    val languageId = settings.templateDirectory.stripPrefix("classpath:").split('/').headOption.getOrElse("python")
    Option(getClass.getClassLoader.getResourceAsStream(s"$languageId/base_config.json")) match {
      case Some(stream) =>
        try {
          val text = scala.io.Source.fromInputStream(stream, "UTF-8").mkString
          LanguageBaseConfigLoader.loadJson(text)
        } finally stream.close()
      case None         =>
        InvalidLanguageBaseConfig(
          s"missing language base config resource: $languageId/base_config.json"
        ).invalidNel
    }
  }

  def serviceModuleName(settings: SqlServiceCodegenSettings, serviceName: String): CodegenValidated[String] =
    loadBaseConfig(settings).map { baseConfig =>
      baseConfig.conventions(settings.rootNamespace).memberName(serviceName)
    }

  def codegenSettings(settings: SqlServiceCodegenSettings): CodegenValidated[CodegenSettings] =
    loadBaseConfig(settings).map { baseConfig =>
      CodegenSettings(
        sourceOutputDirectory = settings.sourceOutputDirectory.map(normalizeDirectory).getOrElse(""),
        testOutputDirectory = settings.testOutputDirectory.map(normalizeDirectory).getOrElse(""),
        conventions = baseConfig.conventions(settings.rootNamespace)
      )
    }

  def normalizeDirectory(directory: String): String =
    directory.stripSuffix("/").stripSuffix("\\")
}
