package com.jacoby6000.smithy.stache

import cats.data.Validated
import cats.syntax.all.*
import com.jacoby6000.smithy.stache.sql.SqlIrExtractor
import com.jacoby6000.smithy.stache.sql.SqlServiceIrExtractor
import com.jacoby6000.smithy.stache.sql.SqlValidated
import com.jacoby6000.smithy.stache.sql.codegen.SqlServiceCodegenRenderer
import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.build.SmithyBuildPlugin

import java.util.logging.Logger
import scala.util.control.NonFatal

final class SmithyStacheBuildPlugin extends SmithyBuildPlugin {
  private val logger = Logger.getLogger(classOf[SmithyStacheBuildPlugin].getName)

  override def getName: String = "smithy-stache"

  override def execute(context: PluginContext): Unit = {
    val model = context.getModel

    (
      SmithyStacheSettings.fromNode(context.getSettings),
      SqlIrExtractor.extract(model)
    ).mapN { (settings, schema) =>
      val serviceIr = SqlServiceIrExtractor.extractOrThrow(model, schema)

      if (settings.sql.schemaDialectOutputs.nonEmpty) {
        if (schema.tables.isEmpty) {
          logger.info("Skipping SQL schema generation: Smithy model contains no @sqlTable structures")
        } else {
          settings.sql.schemaDialectOutputs.foreach { case (dialect, fileName) =>
            try {
              logger.info(s"Generating ${dialect.key} schema into '$fileName'")
              val renderer = DialectRenderers.forDialect(dialect)
              val sql      = renderer.render(schema, serviceIr)
              context.getFileManifest.writeFile(fileName, sql)
            } catch {
              case NonFatal(ex) =>
                logger.severe(s"Failed writing ${dialect.key} schema: ${ex.getMessage}")
                throw ex
            }
          }
        }
      }

      settings.sql.languageTargets.keySet.toList.sorted.foreach { languageId =>
        settings.sql.toCodegenSettings(languageId) match {
          case Some(codegenSettings) =>
            if (serviceIr.services.isEmpty) {
              logger.info(
                s"Skipping $languageId service codegen: Smithy model contains no @sqlService services"
              )
            } else {
              SqlServiceCodegenRenderer.render(model, schema, serviceIr, codegenSettings) match {
                case Validated.Valid(artifacts) =>
                  artifacts.foreach { artifact =>
                    try {
                      logger.info(s"Generating SQL service artifact '${artifact.relativePath}'")
                      context.getFileManifest.writeFile(artifact.relativePath, artifact.content)
                    } catch {
                      case NonFatal(ex) =>
                        logger.severe(
                          s"Failed writing SQL service artifact '${artifact.relativePath}': ${ex.getMessage}"
                        )
                        throw ex
                    }
                  }
                case Validated.Invalid(errors)  =>
                  throw new IllegalArgumentException(
                    s"smithy-stache plugin failed $languageId codegen validation: ${SqlValidated.toPluginExceptionMessage(errors)}"
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
          s"smithy-stache plugin failed validation: ${SqlValidated.toPluginExceptionMessage(errors)}"
        )
    }
  }
}
