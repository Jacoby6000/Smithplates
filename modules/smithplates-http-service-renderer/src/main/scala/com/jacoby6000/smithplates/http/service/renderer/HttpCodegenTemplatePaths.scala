package com.jacoby6000.smithplates.http.service.renderer

/** Shared helpers for HTTP deck template paths that use the `models/` prefix to select the models template root. */
object HttpCodegenTemplatePaths {
  val ModelsPrefix: String = "models/"

  def resolvedTemplateDirectory(
      defaultTemplateDirectory: String,
      modelsTemplateDirectory: String,
      templatePath: String
  ): String =
    if (templatePath.startsWith(ModelsPrefix)) {
      modelsTemplateDirectory
    } else {
      defaultTemplateDirectory
    }

  def stripTemplateDirectoryPrefix(templatePath: String): String =
    templatePath.stripPrefix(ModelsPrefix)
}
