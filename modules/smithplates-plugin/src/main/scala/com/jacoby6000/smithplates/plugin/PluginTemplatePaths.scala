package com.jacoby6000.smithplates.plugin

object PluginTemplatePaths {
  def classpathResourcePath(templateDirectory: String, template: String): String = {
    val baseDirectory      = templateDirectory.stripPrefix("classpath:").stripSuffix("/")
    val normalizedTemplate =
      if (template.startsWith("/")) {
        template.stripPrefix("/")
      } else {
        template
      }
    if (baseDirectory.isEmpty) {
      s"/$normalizedTemplate"
    } else {
      s"/$baseDirectory/$normalizedTemplate"
    }
  }

  def defaultSqlTemplateDirectory(languageId: String): String =
    languageId match {
      case "python" => "classpath:python/src/db"
      case other    => s"classpath:templates/$other/src/db"
    }

  def defaultHttpTemplateDirectory(languageId: String, relativePath: String): String =
    languageId match {
      case "python" => s"classpath:$languageId/src/$relativePath"
      case other    => s"classpath:templates/$other/src/$relativePath"
    }
}
