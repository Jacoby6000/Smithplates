package com.jacoby6000.smithplates.codegen

/** Maps template classpath roots to generated output path segments and Python import packages. */
object TemplateOutputPrefix {

  /** Filesystem path segment derived from a template root (strips `<language>/src/`). */
  def fromTemplateDirectory(templateDirectory: String): String =
    stripTemplateSourcePrefix(templateDirectory)

  def stripTemplateSourcePrefix(templateDirectory: String): String = {
    val normalized = templateDirectory.stripPrefix("classpath:").stripPrefix("/").stripSuffix("/")
    val segments   = normalized.split("/").toList.filter(_.nonEmpty)
    segments match {
      case language :: "src" :: rest if language != "templates" =>
        rest.mkString("/")
      case "templates" :: _language :: "src" :: rest            =>
        rest.mkString("/")
      case _                                                    =>
        normalized
    }
  }
}
