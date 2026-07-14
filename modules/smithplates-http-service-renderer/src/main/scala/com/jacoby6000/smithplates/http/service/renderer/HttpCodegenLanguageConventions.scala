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
    val languageId = settings.templateDirectory.stripPrefix("classpath:").split('/').headOption.getOrElse("")
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
        conventions = httpConventions(
          applyServicePackageOverride(baseConfig.conventions(settings.rootNamespace), settings.packageNameOverride),
          settings.modelsPackageNameOverride
        ),
        typeRenderer = baseConfig.typeRenderer,
        commentPrefix = baseConfig.commentPrefix
      )
    }

  def applyServicePackageOverride(delegate: Conventions, packageNameOverride: Option[String]): Conventions =
    packageNameOverride match {
      case None                 => delegate
      case Some(servicePackage) =>
        new Conventions {
          def className(id: ModelId): String =
            delegate.className(id)

          def modulePath(id: ModelId): String =
            delegate.modulePath(id)

          def fileName(id: ModelId): String =
            delegate.fileName(id)

          def fileStem(id: ModelId): String =
            delegate.fileStem(id)

          def memberName(smithyName: String): String =
            delegate.memberName(smithyName)

          def functionName(smithyName: String): String =
            delegate.functionName(smithyName)

          def constantName(smithyName: String): String =
            delegate.constantName(smithyName)

          def packageName(smithyNamespace: String): String = {
            val _ = smithyNamespace
            servicePackage
          }

          def rootNamespaceDir: String =
            delegate.rootNamespaceDir
        }
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

          def modulePath(id: ModelId): String =
            s"$modelsPackageName.${delegate.fileStem(id)}"

          def fileName(id: ModelId): String =
            delegate.fileName(id)

          def fileStem(id: ModelId): String =
            delegate.fileStem(id)

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
