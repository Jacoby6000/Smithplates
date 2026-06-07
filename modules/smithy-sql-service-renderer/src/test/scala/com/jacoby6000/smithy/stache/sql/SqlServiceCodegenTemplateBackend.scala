package com.jacoby6000.smithy.stache.sql.codegen

import com.jacoby6000.smithy.stache.codegentest.CodegenTemplateBackend
import com.jacoby6000.smithy.stache.codegentest.CodegenTemplateTestCase
import com.jacoby6000.smithy.stache.codegentest.CodegenTemplateValidator
import com.jacoby6000.smithy.stache.codegentest.CodegenTemplateVariant
import com.jacoby6000.smithy.stache.sql.SqlTestModelLoader
import com.jacoby6000.smithy.stache.sql.postgres.PostgresRenderer
import com.jacoby6000.smithy.stache.sql.query.SqlBindPlaceholder
import com.jacoby6000.smithy.stache.sql.query.SqlQueryRenderer
import com.jacoby6000.smithy.stache.sql.query.postgres.PostgresSqlQueryRenderer
import com.jacoby6000.smithy.stache.sql.query.sqlite.SqliteSqlQueryRenderer
import com.jacoby6000.smithy.stache.sql.service.SqlModelExtractor
import com.jacoby6000.smithy.stache.sql.shared.SqlSchemaDdlRenderer
import com.jacoby6000.smithy.stache.sql.sqlite.SqliteRenderer
import software.amazon.smithy.model.Model

object SqlServiceCodegenTemplateBackend {

  /** Golden-test `testOutputDirectory`; rendered under `<language>/test/`. */
  val GoldenTestOutputDirectory: String = "test"

  trait ConfiguredBackend extends CodegenTemplateBackend {
    def settingsForTests: SqlServiceCodegenSettings
  }

  def apply(
      templateVariant: CodegenTemplateVariant,
      settings: SqlServiceCodegenSettings,
      validator: Option[CodegenTemplateValidator] = None
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
                        s"${templateVariant.languageId}/${artifact.relativePath}"
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
          testCase: CodegenTemplateTestCase,
          rendered: Map[String, String]
      ): Unit =
        validator.foreach(_.validate(testCase, templateVariant, rendered))
    }

  def db(
      templateVariant: CodegenTemplateVariant,
      dialectKey: String,
      queryRenderer: SqlQueryRenderer,
      schemaDdlRenderer: SqlSchemaDdlRenderer,
      artifacts: List[SqlServiceCodegenArtifactConfig],
      validator: Option[CodegenTemplateValidator] = None
  ): ConfiguredBackend =
    apply(
      templateVariant = templateVariant,
      settings = SqlServiceCodegenSettings(
        templateDirectory = s"classpath:sql-service-codegen/${templateVariant.languageId}",
        defaultDialectKey = dialectKey,
        enabledDialectKeys = List(dialectKey),
        queryRenderers = Map(dialectKey -> queryRenderer),
        schemaDdlRenderers = Map(dialectKey -> schemaDdlRenderer),
        testOutputDirectory = Some(GoldenTestOutputDirectory),
        artifacts = artifacts
      ),
      validator = validator
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
      artifacts = SqlServiceCodegenDbArtifacts.sqlite(),
      validator = Some(PythonCodegenTemplateValidator("sqlite"))
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
      artifacts = SqlServiceCodegenDbArtifacts.postgres(),
      validator = Some(PythonCodegenTemplateValidator("postgres"))
    )
}
