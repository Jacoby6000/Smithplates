package com.jacoby6000.smithplates.plugin

import cats.syntax.all.*
import com.jacoby6000.smithplates.plugin.config.PluginConfigLoader
import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.model.InvalidPluginConfig
import io.circe.Json
import software.amazon.smithy.model.node.ObjectNode

final case class SmithplatesSettings(
    sql: Option[SmithplatesSqlSettings],
    http: Option[SmithplatesHttpSettings]
)

object SmithplatesSettings {
  def parseJson(text: String): SqlValidated[SmithplatesSettings] =
    PluginConfigLoader.parseJson(text)

  def fromSmithyNode(node: ObjectNode): SqlValidated[SmithplatesSettings] =
    PluginConfigLoader.fromSmithyNode(node)

  def fromLanguageMap(configs: Map[String, Json]): SqlValidated[SmithplatesSettings] =
    configs.toList
      .traverse { case (languageId, json) =>
        if (json.isObject) {
          internal.parseLanguage(languageId, json).map(languageId -> _)
        } else {
          SqlValidated.invalid(
            InvalidPluginConfig(s"smithplates.$languageId must be an object")
          )
        }
      }
      .map { entries =>
        val configs     = entries.toMap
        val sqlTargets  = configs.collect {
          case (languageId, config) if config.sql.isDefined =>
            languageId -> SmithplatesSqlLanguageTarget(
              target = config.sql.get,
              enableExternalTemplates = config.enableExternalTemplates
            )
        }
        val httpTargets = configs.collect {
          case (languageId, config) if config.http.isDefined =>
            languageId -> SmithplatesHttpLanguageTarget(
              target = config.http.get,
              enableExternalTemplates = config.enableExternalTemplates
            )
        }

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

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def parseLanguage(
        languageId: String,
        json: Json
    ): SqlValidated[SmithplatesLanguageConfiguration] = {
      val enableExternalTemplates =
        PluginConfigMembers.optionalBooleanMember(json, "enableExternalTemplates").getOrElse(false)
      json.asObject.toList
        .flatMap(_.toList)
        .traverse { case (key, memberJson) =>
          key.toLowerCase match {
            case "enableexternaltemplates" =>
              None.validNel
            case "sourceoutputdir" | "testoutputdir" =>
              SqlValidated.invalid(
                InvalidPluginConfig(
                  s"smithplates.$languageId: `sourceOutputDir` and `testOutputDir` have been removed; " +
                    "specify them per-entry in the target's `outputs` array instead"
                )
              )
            case "sql"                                                           =>
              if (memberJson.isObject) {
                LanguageTarget
                  .parse(languageId, memberJson)
                  .map(target => Some(Left(target): Either[LanguageTarget, HttpLanguageTarget]))
              } else {
                SqlValidated.invalid(
                  InvalidPluginConfig(s"smithplates.$languageId.sql must be an object")
                )
              }
            case "http"                                                          =>
              if (memberJson.isObject) {
                HttpLanguageTarget
                  .parse(languageId, memberJson)
                  .map(target => Some(Right(target): Either[LanguageTarget, HttpLanguageTarget]))
              } else {
                SqlValidated.invalid(
                  InvalidPluginConfig(s"smithplates.$languageId.http must be an object")
                )
              }
            case other                                                           =>
              SqlValidated.invalid(
                InvalidPluginConfig(
                  s"smithplates.$languageId contains unknown key '$other'; expected `sql`, `http`, " +
                    "or `enableExternalTemplates`"
                )
              )
          }
        }
        .andThen { entries =>
          val sqlTargets  = entries.flatten.collect { case Left(target) => target }
          val httpTargets = entries.flatten.collect { case Right(target) => target }
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
            SmithplatesLanguageConfiguration(
              enableExternalTemplates = enableExternalTemplates,
              sql = sqlTargets.headOption,
              http = httpTargets.headOption
            ).validNel
          }
        }
    }
  }
}
