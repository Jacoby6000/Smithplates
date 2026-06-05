package com.jacoby6000.smithy.stache.sql.codegen

import com.jacoby6000.smithy.stache.mustachetest.MustacheTemplateTestDiscovery
import com.jacoby6000.smithy.stache.sql.SqlTestModelLoader
import com.jacoby6000.smithy.stache.sql.query.SqlBindPlaceholder
import com.jacoby6000.smithy.stache.sql.query.sqlite.SqliteSqlQueryRenderer
import com.jacoby6000.smithy.stache.sql.service.SqlModelExtractor

class SqlServiceCodegenRendererSpec extends munit.FunSuite {
  private def loadTestCaseModel(testName: String) =
    MustacheTemplateTestDiscovery
      .discover(getClass.getClassLoader, Set(SqlServiceCodegenPythonDbBackend.sqlite.variant))
      .find(_.name == testName)
      .map(testCase => SqlTestModelLoader.assemble(testCase.smithyModelId -> testCase.smithyContent))
      .getOrElse(fail(s"expected mustache-template-tests case '$testName'"))

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
        fileName = "widget_repository",
        version = "1",
        dialectKey = "sqlite",
        queryRenderer = queryRenderer,
        bindPlaceholderStyle = queryRenderer.codegenBindPlaceholder,
        implementationClassName = "WidgetRepositoryAiosqliteService",
        implementationModuleName = "widget_repository_aiosqlite",
        hasSqlOperations = true,
        models = Nil,
        unions = Nil,
        operations = Nil
      )
    val settings                = SqlServiceCodegenPythonBackend.settingsForTests
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
}
