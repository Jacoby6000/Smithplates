package com.jacoby6000.smithplates.plugin

import cats.data.Validated
import cats.syntax.all.*
import com.jacoby6000.smithplates.http.HttpIrExtractor
import com.jacoby6000.smithplates.http.HttpValidated
import com.jacoby6000.smithplates.http.service.renderer.HttpServiceCodegenRenderer
import com.jacoby6000.smithplates.sql.SqlIrExtractor
import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.service.SqlServiceIrExtractor
import com.jacoby6000.smithplates.sql.service.renderer.SqlServiceCodegenRenderer
import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.build.SmithyBuildPlugin
import software.amazon.smithy.model.Model

import java.util.logging.Logger
import scala.util.control.NonFatal

final class SmithplatesBuildPlugin extends SmithyBuildPlugin {
  private val logger = Logger.getLogger(classOf[SmithplatesBuildPlugin].getName)

  override def getName: String = "smithplates"

  override def execute(context: PluginContext): Unit = {
    val model = context.getModel
    SmithplatesSettings.fromNode(context.getSettings) match {
      case Validated.Invalid(errors) =>
        throw new IllegalArgumentException(
          s"smithplates plugin failed validation: ${SqlValidated.toPluginExceptionMessage(errors)}"
        )
      case Validated.Valid(settings) =>
        settings.sql.foreach(executeSql(context, model, _))
        settings.http.foreach(executeHttp(context, model, _))
    }
  }

  private def executeSql(
      context: PluginContext,
      model: Model,
      sqlSettings: SmithplatesSqlSettings
  ): Unit =
    (
      SqlValidated.valid(sqlSettings),
      SqlIrExtractor.extract(model)
    ).mapN { (_, schema) =>
      val serviceIr = SqlServiceIrExtractor.extractOrThrow(model, schema)

      if (sqlSettings.schemaDialectOutputs.nonEmpty) {
        if (schema.tables.isEmpty) {
          logger.info("Skipping SQL schema generation: Smithy model contains no @sqlTable structures")
        } else {
          sqlSettings.schemaDialectOutputs.foreach { case (dialectKey, fileName) =>
            try {
              logger.info(s"Generating $dialectKey schema into '$fileName'")
              val sql = DialectRenderers.render(schema, serviceIr, dialectKey)
              context.getFileManifest.writeFile(fileName, sql)
            } catch {
              case NonFatal(ex) =>
                logger.severe(s"Failed writing $dialectKey schema: ${ex.getMessage}")
                throw ex
            }
          }
        }
      }

      sqlSettings.languageTargets.keySet.toList.sorted.foreach { languageId =>
        sqlSettings.toCodegenSettings(languageId) match {
          case Some(codegenSettings) =>
            if (serviceIr.services.isEmpty) {
              logger.info(
                s"Skipping $languageId SQL service codegen: Smithy model contains no @sqlService services"
              )
            } else {
              SqlServiceCodegenRenderer.render(model, schema, serviceIr, codegenSettings) match {
                case Validated.Valid(artifacts) =>
                  artifacts.foreach { artifact =>
                    writeArtifact(context, s"SQL service", artifact.relativePath, artifact.content)
                  }
                case Validated.Invalid(errors)  =>
                  throw new IllegalArgumentException(
                    s"smithplates plugin failed $languageId SQL codegen validation: ${SqlValidated.toPluginExceptionMessage(errors)}"
                  )
              }
            }
          case None                  => ()
        }
      }
    } match {
      case Validated.Valid(_)        => ()
      case Validated.Invalid(errors) =>
        throw new IllegalArgumentException(
          s"smithplates plugin failed SQL validation: ${SqlValidated.toPluginExceptionMessage(errors)}"
        )
    }

  private def executeHttp(
      context: PluginContext,
      model: Model,
      httpSettings: SmithplatesHttpSettings
  ): Unit = {
    val serviceIr = HttpIrExtractor.extractOrThrow(model)

    httpSettings.languageTargets.keySet.toList.sorted.foreach { languageId =>
      httpSettings.toCodegenSettings(languageId, serviceIr) match {
        case Some(codegenSettings) =>
          if (serviceIr.services.isEmpty) {
            logger.info(
              s"Skipping $languageId HTTP service codegen: Smithy model contains no @httpService services"
            )
          } else {
            HttpServiceCodegenRenderer.render(model, serviceIr, codegenSettings) match {
              case Validated.Valid(artifacts) =>
                artifacts.foreach { artifact =>
                  writeArtifact(context, "HTTP service", artifact.relativePath, artifact.content)
                }
              case Validated.Invalid(errors)  =>
                throw new IllegalArgumentException(
                  s"smithplates plugin failed $languageId HTTP codegen validation: ${HttpValidated.toPluginExceptionMessage(errors)}"
                )
            }
          }
        case None                  => ()
      }
    }
  }

  private def writeArtifact(
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
}
