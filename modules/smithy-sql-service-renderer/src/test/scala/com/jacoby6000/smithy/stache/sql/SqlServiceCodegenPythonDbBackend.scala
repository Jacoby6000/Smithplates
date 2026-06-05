package com.jacoby6000.smithy.stache.sql.codegen

import com.jacoby6000.smithy.stache.mustachetest.MustacheTemplateLanguageBackend
import com.jacoby6000.smithy.stache.mustachetest.MustacheTemplateTestCase
import com.jacoby6000.smithy.stache.mustachetest.MustacheTemplateVariant
import com.jacoby6000.smithy.stache.sql.PostgresDialect
import com.jacoby6000.smithy.stache.sql.SqlDialect
import com.jacoby6000.smithy.stache.sql.SqlTestModelLoader
import com.jacoby6000.smithy.stache.sql.SqliteDialect
import com.jacoby6000.smithy.stache.sql.service.SqlModelExtractor
import com.jacoby6000.smithy.stache.sql.shared.SqlBindPlaceholder
import software.amazon.smithy.model.Model

trait SqlServiceCodegenTestBackend extends MustacheTemplateLanguageBackend {
  def settingsForTests: SqlServiceCodegenSettings
}

object SqlServiceCodegenPythonDbBackend {

  /** Golden-test `testOutputDirectory`; rendered under `<language>/test/`. */
  val GoldenTestOutputDirectory: String = "test"

  val sqlite: SqlServiceCodegenTestBackend =
    forImplementation(
      templateVariant = MustacheTemplateVariant("python", "db", "sqlite"),
      dialect = SqliteDialect,
      artifacts = SqlServiceCodegenDbArtifacts.sqlite()
    )

  val postgres: SqlServiceCodegenTestBackend =
    forImplementation(
      templateVariant = MustacheTemplateVariant("python", "db", "postgres"),
      dialect = PostgresDialect,
      artifacts = SqlServiceCodegenDbArtifacts.postgres()
    )

  private def forImplementation(
      templateVariant: MustacheTemplateVariant,
      dialect: SqlDialect,
      artifacts: List[SqlServiceCodegenArtifactConfig]
  ): SqlServiceCodegenTestBackend =
    new SqlServiceCodegenTestBackend {
      override val variant: MustacheTemplateVariant = templateVariant

      val settingsForTests: SqlServiceCodegenSettings =
        SqlServiceCodegenSettings(
          templateDirectory = "classpath:sql-service-codegen/python",
          dialect = dialect,
          bindPlaceholderStyle = SqlBindPlaceholder.inferForCodegen(dialect),
          testOutputDirectory = Some(GoldenTestOutputDirectory),
          artifacts = artifacts
        )

      def loadModel(testCase: MustacheTemplateTestCase): Either[String, Model] =
        Right(SqlTestModelLoader.assemble(testCase.smithyModelId -> testCase.smithyContent))

      def render(testCase: MustacheTemplateTestCase, model: Model): Either[String, Map[String, String]] = {
        val _ = testCase
        SqlModelExtractor
          .extract(model)
          .andThen { extraction =>
            SqlServiceCodegenRenderer
              .render(model, extraction.schema, extraction.serviceIr, settingsForTests)
              .map { renderedArtifacts =>
                renderedArtifacts.map { artifact =>
                  val relativePath =
                    artifact.kind match {
                      case SqlServiceCodegenArtifactKind.Src  =>
                        s"${variant.srcOutputRootId}/${artifact.relativePath}"
                      case SqlServiceCodegenArtifactKind.Test =>
                        s"${variant.languageId}/${artifact.relativePath}"
                    }
                  relativePath -> artifact.content
                }.toMap
              }
          }
          .toEither
          .left
          .map(_.toList.mkString("; "))
      }

      override def validateRenderedOutputs(
          testCase: MustacheTemplateTestCase,
          rendered: Map[String, String]
      ): Unit = {
        val artifacts =
          rendered.toList.map { case (relativePath, content) =>
            SqlCodegenArtifact(
              relativePath = relativePath,
              content = content,
              kind = if (relativePath.startsWith(s"${variant.testOutputRootId}/")) {
                SqlServiceCodegenArtifactKind.Test
              } else {
                SqlServiceCodegenArtifactKind.Src
              }
            )
          }
        PythonCodegenWorkspace.validateCase(
          testCase.name,
          artifacts,
          dialect
        )
      }
    }
}

object SqlServiceCodegenPythonBackend {
  val settingsForTests: SqlServiceCodegenSettings = SqlServiceCodegenPythonDbBackend.sqlite.settingsForTests
}
