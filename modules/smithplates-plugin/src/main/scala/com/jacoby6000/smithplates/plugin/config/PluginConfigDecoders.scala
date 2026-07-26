package com.jacoby6000.smithplates.plugin.config

import cats.syntax.all.*
import com.jacoby6000.smithplates.codegen.core.json.StrictJsonDecoding.makeStrict
import com.jacoby6000.smithplates.plugin.HttpClientTarget
import com.jacoby6000.smithplates.plugin.HttpLanguageTarget
import com.jacoby6000.smithplates.plugin.HttpServerTarget
import com.jacoby6000.smithplates.plugin.HttpServiceOutputEntry
import com.jacoby6000.smithplates.plugin.LanguageTarget
import com.jacoby6000.smithplates.plugin.SmithplatesSqlSettings
import com.jacoby6000.smithplates.plugin.SqlDialectSettings
import com.jacoby6000.smithplates.plugin.SqlServiceOutputEntry
import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.model.InvalidPluginConfig
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

/** Circe decoders for smithplates plugin configuration JSON. */
object PluginConfigDecoders {
  given Decoder[internal.SqlDialectJson] =
    deriveDecoder[internal.SqlDialectJson].makeStrict

  given Decoder[internal.SqlServiceOutputEntryJson] =
    deriveDecoder[internal.SqlServiceOutputEntryJson].makeStrict

  given Decoder[internal.HttpServerTargetJson] =
    deriveDecoder[internal.HttpServerTargetJson].makeStrict

  given Decoder[internal.HttpClientTargetJson] =
    deriveDecoder[internal.HttpClientTargetJson].makeStrict

  given Decoder[internal.HttpServiceOutputEntryJson] =
    deriveDecoder[internal.HttpServiceOutputEntryJson].makeStrict

  given Decoder[internal.HttpLanguageTargetJson] =
    deriveDecoder[internal.HttpLanguageTargetJson].makeStrict

  given Decoder[internal.LanguageTargetJson] =
    deriveDecoder[internal.LanguageTargetJson].makeStrict

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    final case class SqlDialectJson(
        enable: Option[Boolean] = None,
        migrationLocation: Option[String] = None
    ) {
      def toDomain(key: String): SqlValidated[SqlDialectSettings] = {
        val enabled = enable.getOrElse(false)
        migrationLocation match {
          case Some(location) if enabled =>
            SmithplatesSqlSettings.internal.validateMigrationDirectory(key, location).map { directory =>
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
    }

    final case class SqlServiceOutputEntryJson(
        services: Option[List[String]] = None,
        sourceOutputDir: Option[String] = None,
        testOutputDir: Option[String] = None,
        packageName: Option[String] = None
    ) {
      def toDomain: SqlValidated[SqlServiceOutputEntry] =
        (sourceOutputDir, testOutputDir) match {
          case (Some(src), Some(test)) =>
            SqlServiceOutputEntry(
              services = services,
              sourceOutputDir = src,
              testOutputDir = test,
              packageName = packageName
            ).validNel
          case (None, _)                =>
            SqlValidated.invalid(
              InvalidPluginConfig("smithplates sql output entry requires `sourceOutputDir`")
            )
          case (_, None)               =>
            SqlValidated.invalid(
              InvalidPluginConfig("smithplates sql output entry requires `testOutputDir`")
            )
        }
    }

    final case class LanguageTargetJson(
        sqlite: Option[SqlDialectJson] = None,
        postgres: Option[SqlDialectJson] = None,
        templateDirectory: Option[String] = None,
        additionalTemplatesDirectory: Option[String] = None,
        rootNamespace: Option[String] = None,
        packageName: Option[String] = None,
        outputs: List[SqlServiceOutputEntryJson] = Nil
    ) {
      def toDomain: SqlValidated[LanguageTarget] =
        (
          sqlite.traverse(_.toDomain("sqlite")),
          postgres.traverse(_.toDomain("postgres")),
          outputs.traverse(_.toDomain)
        ).mapN { (sqliteDialect, postgresDialect, outputEntries) =>
          LanguageTarget(
            dialects = List(
              sqliteDialect.map("sqlite" -> _),
              postgresDialect.map("postgres" -> _)
            ).flatten.toMap,
            templateDirectory = templateDirectory,
            additionalTemplatesDirectory = additionalTemplatesDirectory,
            rootNamespace = rootNamespace,
            packageName = packageName,
            outputs = outputEntries
          )
        }
    }

    final case class HttpServerTargetJson(
        webFramework: Option[String] = None,
        templateDirectory: Option[String] = None,
        additionalTemplatesDirectory: Option[String] = None,
        packageName: Option[String] = None
    ) {
      def toDomain: HttpServerTarget =
        HttpServerTarget(
          webFramework = webFramework,
          templateDirectory = templateDirectory,
          additionalTemplatesDirectory = additionalTemplatesDirectory,
          packageName = packageName
        )
    }

    final case class HttpClientTargetJson(
        httpLibrary: Option[String] = None,
        templateDirectory: Option[String] = None,
        additionalTemplatesDirectory: Option[String] = None,
        packageName: Option[String] = None
    ) {
      def toDomain: HttpClientTarget =
        HttpClientTarget(
          httpLibrary = httpLibrary,
          templateDirectory = templateDirectory,
          additionalTemplatesDirectory = additionalTemplatesDirectory,
          packageName = packageName
        )
    }

    final case class HttpServiceOutputEntryJson(
        services: Option[List[String]] = None,
        sourceOutputDir: Option[String] = None,
        testOutputDir: Option[String] = None,
        packageName: Option[String] = None
    ) {
      def toDomain: SqlValidated[HttpServiceOutputEntry] =
        (sourceOutputDir, testOutputDir) match {
          case (Some(src), Some(test)) =>
            HttpServiceOutputEntry(
              services = services,
              sourceOutputDir = src,
              testOutputDir = test,
              packageName = packageName
            ).validNel
          case (None, _)                =>
            SqlValidated.invalid(
              InvalidPluginConfig("smithplates http output entry requires `sourceOutputDir`")
            )
          case (_, None)               =>
            SqlValidated.invalid(
              InvalidPluginConfig("smithplates http output entry requires `testOutputDir`")
            )
        }
    }

    final case class HttpLanguageTargetJson(
        server: Option[HttpServerTargetJson] = None,
        client: Option[HttpClientTargetJson] = None,
        rootNamespace: Option[String] = None,
        modelsPackageName: Option[String] = None,
        outputs: List[HttpServiceOutputEntryJson] = Nil
    ) {
      def toDomain: SqlValidated[HttpLanguageTarget] =
        if (server.isEmpty && client.isEmpty) {
          SqlValidated.invalid(
            InvalidPluginConfig("smithplates.http requires `server` and/or `client`")
          )
        } else if (outputs.isEmpty) {
          SqlValidated.invalid(
            InvalidPluginConfig("smithplates.http requires at least one entry in `outputs`")
          )
        } else {
          outputs.traverse(_.toDomain).map { outputEntries =>
            HttpLanguageTarget(
              server = server.map(_.toDomain),
              client = client.map(_.toDomain),
              rootNamespace = rootNamespace,
              modelsPackageName = modelsPackageName,
              outputs = outputEntries
            )
          }
        }
    }
  }
}
