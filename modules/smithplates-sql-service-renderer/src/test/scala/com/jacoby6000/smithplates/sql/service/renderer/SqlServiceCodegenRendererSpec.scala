package com.jacoby6000.smithplates.sql.service.renderer

import com.jacoby6000.smithplates.sql.SqlTestModelBuilder
import com.jacoby6000.smithplates.sql.service.SqlModelExtractor
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlBindPlaceholder
import com.jacoby6000.smithplates.sql.service.query.renderer.sqlite.SqliteSqlQueryRenderer

class SqlServiceCodegenRendererSpec extends munit.FunSuite {
  test("@sqlJson - derives insert and update columns for struct and union members") {
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

  test("OutputPath - expands placeholders per service") {
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
        queryRenderer = Some(queryRenderer),
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

  test("renders shared model and protocol artifacts without an enabled dialect") {
    val model      =
      SqlTestModelBuilder.assemble(
        """
          |use smithplates.codegen.sql#DerivedStruct
          |use smithplates.codegen.sql#sqlDeriveInsert
          |use smithplates.codegen.sql#sqlDeriveSelectOne
          |use smithplates.codegen.sql#sqlPrimaryKey
          |use smithplates.codegen.sql#sqlService
          |use smithplates.codegen.sql#sqlTable
          |
          |@sqlTable(name: "widgets")
          |structure Widget {
          |    @sqlPrimaryKey
          |    id: String
          |    name: String
          |}
          |
          |@sqlDeriveInsert(targetTable: "example#Widget")
          |operation CreateWidget {
          |    input: DerivedStruct
          |    output: String
          |}
          |
          |@sqlDeriveSelectOne(targetTable: "example#Widget")
          |operation GetWidget {
          |    input: DerivedStruct
          |    output: Widget
          |}
          |
          |@sqlService
          |service WidgetRepository {
          |    version: "1"
          |    operations: [CreateWidget, GetWidget]
          |}
          |""".stripMargin
      )
    val extraction = SqlModelExtractor.extractOrThrow(model)
    val settings   =
      SqlServiceCodegenSettings(
        templateDirectory = PythonTemplateNamespaces.bundledDbTemplateDirectory,
        defaultDialectKey = SqlServiceCodegenSettings.SharedDialectKey,
        enabledDialectKeys = Nil,
        queryRenderers = Map.empty,
        schemaDdlRenderers = Map.empty,
        sourceOutputDirectory = Some("src/generated"),
        testOutputDirectory = Some("tests"),
        artifacts = SqlServiceCodegenDbArtifacts.shared
      )

    val artifacts =
      SqlServiceCodegenRenderer
        .render(model, extraction.schema, extraction.serviceIr, settings)
        .toEither
        .getOrElse(fail("expected shared-only render to succeed"))

    assertEquals(
      artifacts.map(_.relativePath).sorted,
      List(
        "src/generated/db/model/widget_repository_models.py",
        "src/generated/db/widget_repository_protocol.py"
      )
    )
    assert(artifacts.forall(!_.relativePath.contains("/sqlite/")))
    assert(artifacts.forall(!_.relativePath.contains("/postgres/")))
    val artifactContentByPath = artifacts.map(artifact => artifact.relativePath -> artifact.content).toMap
    assert(
      artifactContentByPath("src/generated/db/widget_repository_protocol.py")
        .contains("async def get_widget(")
    )
    assert(
      artifactContentByPath("src/generated/db/widget_repository_protocol.py")
        .contains(") -> Widget | None:")
    )
  }
}
