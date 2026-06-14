package com.jacoby6000.smithplates.plugin

import cats.syntax.all.*
import com.jacoby6000.smithplates.http.model.HttpServiceIr
import com.jacoby6000.smithplates.http.service.renderer.HttpServiceCodegenSettings
import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.model.InvalidPluginConfig
import software.amazon.smithy.model.node.ObjectNode

import scala.jdk.CollectionConverters.*

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

  def fromNode(node: ObjectNode): SqlValidated[SmithplatesHttpSettings] =
    node.getMembers.asScala.toList
      .traverse { case (keyNode, memberNode) =>
        val languageId = keyNode.expectStringNode().getValue
        if (memberNode.isObjectNode) {
          HttpLanguageTarget.parse(languageId, memberNode.expectObjectNode()).map(languageId -> _)
        } else {
          SqlValidated.invalid(
            InvalidPluginConfig(s"smithplates http.$languageId must be an object")
          )
        }
      }
      .map(_.toMap)
      .map(SmithplatesHttpSettings(_))
      .andThen(validateLanguageTargets)

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
          s"smithplates http.$languageId.server.webFramework '${target.webFramework}' is not supported; " +
            s"supported values: ${SupportedWebFrameworks.toList.sorted.mkString(", ")}"
        )
      )
    }
}
