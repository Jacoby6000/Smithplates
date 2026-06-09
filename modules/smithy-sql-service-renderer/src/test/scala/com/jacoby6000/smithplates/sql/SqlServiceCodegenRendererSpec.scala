package com.jacoby6000.smithplates.sql.codegen

import com.jacoby6000.smithplates.codegentest.CodegenTemplateTestDiscovery
import com.jacoby6000.smithplates.sql.SqlTestModelLoader
import com.jacoby6000.smithplates.sql.query.SqlBindPlaceholder
import com.jacoby6000.smithplates.sql.query.sqlite.SqliteSqlQueryRenderer
import com.jacoby6000.smithplates.sql.service.SqlModelExtractor

class SqlServiceCodegenRendererSpec extends munit.FunSuite {
  private def loadTestCaseModel(testName: String) =
    CodegenTemplateTestDiscovery
      .discover(getClass.getClassLoader, Set(SqlServiceCodegenTemplateBackend.pythonSqlite.variant))
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
}
