package com.jacoby6000.smithplates

import cats.syntax.all.*
import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.codegen.SqlServiceCodegenSettings
import com.jacoby6000.smithplates.sql.model.InvalidPluginConfig
import com.jacoby6000.smithplates.sql.shared.SqlShared
import software.amazon.smithy.model.node.ObjectNode

import scala.jdk.CollectionConverters.*

final case class SqlDialectSettings(
    enabled: Boolean,
    migrationLocation: Option[String]
)

final case class SmithplatesSqlSettings(
    dialects: Map[String, SqlDialectSettings],
    languageTargets: Map[String, LanguageTarget]
) {
  def enabledDialectKeys: List[String] =
    SmithplatesSqlSettings.OrderedDialectKeys.filter(key => dialects.get(key).exists(_.enabled))

  def schemaDialectOutputs: Map[String, String] =
    enabledDialectKeys.flatMap { key =>
      dialects.get(key).flatMap(_.migrationLocation).map(key -> _)
    }.toMap

  def toCodegenSettings(languageId: String): Option[SqlServiceCodegenSettings] = {
    val enabledKeys = enabledDialectKeys
    languageTargets.get(languageId).map { target =>
      target.toCodegenSettings(
        languageId = languageId,
        enabledDialectKeys = enabledKeys,
        queryRenderers = DialectRenderers.queryRenderersForKeys(enabledKeys),
        schemaDdlRenderers = DialectRenderers.schemaDdlRenderersForKeys(enabledKeys)
      )
    }
  }
}

object SmithplatesSqlSettings {
  private val DialectKeys: Set[String] = Set("sqlite", "postgres")

  val OrderedDialectKeys: List[String] = List("sqlite", "postgres")

  def fromNode(node: ObjectNode): SqlValidated[SmithplatesSqlSettings] =
    node.getMembers.asScala.toList
      .traverse { case (keyNode, memberNode) =>
        val key = keyNode.expectStringNode().getValue.toLowerCase
        key match {
          case "languagetargets"                              =>
            if (memberNode.isObjectNode) {
              parseLanguageTargets(memberNode.expectObjectNode()).map(targets => Right(targets))
            } else {
              SqlValidated.invalid(
                InvalidPluginConfig("smithplates sql.languageTargets must be an object")
              )
            }
          case dialectKey if DialectKeys.contains(dialectKey) =>
            if (memberNode.isObjectNode) {
              parseDialect(dialectKey, memberNode.expectObjectNode()).map(settings => Left(dialectKey -> settings))
            } else {
              SqlValidated.invalid(
                InvalidPluginConfig(s"smithplates sql.$dialectKey must be an object")
              )
            }
          case other                                          =>
            SqlValidated.invalid(
              InvalidPluginConfig(
                s"smithplates sql contains unknown key '$other'; expected dialect (sqlite, postgres) or languageTargets"
              )
            )
        }
      }
      .map { entries =>
        val dialects        =
          entries.collect { case Left((key, settings)) => key -> settings }.toMap
        val languageTargets =
          entries.collect { case Right(targets) => targets }.foldLeft(Map.empty[String, LanguageTarget])(_ ++ _)
        SmithplatesSqlSettings(dialects = dialects, languageTargets = languageTargets)
      }
      .andThen(validateLanguageTargets)

  private def validateLanguageTargets(
      settings: SmithplatesSqlSettings
  ): SqlValidated[SmithplatesSqlSettings] =
    settings.languageTargets.toList
      .traverse { case (languageId, target) =>
        LanguageTargetTemplateValidator.validate(languageId, target, settings.enabledDialectKeys)
      }
      .map(_ => settings)

  private def parseLanguageTargets(node: ObjectNode): SqlValidated[Map[String, LanguageTarget]] =
    node.getMembers.asScala.toList
      .traverse { case (keyNode, memberNode) =>
        val languageId = keyNode.expectStringNode().getValue
        if (memberNode.isObjectNode) {
          LanguageTarget.parse(languageId, memberNode.expectObjectNode()).map(languageId -> _)
        } else {
          SqlValidated.invalid(
            InvalidPluginConfig(s"smithplates sql.languageTargets.$languageId must be an object")
          )
        }
      }
      .map(_.toMap)

  private def parseDialect(
      key: String,
      node: ObjectNode
  ): SqlValidated[SqlDialectSettings] = {
    val enabled = optionalBooleanMember(node, "enable").getOrElse(false)
    optionalStringMember(node, "migrationLocation") match {
      case Some(location) if enabled =>
        SqlDialectSettings(enabled = true, migrationLocation = Some(location)).validNel
      case Some(location)            =>
        SqlDialectSettings(enabled = false, migrationLocation = Some(location)).validNel
      case None if enabled           =>
        SqlValidated.invalid(
          InvalidPluginConfig(
            s"smithplates sql.$key requires `migrationLocation` when `enable` is true"
          )
        )
      case None                      =>
        SqlDialectSettings(enabled = false, migrationLocation = None).validNel
    }
  }

  private def optionalBooleanMember(node: ObjectNode, memberName: String): Option[Boolean] =
    Option(node.getMember(memberName).orElse(null)).flatMap {
      case value if value.isBooleanNode => Some(value.expectBooleanNode().getValue)
      case _                            => None
    }

  private def optionalStringMember(node: ObjectNode, memberName: String): Option[String] =
    Option(node.getMember(memberName).orElse(null)).flatMap {
      case value if value.isStringNode => SqlShared.trimmedNonEmpty(value.expectStringNode().getValue)
      case _                           => None
    }
}
