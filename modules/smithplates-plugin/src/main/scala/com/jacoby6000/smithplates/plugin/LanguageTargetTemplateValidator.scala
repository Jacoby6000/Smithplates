package com.jacoby6000.smithplates.plugin

import cats.syntax.all.*
import com.jacoby6000.smithplates.codegen.core.planning.CodegenOutput
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
    ): SqlValidated[Unit] =
      SqlServiceCodegenDbArtifacts
        .dialectArtifacts(templateDirectory, enabledDialectKeys)
        .leftMap(_.map(error => com.jacoby6000.smithplates.sql.model.InvalidPluginConfig(error.message)))
        .andThen(artifacts => validateTemplatesExist(languageId, templateDirectory, artifacts))

    def validateTemplatesExist(
        languageId: String,
        templateDirectory: String,
        artifacts: List[CodegenOutput]
    ): SqlValidated[Unit] = {
      val requiredTemplates =
        artifacts
          .flatMap(SqlServiceCodegenDbArtifacts.templatePath)
          .filter(SqlServiceCodegenDbArtifacts.isRenderedTemplate)
          .distinct

      val missingTemplates =
        requiredTemplates.filterNot { template =>
          val resourcePath =
            if (ConsumerCodegenOutputs.isAdditionalTemplatePath(template)) {
              template.stripPrefix("classpath:")
            } else {
              PluginTemplatePaths.classpathResourcePath(templateDirectory, template)
            }
          ScalateSspTemplateEngine.classpathResourceExists(resourcePath)
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
