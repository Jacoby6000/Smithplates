package com.jacoby6000.smithplates.http.service.renderer

import com.jacoby6000.smithplates.codegen.core.CodegenValidated
import com.jacoby6000.smithplates.codegen.core.CodegenValidated.*
import com.jacoby6000.smithplates.codegen.core.InvalidLanguageBaseConfig
import com.jacoby6000.smithplates.codegen.core.ModelId
import com.jacoby6000.smithplates.codegen.core.planning.CodegenSettings
import com.jacoby6000.smithplates.codegen.core.strategy.Conventions
import com.jacoby6000.smithplates.codegen.core.strategy.config.LanguageBaseConfig
import com.jacoby6000.smithplates.codegen.core.strategy.config.LanguageBaseConfigLoader

object HttpCodegenLanguageConventions {
  def loadBaseConfig(settings: HttpServiceCodegenSettings): CodegenValidated[LanguageBaseConfig] = {
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

  def codegenSettings(settings: HttpServiceCodegenSettings): CodegenValidated[CodegenSettings] =
    loadBaseConfig(settings).map { baseConfig =>
      CodegenSettings(
        sourceOutputDirectory = settings.sourceOutputDirectory.map(normalizeDirectory).getOrElse(""),
        testOutputDirectory = settings.testOutputDirectory.map(normalizeDirectory).getOrElse(""),
        conventions =
          httpConventions(baseConfig.conventions(settings.rootNamespace), settings.modelsPackageNameOverride)
      )
    }

  def normalizeDirectory(directory: String): String =
    directory.stripSuffix("/").stripSuffix("\\")

  def httpConventions(delegate: Conventions, modelsPackageNameOverride: Option[String]): Conventions =
    modelsPackageNameOverride match {
      case None                    => delegate
      case Some(modelsPackageName) =>
        new Conventions {
          def className(id: ModelId): String =
            delegate.className(id)

          def modulePath(id: ModelId): String = {
            val moduleName = delegate.fileName(id).stripSuffix(".py")
            s"$modelsPackageName.$moduleName"
          }

          def fileName(id: ModelId): String =
            delegate.fileName(id)

          def memberName(smithyName: String): String =
            delegate.memberName(smithyName)

          def functionName(smithyName: String): String =
            delegate.functionName(smithyName)

          def constantName(smithyName: String): String =
            delegate.constantName(smithyName)

          def packageName(smithyNamespace: String): String = {
            val _ = smithyNamespace
            modelsPackageName
          }

          def rootNamespaceDir: String =
            delegate.rootNamespaceDir
        }
    }
}
