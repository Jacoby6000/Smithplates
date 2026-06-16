package com.jacoby6000.smithplates.plugin

import cats.syntax.all.*
import com.jacoby6000.smithplates.http.model.HttpServiceIr
import com.jacoby6000.smithplates.http.service.renderer.HttpServiceCodegenSettings
import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.model.InvalidPluginConfig

final case class SmithplatesHttpSettings(
    languageTargets: Map[String, HttpLanguageTarget]
) {
  def toCodegenSettings(languageId: String, serviceIr: HttpServiceIr): Option[HttpServiceCodegenSettings] =
    languageTargets.get(languageId).map { target =>
      val routeGroupTags =
        serviceIr.services.flatMap(_.routeGroups.map(_.tag)).distinct.sorted
      target.toCodegenSettings(
        languageId = languageId,
        routeGroupTags = routeGroupTags
      )
    }
}

object SmithplatesHttpSettings {
  val SupportedWebFrameworks: Set[String] = Set("fastapi")

  def validate(settings: SmithplatesHttpSettings): SqlValidated[SmithplatesHttpSettings] =
    validateLanguageTargets(settings)

  private def validateLanguageTargets(
      settings: SmithplatesHttpSettings
  ): SqlValidated[SmithplatesHttpSettings] =
    settings.languageTargets.toList
      .traverse { case (languageId, target) =>
        validateWebFramework(languageId, target)
          .andThen(_ => HttpLanguageTargetTemplateValidator.validate(languageId, target))
      }
      .map(_ => settings)

  private def validateWebFramework(
      languageId: String,
      target: HttpLanguageTarget
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
}
