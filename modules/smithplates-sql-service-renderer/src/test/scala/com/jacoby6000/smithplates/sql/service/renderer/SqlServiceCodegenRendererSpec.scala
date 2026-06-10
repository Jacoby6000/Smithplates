package com.jacoby6000.smithplates.sql.service.renderer

import com.jacoby6000.smithplates.sql.SqlTestModelLoader
import com.jacoby6000.smithplates.sql.ddl.renderer.postgres.PostgresRenderer
import com.jacoby6000.smithplates.sql.ddl.renderer.sqlite.SqliteRenderer
import com.jacoby6000.smithplates.sql.service.SqlModelExtractor
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlBindPlaceholder
import com.jacoby6000.smithplates.sql.service.query.renderer.postgres.PostgresSqlQueryRenderer
import com.jacoby6000.smithplates.sql.service.query.renderer.sqlite.SqliteSqlQueryRenderer
import com.jacoby6000.smithplates.sql.service.renderer.codegentest.CodegenTemplateTestDiscovery

import java.nio.file.Paths

class SqlServiceCodegenRendererSpec extends munit.FunSuite {
  private lazy val repoRoot =
    Paths.get(sys.props.getOrElse("user.dir", ".")).toAbsolutePath.normalize

  private def loadTestCaseModel(testName: String) =
    CodegenTemplateTestDiscovery
      .discover(repoRoot, "python", Set(SqlServiceCodegenTemplateBackend.pythonSqlite.variant))
      .find(_.name == testName)
      .map(testCase => SqlTestModelLoader.assemble(testCase.smithyModelId -> testCase.smithyContent))
      .getOrElse(fail(s"expected templates golden case '$testName'"))

  test("ServiceCodegen - derives CRUD for @sqlJson struct and union columns") {
    val schema = SqlModelExtractor.extractOrThrow(loadTestCaseModel("sql-json-structs-containing-unions"))
    val insert = schema.queries.inserts.head
    val update = schema.queries.updates.head

    assertEquals(insert.columns.map(_.memberName), List("label", "destination", "state"))
    assertEquals(update.setColumns.map(_.memberName), List("label", "destination", "state"))
  }

  test("ServiceCodegen - expands output path placeholders per service") {
    val queryRenderer           =
      new SqliteSqlQueryRenderer(
        migrationBindPlaceholder = SqlBindPlaceholder("?"),
        codegenBindPlaceholder = SqlBindPlaceholder("?")
      )
    val context                 =
      SqlCodegenServiceContext(
        shapeId = software.amazon.smithy.model.shapes.ShapeId.from("example#WidgetRepository"),
        name = "WidgetRepository",
        namespace = "example",
        version = "1",
        dialectKey = "sqlite",
        queryRenderer = queryRenderer,
        bindPlaceholderStyle = queryRenderer.codegenBindPlaceholder,
        hasSqlOperations = true,
        models = Nil,
        unions = Nil,
        operations = Nil
      )
    val settings                = SqlServiceCodegenTemplateBackend.pythonSqlite.settingsForTests
    val modelArtifact           = settings.artifacts.head
    val integrationTestArtifact = settings.artifacts.last

    assertEquals(
      SqlServiceCodegenRenderer.resolveOutputPath(settings, modelArtifact, context),
      "db/model/widget_repository_models.py"
    )
    assertEquals(
      SqlServiceCodegenRenderer.resolveOutputPath(settings, integrationTestArtifact, context),
      "test/db/sqlite/test_widget_repository_derived_sql.py"
    )
  }

  test("ServiceCodegen - uses postgres query renderer for postgres service artifacts when both dialects are enabled") {
    val model    = loadTestCaseModel("sql-derive-select-one-only")
    val schema   = SqlModelExtractor.extractOrThrow(model)
    val settings =
      SqlServiceCodegenSettings(
        templateDirectory = "classpath:",
        defaultDialectKey = "sqlite",
        enabledDialectKeys = List("sqlite", "postgres"),
        queryRenderers = Map(
          "sqlite"   -> new SqliteSqlQueryRenderer(
            migrationBindPlaceholder = SqlBindPlaceholder("?"),
            codegenBindPlaceholder = SqlBindPlaceholder("?")
          ),
          "postgres" -> new PostgresSqlQueryRenderer(
            migrationBindPlaceholder = SqlBindPlaceholder("$" + SqlBindPlaceholder.NumberToken),
            codegenBindPlaceholder = SqlBindPlaceholder("%s")
          )
        ),
        schemaDdlRenderers = Map(
          "sqlite"   -> SqliteRenderer,
          "postgres" -> PostgresRenderer
        ),
        artifacts = SqlServiceCodegenDbArtifacts.forEnabledDialects(List("sqlite", "postgres"))
      )

    val rendered =
      SqlServiceCodegenRenderer
        .render(model, schema.schema, schema.serviceIr, settings)
        .fold(errors => fail(errors.toList.mkString("; ")), identity)

    val postgresService =
      rendered
        .find(_.relativePath.endsWith("/bookmark_repository_psycopg.py"))
        .getOrElse(fail("expected postgres service artifact"))

    assert(clue(postgresService.content).contains("class BookmarkRepositoryPsycopgService"))
    assert(clue(postgresService.content).contains("WHERE id = %s"))
    assert(!clue(postgresService.content).contains("AiosqliteService"))
  }
}
