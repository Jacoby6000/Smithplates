package com.jacoby6000.smithplates.codegen

/** Maps template classpath roots to generated output path segments and Python import packages. */
object TemplateOutputPrefix {

  /** Filesystem path segment derived from a template root (strips `<language>/src/`). */
  def fromTemplateDirectory(templateDirectory: String): String =
    stripTemplateSourcePrefix(templateDirectory)

  /** Python import package derived from template layout and optional root namespace. */
  def toPackageName(templateDerivedPath: String, rootNamespace: Option[String]): String = {
    val segments =
      rootNamespace.toList.map(_.split("/").toList).flatten ++
        templateDerivedPath.split("/").toList
    segments.filter(_.nonEmpty).mkString(".")
  }

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
