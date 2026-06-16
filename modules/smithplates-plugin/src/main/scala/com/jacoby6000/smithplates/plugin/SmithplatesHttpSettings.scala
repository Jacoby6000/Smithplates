package com.jacoby6000.smithplates.plugin

import cats.syntax.all.*
import com.jacoby6000.smithplates.http.model.HttpServiceIr
import com.jacoby6000.smithplates.http.service.renderer.HttpServiceCodegenSettings
import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.model.InvalidPluginConfig

final case class SmithplatesHttpSettings(
    languageTargets: Map[String, HttpLanguageTarget]
) {
  def toServerCodegenSettings(languageId: String, serviceIr: HttpServiceIr): Option[HttpServiceCodegenSettings] =
    languageTargets.get(languageId).flatMap { target =>
      target.server.map { serverTarget =>
        val routeGroupTags =
          serviceIr.services.flatMap(_.routeGroups.map(_.tag)).distinct.sorted
        serverTarget.toCodegenSettings(
          languageId = languageId,
          routeGroupTags = routeGroupTags
        )
      }
    }

  def toClientCodegenSettings(languageId: String, serviceIr: HttpServiceIr): Option[HttpServiceCodegenSettings] =
    languageTargets.get(languageId).flatMap { target =>
      target.client.map { clientTarget =>
        val routeGroupTags =
          serviceIr.services.flatMap(_.routeGroups.map(_.tag)).distinct.sorted
        clientTarget.toCodegenSettings(
          languageId = languageId,
          routeGroupTags = routeGroupTags
        )
      }
    }
}

object SmithplatesHttpSettings {
  val SupportedWebFrameworks: Set[String] = Set("fastapi")
  val SupportedHttpLibraries: Set[String] = Set("httpx")

  def validate(settings: SmithplatesHttpSettings): SqlValidated[SmithplatesHttpSettings] =
    validateLanguageTargets(settings)

  private def validateLanguageTargets(
      settings: SmithplatesHttpSettings
  ): SqlValidated[SmithplatesHttpSettings] =
    settings.languageTargets.toList
      .traverse { case (languageId, target) =>
        validateTarget(languageId, target)
      }
      .map(_ => settings)

  private def validateTarget(
      languageId: String,
      target: HttpLanguageTarget
  ): SqlValidated[Unit] =
    if (target.server.isEmpty && target.client.isEmpty) {
      SqlValidated.invalid(
        InvalidPluginConfig(s"smithplates.$languageId.http requires `server` and/or `client`")
      )
    } else {
      (
        target.server match {
          case None         => ().validNel
          case Some(server) =>
            validateWebFramework(languageId, server).andThen(_ =>
              HttpLanguageTargetTemplateValidator.validateServer(languageId, server))
        },
        target.client match {
          case None         => ().validNel
          case Some(client) =>
            validateHttpLibrary(languageId, client).andThen(_ =>
              HttpLanguageTargetTemplateValidator.validateClient(languageId, client))
        }
      ).mapN((_, _) => ())
    }

  private def validateWebFramework(
      languageId: String,
      target: HttpServerTarget
  ): SqlValidated[Unit] =
    if (SupportedWebFrameworks.contains(target.webFramework)) {
      ().validNel
    } else {
      SqlValidated.invalid(
        InvalidPluginConfig(
          s"smithplates.$languageId.http.server.webFramework '${target.webFramework}' is not supported; " +
            s"supported values: ${SupportedWebFrameworks.toList.sorted.mkString(", ")}"
        )
      )
    }

  private def validateHttpLibrary(
      languageId: String,
      target: HttpClientTarget
  ): SqlValidated[Unit] =
    if (SupportedHttpLibraries.contains(target.httpLibrary)) {
      ().validNel
    } else {
      SqlValidated.invalid(
        InvalidPluginConfig(
          s"smithplates.$languageId.http.client.httpLibrary '${target.httpLibrary}' is not supported; " +
            s"supported values: ${SupportedHttpLibraries.toList.sorted.mkString(", ")}"
        )
      )
    }
}
