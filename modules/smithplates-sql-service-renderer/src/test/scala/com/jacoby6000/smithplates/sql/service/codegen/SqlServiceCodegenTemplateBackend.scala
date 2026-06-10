package com.jacoby6000.smithplates.sql.service.codegen

import com.jacoby6000.smithplates.codegentest.CodegenTemplateBackend
import com.jacoby6000.smithplates.codegentest.CodegenTemplateTestCase
import com.jacoby6000.smithplates.codegentest.CodegenTemplateVariant
import com.jacoby6000.smithplates.sql.SqlTestModelLoader
import com.jacoby6000.smithplates.sql.ddl.renderer.postgres.PostgresRenderer
import com.jacoby6000.smithplates.sql.ddl.renderer.sqlite.SqliteRenderer
import com.jacoby6000.smithplates.sql.service.SqlModelExtractor
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlBindPlaceholder
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlQueryRenderer
import com.jacoby6000.smithplates.sql.service.query.renderer.postgres.PostgresSqlQueryRenderer
import com.jacoby6000.smithplates.sql.service.query.renderer.sqlite.SqliteSqlQueryRenderer
import com.jacoby6000.smithplates.sql.shared.SqlSchemaDdlRenderer
import software.amazon.smithy.model.Model

object SqlServiceCodegenTemplateBackend {

  /** Golden-test `testOutputDirectory`; rendered under `<language>/test/`. */
  val GoldenTestOutputDirectory: String = "test"

  trait ConfiguredBackend extends CodegenTemplateBackend {
    def settingsForTests: SqlServiceCodegenSettings
  }

  def apply(
      templateVariant: CodegenTemplateVariant,
      settings: SqlServiceCodegenSettings
  ): ConfiguredBackend =
    new ConfiguredBackend {
      override val variant: CodegenTemplateVariant    = templateVariant
      val settingsForTests: SqlServiceCodegenSettings = settings

      def loadModel(testCase: CodegenTemplateTestCase): Either[String, Model] =
        Right(SqlTestModelLoader.assemble(testCase.smithyModelId -> testCase.smithyContent))

      def render(testCase: CodegenTemplateTestCase, model: Model): Either[String, Map[String, String]] = {
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
                        s"${templateVariant.srcOutputRootId}/${artifact.relativePath}"
                      case SqlServiceCodegenArtifactKind.Test =>
                        artifact.relativePath
                    }
                  relativePath -> artifact.content
                }.toMap
              }
          }
          .toEither
          .left
          .map(_.toList.mkString("; "))
      }
    }

  def db(
      templateVariant: CodegenTemplateVariant,
      dialectKey: String,
      queryRenderer: SqlQueryRenderer,
      schemaDdlRenderer: SqlSchemaDdlRenderer,
      artifacts: List[SqlServiceCodegenArtifactConfig]
  ): ConfiguredBackend =
    apply(
      templateVariant = templateVariant,
      settings = SqlServiceCodegenSettings(
        templateDirectory = "classpath:",
        defaultDialectKey = dialectKey,
        enabledDialectKeys = List(dialectKey),
        queryRenderers = Map(dialectKey -> queryRenderer),
        schemaDdlRenderers = Map(dialectKey -> schemaDdlRenderer),
        testOutputDirectory = Some(GoldenTestOutputDirectory),
        artifacts = artifacts
      )
    )

  val pythonSqlite: ConfiguredBackend =
    db(
      templateVariant = CodegenTemplateVariant("python", "db", "sqlite"),
      dialectKey = "sqlite",
      queryRenderer = new SqliteSqlQueryRenderer(
        migrationBindPlaceholder = SqlBindPlaceholder("?"),
        codegenBindPlaceholder = SqlBindPlaceholder("?")
      ),
      schemaDdlRenderer = SqliteRenderer,
      artifacts = SqlServiceCodegenDbArtifacts.sqlite()
    )

  val pythonPostgres: ConfiguredBackend =
    db(
      templateVariant = CodegenTemplateVariant("python", "db", "postgres"),
      dialectKey = "postgres",
      queryRenderer = new PostgresSqlQueryRenderer(
        migrationBindPlaceholder = SqlBindPlaceholder("$" + SqlBindPlaceholder.NumberToken),
        codegenBindPlaceholder = SqlBindPlaceholder("%s")
      ),
      schemaDdlRenderer = PostgresRenderer,
      artifacts = SqlServiceCodegenDbArtifacts.postgres()
    )
}
