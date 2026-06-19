package com.jacoby6000.smithplates.scalate

object ScalateTemplatePaths {
  def normalizeClasspathUri(templateClasspath: String): String =
    templateClasspath.stripPrefix("classpath:")

  def normalizeTemplateRoot(templateRoot: String): String =
    templateRoot.stripPrefix("classpath:").stripPrefix("/").stripSuffix("/")

  def templateUriRelativeToRoot(templateClasspath: String, templateRoot: String): String = {
    val normalizedTemplate = normalizeTemplateRoot(templateClasspath)
    val root               = normalizeTemplateRoot(templateRoot)
    if (normalizedTemplate.startsWith(s"$root/")) {
      normalizedTemplate.stripPrefix(s"$root/")
    } else {
      normalizedTemplate
    }
  }

  def partialUriRelativeToRoot(partialReference: String): String = {
    val normalizedReference =
      if (partialReference.endsWith(".ssp")) {
        partialReference
      } else {
        s"$partialReference.ssp"
      }
    normalizedReference.stripPrefix("/")
  }

  def normalizeResourcePath(resourcePath: String): String =
    if (resourcePath.startsWith("/")) {
      resourcePath
    } else {
      s"/$resourcePath"
    }

  def normalizeRenderedOutput(output: String): String = {
    val trimmed = output.stripTrailing()
    if (trimmed.isEmpty) {
      trimmed
    } else {
      s"$trimmed\n"
    }
  }
}
