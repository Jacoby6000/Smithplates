package com.jacoby6000.smithplates.plugin

import cats.syntax.all.*
import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.model.InvalidPluginConfig
import software.amazon.smithy.model.node.ObjectNode

import scala.jdk.CollectionConverters.*

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
          internal.parseLanguage(languageId, memberNode.expectObjectNode()).map(languageId -> _)
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
              sourceOutputDir = config.sourceOutputDir,
              testOutputDir = config.testOutputDir
            )
        }
        val httpTargets = configs.collect {
          case (languageId, config) if config.http.isDefined =>
            languageId -> SmithplatesHttpLanguageTarget(
              target = config.http.get,
              sourceOutputDir = config.sourceOutputDir,
              testOutputDir = config.testOutputDir
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
        node: ObjectNode
    ): SqlValidated[SmithplatesLanguageConfiguration] =
      (
        PluginConfigMembers.requiredStringMember(
          node,
          "sourceOutputDir",
          s"smithplates.$languageId requires `sourceOutputDir`"
        ),
        PluginConfigMembers.requiredStringMember(
          node,
          "testOutputDir",
          s"smithplates.$languageId requires `testOutputDir`"
        )
      ).mapN { (sourceOutputDir, testOutputDir) =>
        (sourceOutputDir, testOutputDir)
      }.andThen { case (sourceOutputDir, testOutputDir) =>
        node.getMembers.asScala.toList
          .traverse { case (keyNode, memberNode) =>
            val key = keyNode.expectStringNode().getValue.toLowerCase
            key match {
              case "sourceoutputdir" | "testoutputdir" =>
                None.validNel
              case "sql"                               =>
                if (memberNode.isObjectNode) {
                  LanguageTarget
                    .parse(languageId, memberNode.expectObjectNode())
                    .map(target => Some(Left(target): Either[LanguageTarget, HttpLanguageTarget]))
                } else {
                  SqlValidated.invalid(
                    InvalidPluginConfig(s"smithplates.$languageId.sql must be an object")
                  )
                }
              case "http"                              =>
                if (memberNode.isObjectNode) {
                  HttpLanguageTarget
                    .parse(languageId, memberNode.expectObjectNode())
                    .map(target => Some(Right(target): Either[LanguageTarget, HttpLanguageTarget]))
                } else {
                  SqlValidated.invalid(
                    InvalidPluginConfig(s"smithplates.$languageId.http must be an object")
                  )
                }
              case other                               =>
                SqlValidated.invalid(
                  InvalidPluginConfig(
                    s"smithplates.$languageId contains unknown key '$other'; expected `sql`, `http`, " +
                      "`sourceOutputDir`, or `testOutputDir`"
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
                sourceOutputDir = sourceOutputDir,
                testOutputDir = testOutputDir,
                sql = sqlTargets.headOption,
                http = httpTargets.headOption
              ).validNel
            }
          }
      }
  }
}
