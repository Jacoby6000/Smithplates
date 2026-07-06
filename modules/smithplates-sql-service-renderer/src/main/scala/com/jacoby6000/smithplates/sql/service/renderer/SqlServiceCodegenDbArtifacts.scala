package com.jacoby6000.smithplates.sql.service.renderer

import cats.syntax.all.*
import com.jacoby6000.smithplates.codegen.core.CodegenValidated
import com.jacoby6000.smithplates.codegen.core.planning.CodegenOutput
import com.jacoby6000.smithplates.codegen.core.planning.config.CodegenOutputDeckLoader

/** Composes bundled `@sqlService` DB artifacts from the `outputs.json` deck resource located beside each language's DB
  * templates. The deck data (ids, template paths, output paths) lives entirely in JSON, so this object holds only
  * language-neutral composition logic and no per-language paths.
  */
object SqlServiceCodegenDbArtifacts {
  def dialectArtifacts(templateDirectory: String, dialectKeys: List[String]): CodegenValidated[List[CodegenOutput]] =
    CodegenOutputDeckLoader
      .load(templateDirectory, getClass.getClassLoader)
      .andThen(_.forEnabled(dialectKeys))

  def forEnabledDialects(templateDirectory: String, dialectKeys: List[String]): List[CodegenOutput] =
    dialectArtifacts(templateDirectory, dialectKeys).fold(
      errors => throw new IllegalStateException(errors.map(_.message).toList.mkString("; ")),
      identity
    )

  def templatePath(output: CodegenOutput): Option[String] =
    output match {
      case template: CodegenOutput.CodegenTemplateBindingOutput => Some(template.templatePath)
      case _: CodegenOutput.CodegenStaticOutput                 => None
    }

  /** A deck template is rendered through Scalate when it is an `.ssp` template; any other bundled resource (e.g. a
    * runtime `.py`/`.pyi` support file) is copied verbatim. `.ssp` is the Scalate template extension and is not
    * language-specific.
    */
  def isRenderedTemplate(templatePath: String): Boolean =
    templatePath.endsWith(".ssp")
}
