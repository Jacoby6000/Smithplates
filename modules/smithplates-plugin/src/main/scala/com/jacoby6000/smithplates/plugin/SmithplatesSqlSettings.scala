package com.jacoby6000.smithplates.plugin

import cats.syntax.all.*
import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.model.InvalidPluginConfig
import com.jacoby6000.smithplates.sql.service.renderer.SqlServiceCodegenSettings
import software.amazon.smithy.model.node.ObjectNode

final case class SqlDialectSettings(
    enabled: Boolean,
    migrationLocation: Option[String]
)

final case class SmithplatesSqlSettings(
    languageTargets: Map[String, SmithplatesSqlLanguageTarget]
) {
  def enabledDialectKeys: List[String] =
    SmithplatesSqlSettings.OrderedDialectKeys.filter { key =>
      languageTargets.values.exists(_.target.dialects.get(key).exists(_.enabled))
    }

  def dialectMigrationDirectories: Map[String, String] =
    enabledDialectKeys.flatMap { dialectKey =>
      languageTargets.values
        .flatMap(_.target.dialects.get(dialectKey).flatMap(_.migrationLocation))
        .toList
        .distinct
        .headOption
        .map(dialectKey -> _)
    }.toMap

  def toCodegenSettings(languageId: String): Option[SqlServiceCodegenSettings] =
    languageTargets.get(languageId).map { languageTarget =>
      val enabledKeys = languageTarget.target.enabledDialectKeys
      languageTarget.target.toCodegenSettings(
        languageId = languageId,
        enabledDialectKeys = enabledKeys,
        queryRenderers = DialectRenderers.queryRenderersForKeys(enabledKeys),
        schemaDdlRenderers = DialectRenderers.schemaDdlRenderersForKeys(enabledKeys),
        migrationDirectories = dialectMigrationDirectories.view.filterKeys(enabledKeys.toSet).toMap,
        sourceOutputDir = languageTarget.sourceOutputDir,
        testOutputDir = languageTarget.testOutputDir
      )
    }
}

object SmithplatesSqlSettings {
  val OrderedDialectKeys: List[String] = List("sqlite", "postgres")

  def validate(settings: SmithplatesSqlSettings): SqlValidated[SmithplatesSqlSettings] =
    (
      internal.validateLanguageTargets(settings),
      internal.validateMigrationDirectoryConflicts(settings)
    ).mapN((_, _) => settings)

  def parseDialect(
      key: String,
      node: ObjectNode
  ): SqlValidated[SqlDialectSettings] = {
    val enabled = PluginConfigMembers.optionalBooleanMember(node, "enable").getOrElse(false)
    PluginConfigMembers.optionalStringMember(node, "migrationLocation") match {
      case Some(location) if enabled =>
        internal.validateMigrationDirectory(key, location).map { directory =>
          SqlDialectSettings(enabled = true, migrationLocation = Some(directory))
        }
      case Some(location)            =>
        SqlDialectSettings(enabled = false, migrationLocation = Some(location)).validNel
      case None if enabled           =>
        SqlValidated.invalid(
          InvalidPluginConfig(
            s"smithplates language sql.$key requires `migrationLocation` when `enable` is true"
          )
        )
      case None                      =>
        SqlDialectSettings(enabled = false, migrationLocation = None).validNel
    }
  }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def validateLanguageTargets(
        settings: SmithplatesSqlSettings
    ): SqlValidated[SmithplatesSqlSettings] =
      settings.languageTargets.toList
        .traverse { case (languageId, languageTarget) =>
          LanguageTargetTemplateValidator.validate(
            languageId,
            languageTarget.target,
            languageTarget.target.enabledDialectKeys
          )
        }
        .map(_ => settings)

    def validateMigrationDirectoryConflicts(
        settings: SmithplatesSqlSettings
    ): SqlValidated[Unit] = {
      val conflicts =
        OrderedDialectKeys.flatMap { dialectKey =>
          val locations         =
            settings.languageTargets.toList.flatMap { case (languageId, languageTarget) =>
              languageTarget.target.dialects.get(dialectKey).flatMap {
                case SqlDialectSettings(true, Some(location)) => Some(languageId -> location)
                case _                                        => None
              }
            }
          val distinctLocations = locations.map(_._2).distinct
          Option.when(distinctLocations.size > 1)(dialectKey -> locations)
        }

      conflicts.traverse_ { case (dialectKey, locations) =>
        val details = locations.map { case (languageId, location) => s"$languageId=$location" }.mkString(", ")
        SqlValidated.invalid(
          InvalidPluginConfig(
            s"smithplates language SQL blocks configure conflicting $dialectKey migrationLocation values: $details"
          )
        )
      }
    }

    def validateMigrationDirectory(key: String, location: String): SqlValidated[String] =
      if (location.endsWith(".sql")) {
        SqlValidated.invalid(
          InvalidPluginConfig(
            s"smithplates language sql.$key `migrationLocation` must be a directory path for versioned migrations, not a .sql file: $location"
          )
        )
      } else {
        location.validNel
      }
  }
}
