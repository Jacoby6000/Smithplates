package com.jacoby6000.smithplates.sql.service.renderer

import com.jacoby6000.smithplates.codegen.core.planning.CodegenOutput
import com.jacoby6000.smithplates.sql.SqlTestModelBuilder
import com.jacoby6000.smithplates.sql.service.SqlModelExtractor
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlBindPlaceholder

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

  test("SQL output deck carries planner path templates") {
    val settings = SqlServiceCodegenTemplateBackend.pythonSqlite.settingsForTests

    val outputPaths =
      settings.artifacts.collect { case output: CodegenOutput.CodegenTemplateBindingOutput =>
        output.id.value -> output.outputPath
      }.toMap

    assertEquals(outputPaths("python.sql.db.models"), "{{smithyNamespaceDir}}/models/{{serviceModuleName}}_models.py")
    assertEquals(
      outputPaths("python.sql.db.sqlite.integration_tests"),
      "{{smithyNamespaceDir}}/sqlite/test_{{serviceModuleName}}_derived_sql.py"
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
        templateDirectory = "classpath:python/src/db",
        defaultDialectKey = SqlServiceCodegenSettings.SharedDialectKey,
        enabledDialectKeys = Nil,
        queryRenderers = Map.empty,
        schemaDdlRenderers = Map.empty,
        sourceOutputDirectory = Some("src/generated"),
        testOutputDirectory = Some("tests"),
        artifacts = SqlServiceCodegenDbArtifacts.shared,
        rootNamespace = Some("generated"),
        packageNameOverride = None
      )

    val artifacts =
      SqlServiceCodegenRenderer
        .render(model, extraction.schema, extraction.serviceIr, settings)
        .toEither
        .getOrElse(fail("expected shared-only render to succeed"))

    assertEquals(
      artifacts.map(_.relativePath).sorted,
      List(
        "src/generated/example/models/widget_repository_models.py",
        "src/generated/example/widget_repository_protocol.py"
      )
    )
    assert(artifacts.forall(!_.relativePath.contains("/sqlite/")))
    assert(artifacts.forall(!_.relativePath.contains("/postgres/")))
    val artifactContentByPath = artifacts.map(artifact => artifact.relativePath -> artifact.content).toMap
    assert(
      artifactContentByPath("src/generated/example/widget_repository_protocol.py")
        .contains("async def get_widget(")
    )
    assert(
      artifactContentByPath("src/generated/example/widget_repository_protocol.py")
        .contains(") -> Widget | None:")
    )
  }

  test("shouldSkip skips integration tests and migration templates when context sections are absent") {
    val model         = SqlTestModelBuilder.assemble(
      """
        |use smithplates.codegen.sql#DerivedStruct
        |use smithplates.codegen.sql#sqlDeriveInsert
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
        |@sqlService
        |service WidgetRepository {
        |    version: "1"
        |    operations: [CreateWidget]
        |}
        |""".stripMargin
    )
    val extraction    = SqlModelExtractor.extractOrThrow(model)
    val sharedContext =
      SqlServiceCodegenContextBuilder
        .build(
          model = model,
          schema = extraction.schema,
          queries = extraction.serviceIr.queries,
          service = extraction.serviceIr.services.head,
          queryRenderer = None,
          bindPlaceholderStyle = SqlBindPlaceholder("?"),
          settings = SqlServiceCodegenTemplateBackend.pythonSqlite.settingsForTests
        )
        .toEither
        .getOrElse(fail("expected shared context"))

    assert(
      SqlServiceCodegenRenderer.internal.shouldSkip(
        "sqlite/tests/service_derived_sql_integration_tests.ssp",
        sharedContext.copy(integrationTest = None)
      )
    )
    assert(
      SqlServiceCodegenRenderer.internal.shouldSkip(
        "postgres/stubs/testcontainers/postgres.pyi",
        sharedContext.copy(integrationTest = None)
      )
    )
    assert(
      SqlServiceCodegenRenderer.internal.shouldSkip(
        "sqlite/migrations_service.ssp",
        sharedContext.copy(migration = None)
      )
    )
    assert(
      !SqlServiceCodegenRenderer.internal.shouldSkip(
        "sqlite/service_aiosqlite.ssp",
        sharedContext
      )
    )
  }

  test("reserved-keyword service names use conventions in artifact paths and generated imports") {
    val model      =
      SqlTestModelBuilder.assemble(
        """
          |use smithplates.codegen.sql#DerivedStruct
          |use smithplates.codegen.sql#sqlDeriveInsert
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
          |@sqlService
          |service Class {
          |    version: "1"
          |    operations: [CreateWidget]
          |}
          |""".stripMargin
      )
    val extraction = SqlModelExtractor.extractOrThrow(model)
    val settings   =
      SqlServiceCodegenSettings(
        templateDirectory = "classpath:python/src/db",
        defaultDialectKey = SqlServiceCodegenSettings.SharedDialectKey,
        enabledDialectKeys = Nil,
        queryRenderers = Map.empty,
        schemaDdlRenderers = Map.empty,
        sourceOutputDirectory = Some("src/generated"),
        testOutputDirectory = Some("tests"),
        artifacts = SqlServiceCodegenDbArtifacts.shared,
        rootNamespace = Some("generated"),
        packageNameOverride = None
      )

    val artifacts =
      SqlServiceCodegenRenderer
        .render(model, extraction.schema, extraction.serviceIr, settings)
        .toEither
        .getOrElse(fail("expected reserved-keyword render to succeed"))

    val protocolPath = "src/generated/example/class__protocol.py"
    val modelsPath   = "src/generated/example/models/class__models.py"
    assertEquals(artifacts.map(_.relativePath).sorted, List(protocolPath, modelsPath))

    val protocolContent      = artifacts.find(_.relativePath == protocolPath).map(_.content).getOrElse("")
    val sqliteServiceContent =
      SqlServiceCodegenRenderer
        .render(
          model,
          extraction.schema,
          extraction.serviceIr,
          settings.copy(
            defaultDialectKey = "sqlite",
            enabledDialectKeys = List("sqlite"),
            queryRenderers = SqlServiceCodegenTemplateBackend.pythonSqlite.settingsForTests.queryRenderers,
            schemaDdlRenderers = SqlServiceCodegenTemplateBackend.pythonSqlite.settingsForTests.schemaDdlRenderers,
            migrationDirectories = SqlServiceCodegenTemplateBackend.pythonSqlite.settingsForTests.migrationDirectories,
            artifacts = SqlServiceCodegenDbArtifacts.sqlite()
          )
        )
        .toEither
        .getOrElse(fail("expected sqlite render to succeed"))
        .find(_.relativePath.endsWith("sqlite/class__aiosqlite.py"))
        .map(_.content)
        .getOrElse(fail("expected sqlite service artifact"))

    assert(protocolContent.contains("class ClassServiceProtocol"))
    assert(sqliteServiceContent.contains("from generated.example.class__protocol import ClassServiceProtocol"))
    assert(!sqliteServiceContent.contains("from generated.example.class_protocol import ClassServiceProtocol"))
  }

  test("planner output paths use sourceOutputDirectory and testOutputDirectory prefixes") {
    val model      =
      SqlTestModelBuilder.assemble(
        """
          |use smithplates.codegen.sql#DerivedStruct
          |use smithplates.codegen.sql#sqlDeriveInsert
          |use smithplates.codegen.sql#sqlDeriveSelectOne
          |use smithplates.codegen.sql#sqlDeriveDelete
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
          |@sqlDeriveDelete(targetTable: "example#Widget")
          |operation DeleteWidget {
          |    input: DerivedStruct
          |    output: Boolean
          |}
          |
          |@sqlService
          |service WidgetRepository {
          |    version: "1"
          |    operations: [CreateWidget, GetWidget, DeleteWidget]
          |}
          |""".stripMargin
      )
    val extraction = SqlModelExtractor.extractOrThrow(model)
    val settings   = SqlServiceCodegenTemplateBackend.pythonSqlite.settingsForTests.copy(
      sourceOutputDirectory = Some("src/generated"),
      testOutputDirectory = Some("tests/generated")
    )

    val artifacts =
      SqlServiceCodegenRenderer
        .render(model, extraction.schema, extraction.serviceIr, settings)
        .toEither
        .getOrElse(fail("expected sqlite render to succeed"))

    assert(artifacts.exists(_.relativePath.startsWith("src/generated/")))
    assert(
      artifacts.exists(artifact =>
        artifact.relativePath.startsWith("tests/generated/") &&
          artifact.relativePath.contains("test_widget_repository_derived_sql.py"))
    )
  }
}
