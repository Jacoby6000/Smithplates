package com.jacoby6000.smithplates.plugin

import cats.syntax.all.*
import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.service.renderer.ScalateSspTemplateEngine
import com.jacoby6000.smithplates.sql.service.renderer.SqlServiceCodegenDbArtifacts

object LanguageTargetTemplateValidator {
  def defaultTemplateDirectory(languageId: String): String =
    PluginTemplatePaths.defaultSqlTemplateDirectory(languageId)

  def resolveTemplateDirectory(target: LanguageTarget, languageId: String): String =
    target.templateDirectory.getOrElse(defaultTemplateDirectory(languageId))

  def validate(
      languageId: String,
      target: LanguageTarget,
      enabledDialectKeys: List[String]
  ): SqlValidated[Unit] =
    PluginConstants
      .requireTemplateDirectoryForLanguage(languageId, "sql", target.templateDirectory)
      .andThen(_ =>
        internal.validateRequiredTemplatesExist(
          languageId = languageId,
          templateDirectory = resolveTemplateDirectory(target, languageId),
          enabledDialectKeys = enabledDialectKeys
        ))

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def validateRequiredTemplatesExist(
        languageId: String,
        templateDirectory: String,
        enabledDialectKeys: List[String]
    ): SqlValidated[Unit] = {
      val requiredTemplates =
        SqlServiceCodegenDbArtifacts
          .forEnabledDialects(enabledDialectKeys)
          .map(_.template)
          .distinct

      val missingTemplates =
        requiredTemplates.filterNot { template =>
          ScalateSspTemplateEngine.classpathResourceExists(
            PluginTemplatePaths.classpathResourcePath(templateDirectory, template)
          )
        }

      if (missingTemplates.isEmpty) {
        ().validNel
      } else {
        SqlValidated.invalid(
          com.jacoby6000.smithplates.sql.model.InvalidPluginConfig(
            s"smithplates.$languageId.sql templateDirectory '$templateDirectory' " +
              s"is missing required templates: ${missingTemplates.sorted.mkString(", ")}"
          )
        )
      }
    }
  }
}
