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
        templateDirectory = PythonTemplateNamespaces.bundledDbTemplateDirectory,
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
