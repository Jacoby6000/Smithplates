package com.jacoby6000.smithplates.plugin

import com.jacoby6000.smithplates.codegen.core.CodegenValidated
import com.jacoby6000.smithplates.codegen.core.planning.CodegenOutput
import com.jacoby6000.smithplates.codegen.core.planning.CodegenTemplatePaths
import com.jacoby6000.smithplates.codegen.core.planning.config.CodegenOutputDeckLoader

/** Loads a consumer `additionalTemplatesDirectory` deck and appends its outputs to the bundled deck.
  *
  * A consumer directory ships an `outputs.json` beside its templates, exactly like a bundled language template root.
  * Its outputs are appended after the bundled outputs; the
  * [[com.jacoby6000.smithplates.codegen.core.planning.CodegenPlanner]] then resolves `overrides` by id and rejects
  * duplicate resolved paths. Because the consumer templates live under a different directory than the bundled ones,
  * each output's template/resource path is rewritten to an absolute `classpath:` path anchored at the additional
  * directory so renderers resolve it against that directory.
  */
object ConsumerCodegenOutputs {
  def compose(bundled: List[CodegenOutput], additional: List[CodegenOutput]): List[CodegenOutput] =
    bundled ++ additional

  /** Loads the additional directory's deck (tolerating missing variants) and qualifies each output's template path. */
  def additionalOutputs(
      additionalTemplatesDirectory: String,
      enabledKeys: List[String],
      classLoader: ClassLoader
  ): CodegenValidated[List[CodegenOutput]] =
    CodegenOutputDeckLoader
      .load(additionalTemplatesDirectory, classLoader)
      .map(deck => deck.forAvailable(enabledKeys).map(qualify(additionalTemplatesDirectory, _)))

  /** Rewrites a consumer output's relative template/resource path to an absolute `classpath:` path anchored at the
    * additional directory, so it is resolved against that directory rather than the bundled template root.
    */
  def qualify(additionalTemplatesDirectory: String, output: CodegenOutput): CodegenOutput = {
    val base = normalizedBase(additionalTemplatesDirectory)
    output match {
      case template: CodegenOutput.CodegenTemplateBindingOutput =>
        template.copy(templatePath = absolutePath(base, template.templatePath))
      case static: CodegenOutput.CodegenStaticOutput            =>
        static.copy(filePath = absolutePath(base, static.filePath))
    }
  }

  def isAdditionalTemplatePath(templatePath: String): Boolean =
    CodegenTemplatePaths.isClasspathQualified(templatePath)

  private def normalizedBase(additionalTemplatesDirectory: String): String =
    s"classpath:${additionalTemplatesDirectory.stripPrefix("classpath:").stripSuffix("/")}"

  private def absolutePath(base: String, relative: String): String =
    if (CodegenTemplatePaths.isClasspathQualified(relative)) {
      relative
    } else {
      s"$base/${relative.stripPrefix("/")}"
    }
}
