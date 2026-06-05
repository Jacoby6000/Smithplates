package com.jacoby6000.smithy.stache

import com.jacoby6000.smithy.sql.codegen.SqlServiceCodegenRenderer
import com.jacoby6000.smithy.sql.{
  DialectRenderer,
  SqlModelExtractor,
  SqlValidated
}
import java.util.logging.Logger
import scala.util.control.NonFatal
import cats.data.Validated
import cats.syntax.all.*
import software.amazon.smithy.build.{PluginContext, SmithyBuildPlugin}

final class SmithyStacheBuildPlugin extends SmithyBuildPlugin {
  private val logger = Logger.getLogger(classOf[SmithyStacheBuildPlugin].getName)

  override def getName: String = "smithy-stache"

  override def execute(context: PluginContext): Unit = {
    val model = context.getModel

    (
      SmithyStacheSettings.fromNode(context.getSettings),
      SqlModelExtractor.extract(model)
    ).mapN { (settings, schema) =>
      if (settings.sql.schemaDialectOutputs.nonEmpty) {
        if (schema.tables.isEmpty) {
          logger.info("Skipping SQL schema generation: Smithy model contains no @sqlTable structures")
        } else {
          settings.sql.schemaDialectOutputs.foreach { case (dialect, fileName) =>
            try {
              logger.info(s"Generating ${dialect.key} schema into '$fileName'")
              val renderer = DialectRenderer.forDialect(dialect)
              val sql = renderer.render(schema)
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
            if (schema.services.isEmpty) {
              logger.info(
                s"Skipping $languageId service codegen: Smithy model contains no @sqlService services"
              )
            } else {
              SqlServiceCodegenRenderer.render(model, schema, codegenSettings) match {
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
                case Validated.Invalid(errors) =>
                  throw new IllegalArgumentException(
                    s"smithy-stache plugin failed $languageId codegen validation: ${SqlValidated.toPluginExceptionMessage(errors)}"
                  )
              }
            }
          case None => ()
        }
      }
    } match {
      case Validated.Valid(_) => ()
      case Validated.Invalid(errors) =>
        throw new IllegalArgumentException(
          s"smithy-stache plugin failed validation: ${SqlValidated.toPluginExceptionMessage(errors)}"
        )
    }
  }
}
