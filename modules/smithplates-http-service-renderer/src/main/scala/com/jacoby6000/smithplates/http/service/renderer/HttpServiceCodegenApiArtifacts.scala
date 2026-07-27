package com.jacoby6000.smithplates.http.service.renderer

import cats.syntax.all.*
import com.jacoby6000.smithplates.codegen.core.CodegenValidated
import com.jacoby6000.smithplates.codegen.core.planning.CodegenOutput
import com.jacoby6000.smithplates.codegen.core.planning.config.CodegenOutputDeckLoader

/** Composes bundled `@httpService` server artifacts from `outputs.json` deck resources located beside each language's
  * templates. The deck data (ids, template paths, output paths, bindings) lives entirely in JSON, so this object holds
  * only language-neutral composition logic and no per-language paths.
  */
object HttpServiceCodegenApiArtifacts {
  def frameworkArtifacts(
      serverTemplateDirectory: String,
      modelsTemplateDirectory: String,
      frameworkKeys: List[String],
      emitModels: Boolean
  ): CodegenValidated[List[CodegenOutput]] =
    (
      internal.enabled(serverTemplateDirectory, frameworkKeys),
      if (emitModels) modelArtifacts(modelsTemplateDirectory) else List.empty[CodegenOutput].validNel
    ).mapN(_ ++ _)

  def modelArtifacts(modelsTemplateDirectory: String): CodegenValidated[List[CodegenOutput]] =
    internal.deck(modelsTemplateDirectory).map(_.shared)

  def forEnabledFrameworks(
      serverTemplateDirectory: String,
      modelsTemplateDirectory: String,
      frameworkKeys: List[String],
      emitModels: Boolean
  ): List[CodegenOutput] =
    internal.orThrow(frameworkArtifacts(serverTemplateDirectory, modelsTemplateDirectory, frameworkKeys, emitModels))

  def sharedModels(modelsTemplateDirectory: String): List[CodegenOutput] =
    internal.orThrow(modelArtifacts(modelsTemplateDirectory))

  def templatePath(output: CodegenOutput): Option[String] =
    output match {
      case template: CodegenOutput.CodegenTemplateBindingOutput => Some(template.templatePath)
      case _: CodegenOutput.CodegenStaticOutput                 => None
    }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def deck(templateDirectory: String) =
      CodegenOutputDeckLoader.load(templateDirectory, getClass.getClassLoader)

    def enabled(templateDirectory: String, enabledKeys: List[String]): CodegenValidated[List[CodegenOutput]] =
      deck(templateDirectory).andThen(_.forEnabled(enabledKeys))

    def orThrow(artifacts: CodegenValidated[List[CodegenOutput]]): List[CodegenOutput] =
      artifacts.fold(
        errors => throw new IllegalStateException(errors.map(_.message).toList.mkString("; ")),
        identity
      )
  }
}

final case class HttpServiceCodegenSettings(
    templateDirectory: String,
    defaultFrameworkKey: String,
    enabledFrameworkKeys: List[String],
    sourceOutputDirectory: Option[String] = None,
    testOutputDirectory: Option[String] = None,
    artifacts: List[CodegenOutput],
    rootNamespace: Option[String],
    packageNameOverride: Option[String] = None,
    modelsPackageNameOverride: Option[String] = None,
    emitModels: Boolean = true,
    modelTemplateDirectory: Option[String] = None,
    serviceFilter: Option[Set[String]] = None
) {
  def resolvedModelTemplateDirectory: String =
    modelTemplateDirectory.getOrElse(templateDirectory)
}
