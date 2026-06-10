package com.jacoby6000.smithplates.sql.service.renderer

import com.jacoby6000.smithplates.sql.SqlTestModelBuilder
import com.jacoby6000.smithplates.sql.service.SqlModelExtractor
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlBindPlaceholder
import com.jacoby6000.smithplates.sql.service.query.renderer.sqlite.SqliteSqlQueryRenderer

class SqlServiceCodegenRendererSpec extends munit.FunSuite {
  test("ServiceCodegen - derives CRUD for @sqlJson struct and union columns") {
    val schema =
      SqlModelExtractor.extractOrThrow(
        SqlTestModelBuilder.assemble(
          """
            |use smithplates.codegen.sql#DerivedStruct
            |use smithplates.codegen.sql#sqlAutoUuid
            |use smithplates.codegen.sql#sqlCreatedTimestamp
            |use smithplates.codegen.sql#sqlDeriveInsert
            |use smithplates.codegen.sql#sqlDeriveUpdate
            |use smithplates.codegen.sql#sqlJson
            |use smithplates.codegen.sql#sqlPrimaryKey
            |use smithplates.codegen.sql#sqlTable
            |use smithy.api#required
            |
            |structure PostalAddress {
            |    @required
            |    street: String
            |    @required
            |    city: String
            |}
            |
            |union DeliveryState {
            |    pending: String
            |    delivered: Timestamp
            |}
            |
            |@sqlTable(name: "shipments")
            |structure Shipment {
            |    @sqlPrimaryKey
            |    @sqlAutoUuid
            |    id: String
            |    @required
            |    label: String
            |    @required
            |    @sqlJson
            |    destination: PostalAddress
            |    @required
            |    @sqlJson
            |    state: DeliveryState
            |    @sqlCreatedTimestamp
            |    created_at: Timestamp
            |}
            |
            |@sqlDeriveInsert(targetTable: "example#Shipment")
            |operation CreateShipment {
            |    input: DerivedStruct
            |    output: String
            |}
            |
            |@sqlDeriveUpdate(targetTable: "example#Shipment")
            |operation UpdateShipment {
            |    input: DerivedStruct
            |    output: Boolean
            |}
            |""".stripMargin
        )
      )
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
