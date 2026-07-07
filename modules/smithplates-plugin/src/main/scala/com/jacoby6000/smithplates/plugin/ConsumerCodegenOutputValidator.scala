package com.jacoby6000.smithplates.plugin

import cats.syntax.all.*
import com.jacoby6000.smithplates.codegen.core.CodegenValidated
import com.jacoby6000.smithplates.codegen.core.planning.BindingFilter
import com.jacoby6000.smithplates.codegen.core.planning.CodegenOutput
import com.jacoby6000.smithplates.codegen.core.planning.OutputId
import com.jacoby6000.smithplates.codegen.core.planning.SmithyBinding
import com.jacoby6000.smithplates.codegen.core.planning.config.CodegenOutputDeck
import com.jacoby6000.smithplates.codegen.core.planning.config.CodegenOutputDeckLoader
import com.jacoby6000.smithplates.http.service.renderer.HttpServiceCodegenApiArtifacts
import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.model.InvalidPluginConfig
import com.jacoby6000.smithplates.sql.service.renderer.SqlServiceCodegenDbArtifacts

/** Config-time validation for consumer `additionalTemplatesDirectory` decks. */
object ConsumerCodegenOutputValidator {
  def bundledDeckIds(deck: CodegenOutputDeck): Set[OutputId] =
    (deck.shared ++ deck.variants.values.flatten).map(_.id).toSet

  def validateAdditionalDeck(
      configPath: String,
      additionalOutputs: List[CodegenOutput],
      bundledIds: Set[OutputId],
      enableExternalTemplates: Boolean,
      templateClasspathExists: CodegenOutput.CodegenTemplateBindingOutput => Boolean
  ): SqlValidated[Unit] =
    (
      validateUniqueAdditionalIds(configPath, additionalOutputs),
      validateNoBundledIdCollisions(configPath, additionalOutputs, bundledIds),
      validateOverrideTargets(configPath, additionalOutputs, bundledIds),
      validateBindings(configPath, additionalOutputs),
      validateExternalTemplates(configPath, additionalOutputs, enableExternalTemplates, templateClasspathExists)
    ).mapN((_, _, _, _, _) => ())

  def validateSqlAdditionalDeck(
      languageId: String,
      target: LanguageTarget,
      enabledDialectKeys: List[String],
      enableExternalTemplates: Boolean
  ): SqlValidated[Unit] =
    target.additionalTemplatesDirectory match {
      case None                => ().validNel
      case Some(additionalDir) =>
        val configPath        = s"smithplates.$languageId.sql.additionalTemplatesDirectory"
        val templateDirectory = LanguageTargetTemplateValidator.resolveTemplateDirectory(target, languageId)
        (
          CodegenOutputDeckLoader.load(templateDirectory, getClass.getClassLoader),
          SqlServiceCodegenDbArtifacts.dialectArtifacts(templateDirectory, enabledDialectKeys),
          ConsumerCodegenOutputs.additionalOutputs(additionalDir, enabledDialectKeys, getClass.getClassLoader)
        ).mapN { (bundledDeck, bundledOutputs, additionalOutputs) =>
          validateAdditionalDeck(
            configPath = configPath,
            additionalOutputs = additionalOutputs,
            bundledIds = bundledDeckIds(bundledDeck),
            enableExternalTemplates = enableExternalTemplates,
            templateClasspathExists = template =>
              com.jacoby6000.smithplates.sql.service.renderer.ScalateSspTemplateEngine.classpathResourceExists(
                template.templatePath.stripPrefix("classpath:")
              )
          ).andThen(_ =>
            LanguageTargetTemplateValidator.internal.validateTemplatesExist(
              languageId = languageId,
              templateDirectory = templateDirectory,
              artifacts = ConsumerCodegenOutputs.compose(bundledOutputs, additionalOutputs)
            ))
        }.leftMap(_.map(error => InvalidPluginConfig(error.message)))
          .andThen(identity)
    }

  def validateHttpServerAdditionalDeck(
      languageId: String,
      target: HttpServerTarget,
      enableExternalTemplates: Boolean
  ): SqlValidated[Unit] =
    target.additionalTemplatesDirectory match {
      case None                => ().validNel
      case Some(additionalDir) =>
        val serverTemplateDirectory =
          HttpLanguageTargetTemplateValidator.resolveServerTemplateDirectory(target, languageId)
        val modelsTemplateDirectory = HttpLanguageTargetTemplateValidator.defaultModelsTemplateDirectory(languageId)
        val configPath              = s"smithplates.$languageId.http.server.additionalTemplatesDirectory"
        val enabledKeys             = List(target.webFramework)
        (
          internal.httpServerBundledDeck(serverTemplateDirectory, modelsTemplateDirectory),
          HttpServiceCodegenApiArtifacts.frameworkArtifacts(
            serverTemplateDirectory = serverTemplateDirectory,
            modelsTemplateDirectory = modelsTemplateDirectory,
            frameworkKeys = enabledKeys,
            emitModels = true
          ),
          ConsumerCodegenOutputs.additionalOutputs(additionalDir, enabledKeys, getClass.getClassLoader)
        ).mapN { (bundledDeck, bundledOutputs, additionalOutputs) =>
          validateAdditionalDeck(
            configPath = configPath,
            additionalOutputs = additionalOutputs,
            bundledIds = bundledDeckIds(bundledDeck),
            enableExternalTemplates = enableExternalTemplates,
            templateClasspathExists = internal.httpTemplateClasspathExists
          ).andThen(_ =>
            HttpLanguageTargetTemplateValidator.internal.validateRequiredArtifactsExist(
              languageId = languageId,
              defaultTemplateDirectory = serverTemplateDirectory,
              artifacts = ConsumerCodegenOutputs.compose(bundledOutputs, additionalOutputs)
            ))
        }.leftMap(_.map(error => InvalidPluginConfig(error.message)))
          .andThen(identity)
    }

  def validateHttpClientAdditionalDeck(
      languageId: String,
      target: HttpClientTarget,
      emitModels: Boolean,
      enableExternalTemplates: Boolean
  ): SqlValidated[Unit] =
    target.additionalTemplatesDirectory match {
      case None                => ().validNel
      case Some(additionalDir) =>
        val clientTemplateDirectory =
          HttpLanguageTargetTemplateValidator.resolveClientTemplateDirectory(target, languageId)
        val modelsTemplateDirectory = HttpLanguageTargetTemplateValidator.defaultModelsTemplateDirectory(languageId)
        val configPath              = s"smithplates.$languageId.http.client.additionalTemplatesDirectory"
        val enabledKeys             = List(target.httpLibrary)
        (
          internal.httpClientBundledDeck(clientTemplateDirectory, modelsTemplateDirectory, emitModels),
          com.jacoby6000.smithplates.http.service.renderer.HttpClientCodegenApiArtifacts.libraryArtifacts(
            clientTemplateDirectory,
            enabledKeys
          ),
          if (emitModels) {
            HttpServiceCodegenApiArtifacts.modelArtifacts(modelsTemplateDirectory)
          } else {
            List.empty[CodegenOutput].validNel
          },
          ConsumerCodegenOutputs.additionalOutputs(additionalDir, enabledKeys, getClass.getClassLoader)
        ).mapN { (bundledDeck, clientArtifacts, modelArtifacts, additionalOutputs) =>
          val bundledOutputs = clientArtifacts ++ modelArtifacts
          validateAdditionalDeck(
            configPath = configPath,
            additionalOutputs = additionalOutputs,
            bundledIds = bundledDeckIds(bundledDeck),
            enableExternalTemplates = enableExternalTemplates,
            templateClasspathExists = internal.httpTemplateClasspathExists
          ).andThen(_ =>
            HttpLanguageTargetTemplateValidator.internal.validateRequiredArtifactsExist(
              languageId = languageId,
              defaultTemplateDirectory = clientTemplateDirectory,
              artifacts = ConsumerCodegenOutputs.compose(bundledOutputs, additionalOutputs)
            ))
        }.leftMap(_.map(error => InvalidPluginConfig(error.message)))
          .andThen(identity)
    }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def httpServerBundledDeck(
        serverTemplateDirectory: String,
        modelsTemplateDirectory: String
    ): CodegenValidated[CodegenOutputDeck] =
      HttpServiceCodegenApiArtifacts.internal.deck(serverTemplateDirectory).andThen { serverDeck =>
        HttpServiceCodegenApiArtifacts.internal.deck(modelsTemplateDirectory).map { modelsDeck =>
          CodegenOutputDeck(
            shared = serverDeck.shared ++ modelsDeck.shared,
            variants = serverDeck.variants ++ modelsDeck.variants
          )
        }
      }

    def httpClientBundledDeck(
        clientTemplateDirectory: String,
        modelsTemplateDirectory: String,
        emitModels: Boolean
    ): CodegenValidated[CodegenOutputDeck] =
      HttpServiceCodegenApiArtifacts.internal.deck(clientTemplateDirectory).map { clientDeck =>
        if (emitModels) {
          HttpServiceCodegenApiArtifacts.internal
            .deck(modelsTemplateDirectory)
            .fold(
              _ => clientDeck,
              modelsDeck =>
                CodegenOutputDeck(
                  shared = clientDeck.shared ++ modelsDeck.shared,
                  variants = clientDeck.variants ++ modelsDeck.variants
                )
            )
        } else {
          clientDeck
        }
      }

    def httpTemplateClasspathExists(template: CodegenOutput.CodegenTemplateBindingOutput): Boolean =
      com.jacoby6000.smithplates.http.service.renderer.ScalateSspTemplateEngine.classpathResourceExists(
        template.templatePath.stripPrefix("classpath:")
      )
  }

  private def validateUniqueAdditionalIds(
      configPath: String,
      additionalOutputs: List[CodegenOutput]
  ): SqlValidated[Unit] = {
    val duplicates =
      additionalOutputs
        .groupBy(_.id)
        .collect { case (_, grouped) if grouped.size > 1 => grouped.head.id }
        .toList
    duplicates match {
      case duplicate :: _ =>
        SqlValidated.invalid(
          InvalidPluginConfig(
            s"$configPath outputs.json contains duplicate output id '${duplicate.value}'"
          )
        )
      case Nil            => ().validNel
    }
  }

  private def validateNoBundledIdCollisions(
      configPath: String,
      additionalOutputs: List[CodegenOutput],
      bundledIds: Set[OutputId]
  ): SqlValidated[Unit] = {
    val collisions = additionalOutputs.map(_.id).filter(bundledIds.contains)
    collisions match {
      case collision :: _ =>
        SqlValidated.invalid(
          InvalidPluginConfig(
            s"$configPath outputs.json output id '${collision.value}' collides with a bundled output id; " +
              "use a distinct id with `overrides` to replace a bundled output"
          )
        )
      case Nil            => ().validNel
    }
  }

  private def validateOverrideTargets(
      configPath: String,
      additionalOutputs: List[CodegenOutput],
      bundledIds: Set[OutputId]
  ): SqlValidated[Unit] = {
    val unknownOverrides =
      additionalOutputs.flatMap(_.overrides).filterNot(bundledIds.contains)
    unknownOverrides match {
      case unknown :: _ =>
        SqlValidated.invalid(
          InvalidPluginConfig(
            s"$configPath outputs.json references unknown bundled output override id '${unknown.value}'"
          )
        )
      case Nil          => ().validNel
    }
  }

  private def validateBindings(
      configPath: String,
      additionalOutputs: List[CodegenOutput]
  ): SqlValidated[Unit] =
    additionalOutputs.traverse_ { output =>
      output match {
        case template: CodegenOutput.CodegenTemplateBindingOutput =>
          template.binding match {
            case SmithyBinding.Operation(filters, _) =>
              BindingFilter.validateOperationFilters(filters) match {
                case Some(error) =>
                  SqlValidated.invalid(
                    InvalidPluginConfig(s"$configPath output '${output.id.value}': ${error.message}")
                  )
                case None        => ().validNel
              }
            case _                                   => ().validNel
          }
        case _: CodegenOutput.CodegenStaticOutput                 => ().validNel
      }
    }

  private def validateExternalTemplates(
      configPath: String,
      additionalOutputs: List[CodegenOutput],
      enableExternalTemplates: Boolean,
      templateClasspathExists: CodegenOutput.CodegenTemplateBindingOutput => Boolean
  ): SqlValidated[Unit] = {
    val missingClasspathTemplates =
      additionalOutputs.collect {
        case template: CodegenOutput.CodegenTemplateBindingOutput
            if SqlServiceCodegenDbArtifacts.isRenderedTemplate(template.templatePath) &&
              !templateClasspathExists(template) =>
          template
      }

    missingClasspathTemplates match {
      case template :: _ if !enableExternalTemplates =>
        SqlValidated.invalid(
          InvalidPluginConfig(
            s"$configPath output '${template.id.value}' references template '${template.templatePath}' " +
              "that is not on the classpath; set `enableExternalTemplates` to true on smithplates.<language> " +
              "to use external SSP templates"
          )
        )
      case _                                         => ().validNel
    }
  }
}
