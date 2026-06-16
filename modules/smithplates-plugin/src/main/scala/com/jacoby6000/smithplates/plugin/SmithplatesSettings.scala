package com.jacoby6000.smithplates.plugin

import cats.syntax.all.*
import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.model.InvalidPluginConfig
import software.amazon.smithy.model.node.ObjectNode

import scala.jdk.CollectionConverters.*

final case class SmithplatesLanguageSettings(
    sql: Option[LanguageTarget],
    http: Option[HttpLanguageTarget]
)

final case class SmithplatesSettings(
    sql: Option[SmithplatesSqlSettings],
    http: Option[SmithplatesHttpSettings]
)

object SmithplatesSettings {
  def fromNode(node: ObjectNode): SqlValidated[SmithplatesSettings] =
    node.getMembers.asScala.toList
      .traverse { case (keyNode, memberNode) =>
        val languageId = keyNode.expectStringNode().getValue
        if (memberNode.isObjectNode) {
          parseLanguage(languageId, memberNode.expectObjectNode()).map(languageId -> _)
        } else {
          SqlValidated.invalid(
            InvalidPluginConfig(s"smithplates.$languageId must be an object")
          )
        }
      }
      .map { entries =>
        val sqlTargets  = entries.flatMap { case (languageId, languageSettings) =>
          languageSettings.sql.map(languageId -> _)
        }.toMap
        val httpTargets = entries.flatMap { case (languageId, languageSettings) =>
          languageSettings.http.map(languageId -> _)
        }.toMap

        val sqlSettings  = Option.when(sqlTargets.nonEmpty)(SmithplatesSqlSettings(sqlTargets))
        val httpSettings = Option.when(httpTargets.nonEmpty)(SmithplatesHttpSettings(httpTargets))
        SmithplatesSettings(sql = sqlSettings, http = httpSettings)
      }
      .andThen { settings =>
        if (settings.sql.isEmpty && settings.http.isEmpty) {
          InvalidPluginConfig("smithplates plugin requires at least one language with `sql` or `http`").invalidNel
        } else {
          settings.validNel
        }
      }
      .andThen { settings =>
        settings.sql match {
          case Some(sqlSettings) => SmithplatesSqlSettings.validate(sqlSettings).map(_ => settings)
          case None              => settings.validNel
        }
      }
      .andThen { settings =>
        settings.http match {
          case Some(httpSettings) => SmithplatesHttpSettings.validate(httpSettings).map(_ => settings)
          case None               => settings.validNel
        }
      }

  private def parseLanguage(
      languageId: String,
      node: ObjectNode
  ): SqlValidated[SmithplatesLanguageSettings] =
    node.getMembers.asScala.toList
      .traverse { case (keyNode, memberNode) =>
        val key = keyNode.expectStringNode().getValue.toLowerCase
        key match {
          case "sql"  =>
            if (memberNode.isObjectNode) {
              LanguageTarget.parse(languageId, memberNode.expectObjectNode()).map(target => Left(target))
            } else {
              SqlValidated.invalid(
                InvalidPluginConfig(s"smithplates.$languageId.sql must be an object")
              )
            }
          case "http" =>
            if (memberNode.isObjectNode) {
              HttpLanguageTarget.parse(languageId, memberNode.expectObjectNode()).map(target => Right(target))
            } else {
              SqlValidated.invalid(
                InvalidPluginConfig(s"smithplates.$languageId.http must be an object")
              )
            }
          case other  =>
            SqlValidated.invalid(
              InvalidPluginConfig(
                s"smithplates.$languageId contains unknown key '$other'; expected `sql` or `http`"
              )
            )
        }
      }
      .andThen { entries =>
        val sqlTargets  = entries.collect { case Left(target) => target }
        val httpTargets = entries.collect { case Right(target) => target }
        if (sqlTargets.isEmpty && httpTargets.isEmpty) {
          SqlValidated.invalid(
            InvalidPluginConfig(s"smithplates.$languageId requires at least one of `sql` or `http`")
          )
        } else if (sqlTargets.size > 1) {
          SqlValidated.invalid(
            InvalidPluginConfig(s"smithplates.$languageId contains duplicate `sql` entries")
          )
        } else if (httpTargets.size > 1) {
          SqlValidated.invalid(
            InvalidPluginConfig(s"smithplates.$languageId contains duplicate `http` entries")
          )
        } else {
          SmithplatesLanguageSettings(
            sql = sqlTargets.headOption,
            http = httpTargets.headOption
          ).validNel
        }
      }
}
