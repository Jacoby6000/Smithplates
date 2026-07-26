package com.jacoby6000.smithplates.plugin

import cats.data.Validated
import cats.syntax.all.*
import com.jacoby6000.smithplates.http.HttpIrExtractor
import com.jacoby6000.smithplates.http.HttpValidated
import com.jacoby6000.smithplates.http.service.renderer.HttpServiceCodegenRenderer
import com.jacoby6000.smithplates.http.transform.HttpProblemHttpErrorModelTransformer
import com.jacoby6000.smithplates.sql.SqlIrExtractor
import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.service.SqlServiceIrExtractor
import com.jacoby6000.smithplates.sql.service.renderer.SqlCodegenMigrationBuilder
import com.jacoby6000.smithplates.sql.service.renderer.SqlServiceCodegenRenderer
import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.build.SmithyBuildPlugin
import software.amazon.smithy.model.Model

import java.util.logging.Logger
import scala.util.control.NonFatal

final class SmithplatesBuildPlugin extends SmithyBuildPlugin {
  override def getName: String = "smithplates"

  override def execute(context: PluginContext): Unit = {
    val model = HttpProblemHttpErrorModelTransformer.transform(context.getModel)
    SmithplatesSettings.fromSmithyNode(context.getSettings) match {
      case Validated.Invalid(errors) =>
        throw new IllegalArgumentException(
          s"smithplates plugin failed validation: ${SqlValidated.toPluginExceptionMessage(errors)}"
        )
      case Validated.Valid(settings) =>
        SmithplatesBuildPlugin.internal.warnWhenExternalTemplatesEnabled(settings)
        settings.sql.foreach(SmithplatesBuildPlugin.internal.executeSql(context, model, _))
        settings.http.foreach(SmithplatesBuildPlugin.internal.executeHttp(context, model, _))
    }
  }
}

object SmithplatesBuildPlugin {

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    val logger: Logger = Logger.getLogger(classOf[SmithplatesBuildPlugin].getName)

    def warnWhenExternalTemplatesEnabled(settings: SmithplatesSettings): Unit = {
      val externalEnabled =
        settings.sql.exists(_.languageTargets.values.exists(_.enableExternalTemplates)) ||
          settings.http.exists(_.languageTargets.values.exists(_.enableExternalTemplates))
      if (externalEnabled) {
        logger.warning(
          "smithplates enableExternalTemplates is enabled: external SSP templates execute arbitrary Scala at build time"
        )
      }
    }

    def executeSql(
        context: PluginContext,
        model: Model,
        sqlSettings: SmithplatesSqlSettings
    ): Unit =
      (
        SqlValidated.valid(sqlSettings),
        SqlIrExtractor.extract(model)
      ).mapN { (_, schema) =>
        val serviceIr = SqlServiceIrExtractor.extractOrThrow(model, schema)

        if (sqlSettings.dialectMigrationDirectories.nonEmpty) {
          if (schema.tables.isEmpty) {
            logger.info("Skipping SQL schema generation: Smithy model contains no @sqlTable structures")
          } else {
            sqlSettings.dialectMigrationDirectories.foreach { case (dialectKey, migrationDirectory) =>
              try {
                val ddlOnly           = DialectRenderers.renderDdlOnly(schema, dialectKey)
                val migrationFileName =
                  s"${normalizeDirectory(migrationDirectory)}/${SqlCodegenMigrationBuilder.InitialMigrationFileName}"
                logger.info(s"Generating $dialectKey initial migration into '$migrationFileName'")
                context.getFileManifest.writeFile(migrationFileName, ddlOnly)
              } catch {
                case NonFatal(ex) =>
                  logger.severe(s"Failed writing $dialectKey schema: ${ex.getMessage}")
                  throw ex
              }
            }
          }
        }

        sqlSettings.languageTargets.keySet.toList.sorted.foreach { languageId =>
          val languageTarget = sqlSettings.languageTargets(languageId)
          languageTarget.target.outputs.zipWithIndex.foreach { case (entry, index) =>
            val overriddenTarget   = languageTarget.withOutputEntryOverrides(entry)
            val overriddenSettings = SmithplatesSqlSettings(
              sqlSettings.languageTargets.updated(languageId, overriddenTarget)
            )
            val serviceFilter      = entry.services.map(_.toSet)
            val entryLabel         = s"output[${index}] (services=${entry.services.getOrElse(Nil).mkString(", ")})"
            if (serviceIr.services.isEmpty) {
              logger.info(
                s"Skipping $languageId SQL service codegen: Smithy model contains no @sqlService services"
              )
            } else {
              overriddenSettings.toCodegenSettings(
                languageId,
                entry.sourceOutputDir,
                entry.testOutputDir,
                serviceFilter) match {
                case Validated.Valid(Some(codegenSettings)) =>
                  SqlServiceCodegenRenderer.render(model, schema, serviceIr, codegenSettings) match {
                    case Validated.Valid(artifacts) =>
                      artifacts.foreach { artifact =>
                        writeArtifact(context, s"SQL service $entryLabel", artifact.relativePath, artifact.content)
                      }
                    case Validated.Invalid(errors)  =>
                      throw new IllegalArgumentException(
                        s"smithplates plugin failed $languageId SQL codegen validation: ${SqlValidated.toPluginExceptionMessage(errors)}"
                      )
                  }
                case Validated.Valid(None)                  => ()
                case Validated.Invalid(errors)              =>
                  throw new IllegalArgumentException(
                    s"smithplates plugin failed $languageId SQL codegen settings: ${SqlValidated.toPluginExceptionMessage(errors)}"
                  )
              }
            }
          }
        }
      } match {
        case Validated.Valid(_)        => ()
        case Validated.Invalid(errors) =>
          throw new IllegalArgumentException(
            s"smithplates plugin failed SQL validation: ${SqlValidated.toPluginExceptionMessage(errors)}"
          )
      }

    def executeHttp(
        context: PluginContext,
        model: Model,
        httpSettings: SmithplatesHttpSettings
    ): Unit = {
      val serviceIr = HttpIrExtractor.extractOrThrow(model)
      serviceIr.warnings.foreach(warning => logger.warning(warning.message))

      httpSettings.languageTargets.keySet.toList.sorted.foreach { languageId =>
        if (serviceIr.services.isEmpty) {
          logger.info(
            s"Skipping $languageId HTTP codegen: Smithy model contains no @httpService services"
          )
        } else {
          val languageTarget = httpSettings.languageTargets(languageId)
          languageTarget.target.outputs.zipWithIndex.foreach { case (entry, index) =>
            val overriddenTarget   = languageTarget.withOutputEntryOverrides(entry)
            val overriddenSettings = SmithplatesHttpSettings(
              httpSettings.languageTargets.updated(languageId, overriddenTarget)
            )
            val serviceFilter      = entry.services.map(_.toSet)
            val entryLabel         = s"output[${index}] (services=${entry.services.getOrElse(Nil).mkString(", ")})"
            runHttpCodegen(
              context,
              model,
              serviceIr,
              overriddenSettings,
              languageId,
              entry.sourceOutputDir,
              entry.testOutputDir,
              serviceFilter,
              entryLabel
            )
          }
        }
      }
    }

    def runHttpCodegen(
        context: PluginContext,
        model: Model,
        serviceIr: com.jacoby6000.smithplates.http.model.HttpServiceIr,
        httpSettings: SmithplatesHttpSettings,
        languageId: String,
        sourceOutputDir: String,
        testOutputDir: String,
        serviceFilter: Option[Set[String]],
        entryLabel: String
    ): Unit = {
      val labelSuffix = if (entryLabel.isEmpty) "" else s" $entryLabel"
      httpSettings.toServerCodegenSettings(languageId, serviceIr, sourceOutputDir, testOutputDir, serviceFilter) match {
        case Validated.Valid(Some(codegenSettings)) =>
          renderHttpArtifacts(context, model, serviceIr, languageId, s"HTTP service$labelSuffix", codegenSettings)
        case Validated.Valid(None)                  => ()
        case Validated.Invalid(errors)              =>
          throw new IllegalArgumentException(
            s"smithplates plugin failed $languageId HTTP server codegen settings: ${SqlValidated.toPluginExceptionMessage(errors)}"
          )
      }
      httpSettings.toClientCodegenSettings(languageId, serviceIr, sourceOutputDir, testOutputDir, serviceFilter) match {
        case Validated.Valid(Some(codegenSettings)) =>
          renderHttpArtifacts(context, model, serviceIr, languageId, s"HTTP client$labelSuffix", codegenSettings)
        case Validated.Valid(None)                  => ()
        case Validated.Invalid(errors)              =>
          throw new IllegalArgumentException(
            s"smithplates plugin failed $languageId HTTP client codegen settings: ${SqlValidated.toPluginExceptionMessage(errors)}"
          )
      }
    }

    def renderHttpArtifacts(
        context: PluginContext,
        model: Model,
        serviceIr: com.jacoby6000.smithplates.http.model.HttpServiceIr,
        languageId: String,
        label: String,
        codegenSettings: com.jacoby6000.smithplates.http.service.renderer.HttpServiceCodegenSettings
    ): Unit =
      HttpServiceCodegenRenderer.render(model, codegenSettings) match {
        case Validated.Valid(artifacts) =>
          artifacts.foreach { artifact =>
            writeArtifact(context, label, artifact.relativePath, artifact.content)
          }
        case Validated.Invalid(errors)  =>
          throw new IllegalArgumentException(
            s"smithplates plugin failed $languageId $label codegen validation: ${HttpValidated.toPluginExceptionMessage(errors)}"
          )
      }

    def writeArtifact(
        context: PluginContext,
        label: String,
        relativePath: String,
        content: String
    ): Unit =
      try {
        logger.info(s"Generating $label artifact '$relativePath'")
        val _ = context.getFileManifest.writeFile(relativePath, content)
      } catch {
        case NonFatal(ex) =>
          logger.severe(s"Failed writing $label artifact '$relativePath': ${ex.getMessage}")
          throw ex
      }

    def normalizeDirectory(directory: String): String =
      directory.stripSuffix("/").stripSuffix("\\")
  }
}
