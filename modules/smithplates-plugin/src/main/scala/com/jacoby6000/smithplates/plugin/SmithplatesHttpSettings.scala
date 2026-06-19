package com.jacoby6000.smithplates.plugin

import cats.syntax.all.*
import com.jacoby6000.smithplates.http.model.HttpServiceIr
import com.jacoby6000.smithplates.http.service.renderer.HttpServiceCodegenSettings
import com.jacoby6000.smithplates.sql.SqlValidated

final case class SmithplatesHttpSettings(
    languageTargets: Map[String, SmithplatesHttpLanguageTarget]
) {
  def toServerCodegenSettings(languageId: String, serviceIr: HttpServiceIr): Option[HttpServiceCodegenSettings] =
    languageTargets.get(languageId).flatMap { languageTarget =>
      languageTarget.target.server.map { serverTarget =>
        val context =
          SmithplatesHttpSettings.sharedHttpCodegenContext(languageId, languageTarget, serviceIr, emitModels = true)
        serverTarget.toCodegenSettings(
          languageId = languageId,
          routeGroupTags = context.routeGroupTags,
          rootNamespace = context.rootNamespace,
          modelsPackageNameOverride = context.modelsPackageNameOverride,
          emitModels = context.emitModels,
          sourceOutputDir = languageTarget.sourceOutputDir,
          testOutputDir = languageTarget.testOutputDir
        )
      }
    }

  def toClientCodegenSettings(languageId: String, serviceIr: HttpServiceIr): Option[HttpServiceCodegenSettings] =
    languageTargets.get(languageId).flatMap { languageTarget =>
      languageTarget.target.client.map { clientTarget =>
        val context = SmithplatesHttpSettings.sharedHttpCodegenContext(
          languageId,
          languageTarget,
          serviceIr,
          emitModels = languageTarget.target.server.isEmpty
        )
        clientTarget.toCodegenSettings(
          languageId = languageId,
          routeGroupTags = context.routeGroupTags,
          rootNamespace = context.rootNamespace,
          modelsPackageNameOverride = context.modelsPackageNameOverride,
          emitModels = context.emitModels,
          sourceOutputDir = languageTarget.sourceOutputDir,
          testOutputDir = languageTarget.testOutputDir
        )
      }
    }
}

object SmithplatesHttpSettings {
  val SupportedWebFrameworks: Set[String] = Set("fastapi")
  val SupportedHttpLibraries: Set[String] = Set("httpx")

  final private[plugin] case class SharedHttpCodegenContext(
      routeGroupTags: List[String],
      rootNamespace: Option[String],
      modelsPackageNameOverride: Option[String],
      emitModels: Boolean
  )

  def validate(settings: SmithplatesHttpSettings): SqlValidated[SmithplatesHttpSettings] =
    settings.languageTargets.toList
      .traverse { case (languageId, languageTarget) =>
        internal.validateTarget(languageId, languageTarget.target)
      }
      .map(_ => settings)

  private[plugin] def sharedHttpCodegenContext(
      languageId: String,
      languageTarget: SmithplatesHttpLanguageTarget,
      serviceIr: HttpServiceIr,
      emitModels: Boolean
  ): SharedHttpCodegenContext =
    SharedHttpCodegenContext(
      routeGroupTags = serviceIr.services.flatMap(_.routeGroups.map(_.tag)).distinct.sorted,
      rootNamespace = PluginConstants.resolvedRootNamespace(languageId, languageTarget.target.rootNamespace),
      modelsPackageNameOverride = languageTarget.target.modelsPackageName,
      emitModels = emitModels
    )

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def validateTarget(
        languageId: String,
        target: HttpLanguageTarget
    ): SqlValidated[Unit] =
      (
        target.server match {
          case None         => ().validNel
          case Some(server) =>
            PluginConstants
              .validateSupportedValue(
                languageId,
                "http.server.webFramework",
                server.webFramework,
                SupportedWebFrameworks
              )
              .andThen(_ => HttpLanguageTargetTemplateValidator.validateServer(languageId, server))
        },
        target.client match {
          case None         => ().validNel
          case Some(client) =>
            PluginConstants
              .validateSupportedValue(
                languageId,
                "http.client.httpLibrary",
                client.httpLibrary,
                SupportedHttpLibraries
              )
              .andThen(_ =>
                HttpLanguageTargetTemplateValidator.validateClient(
                  languageId,
                  client,
                  emitModels = target.server.isEmpty
                ))
        }
      ).mapN((_, _) => ())
  }
}
