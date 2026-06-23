package com.jacoby6000.smithplates.sql.service.renderer

import com.jacoby6000.smithplates.sql.SqlTestModelBuilder
import com.jacoby6000.smithplates.sql.service.SqlModelExtractor

class SqlCodegenHelperAttributesSpec extends munit.FunSuite {
  private val documentRecordSmithy =
    """
      |use smithplates.codegen.sql#DerivedStruct
      |use smithplates.codegen.sql#sqlDeriveInsert
      |use smithplates.codegen.sql#sqlDeriveSelectOne
      |use smithplates.codegen.sql#sqlPrimaryKey
      |use smithplates.codegen.sql#sqlService
      |use smithplates.codegen.sql#sqlTable
      |
      |@sqlTable(name: "records")
      |structure Record {
      |    @sqlPrimaryKey
      |    id: String
      |    payload: Document
      |}
      |
      |@sqlDeriveInsert(targetTable: "example#Record")
      |operation CreateRecord {
      |    input: DerivedStruct
      |    output: String
      |}
      |
      |@sqlDeriveSelectOne(targetTable: "example#Record")
      |operation GetRecord {
      |    input: DerivedStruct
      |    output: Record
      |}
      |
      |@sqlService
      |service RecordRepository {
      |    version: "1"
      |    operations: [CreateRecord, GetRecord]
      |}
      |""".stripMargin

  test("documentUsedAsJson - true when Document column is bound or read") {
    val model      = SqlTestModelBuilder.assemble(documentRecordSmithy)
    val extraction = SqlModelExtractor.extractOrThrow(model)
    val view       =
      SqlCodegenTemplateAttributes
        .forService(
          SqlServiceCodegenContextBuilder
            .build(
              model = model,
              schema = extraction.schema,
              queries = extraction.serviceIr.queries,
              service = extraction.serviceIr.services.head,
              queryRenderer =
                Some(SqlServiceCodegenTemplateBackend.pythonSqlite.settingsForTests.queryRenderers("sqlite")),
              bindPlaceholderStyle = SqlServiceCodegenTemplateBackend.pythonSqlite.settingsForTests
                .queryRenderers("sqlite")
                .codegenBindPlaceholder,
              settings = SqlServiceCodegenTemplateBackend.pythonSqlite.settingsForTests
            )
            .toEither
            .getOrElse(fail("expected sqlite context build to succeed"))
        )

    assert(SqlCodegenHelperAttributes.documentUsedAsJson(view))
    assert(SqlCodegenHelperAttributes.documentUsedAsJsonCol(view))
    assertEquals(SqlCodegenHelperAttributes.modelsUsedAsJson(view), Nil)
  }

  test("documentUsedAsJson - false when no Document JSON columns are used") {
    val view =
      ServiceTemplateView(
        serviceShapeId = "example#WidgetRepository",
        serviceName = "WidgetRepository",
        dialectKey = "sqlite",
        packageName = "generated.example",
        models = Nil,
        operationResultModels = Nil,
        unions = Nil,
        operations = Nil,
        usedJsonTypeNames = Set("PostalAddress"),
        usedJsonTypeNamesCol = Set.empty,
        classRowFactories = Nil,
        protocolTableModelImportBlock = "",
        enumImportBlock = "",
        serviceLocalImportBlock = "",
        integrationTest = None,
        migration = None,
        uuidTypeNames = Nil,
        enumTypeNames = Nil
      )

    assert(!SqlCodegenHelperAttributes.documentUsedAsJson(view))
    assert(!SqlCodegenHelperAttributes.documentUsedAsJsonCol(view))
  }

  test("row reader helpers - emit Document JSON bind and read helpers for sqlite and postgres") {
    val view =
      ServiceTemplateView(
        serviceShapeId = "example#RecordRepository",
        serviceName = "RecordRepository",
        dialectKey = "sqlite",
        packageName = "generated.example",
        models = Nil,
        operationResultModels = Nil,
        unions = Nil,
        operations = List(
          TemplateOperationView(
            name = "GetRecord",
            hasSql = true,
            parameters = Nil,
            hasOutput = true,
            outputShapeName = "Record",
            outputTypeName = "Record",
            errors = Nil,
            isSelectOne = true,
            sqlBodyKind = SqlCodegenSqlBodyKind.SelectOneDict,
            sqlStatement = "SELECT id, payload FROM records WHERE id = ?",
            bindParameters = Nil,
            returningColumnIndex = null,
            resultFields = List(
              TemplateResultFieldView(
                fieldName = "id",
                columnNameLiteral = "\"id\"",
                columnIndex = 0,
                typeName = "String",
                readTypeName = "String",
                optional = false,
                isJson = false,
                timestampFormat = "",
                isEnum = false,
                last = false
              ),
              TemplateResultFieldView(
                fieldName = "payload",
                columnNameLiteral = "\"payload\"",
                columnIndex = 1,
                typeName = "Document",
                readTypeName = "Document",
                optional = false,
                isJson = true,
                timestampFormat = "",
                isEnum = false,
                last = true
              )
            ),
            booleanResultFieldName = "",
            selectOneNestedBindings = Nil
          )
        ),
        usedJsonTypeNames = Set("Document"),
        usedJsonTypeNamesCol = Set("Document"),
        classRowFactories = Nil,
        protocolTableModelImportBlock = "",
        enumImportBlock = "",
        serviceLocalImportBlock = "",
        integrationTest = None,
        migration = None,
        uuidTypeNames = Nil,
        enumTypeNames = Nil
      )

    List("sqlite", "postgres").foreach { dialectKey =>
      val dialectView = view.copy(dialectKey = dialectKey)
      val output      =
        ScalateSspTemplateEngine.renderClasspathPartial(
          SspFragmentsSpec.internal.templateRoot,
          "fragments/helpers/row_reader_helpers",
          Map("ctx" -> dialectView)
        )

      assert(output.contains("def _json_bind_Document"), s"$dialectKey missing bind helper: $output")
      assert(output.contains("def _read_Document("), s"$dialectKey missing index read helper: $output")
      assert(output.contains("def _read_Document_col("), s"$dialectKey missing col read helper: $output")
    }
  }

  List(
    ("sqlite", SqlServiceCodegenTemplateBackend.pythonSqlite),
    ("postgres", SqlServiceCodegenTemplateBackend.pythonPostgres)
  ).foreach { case (dialectKey, backend) =>
    test(s"service render - includes Document JSON helpers for $dialectKey") {
      val model           = SqlTestModelBuilder.assemble(documentRecordSmithy)
      val extraction      = SqlModelExtractor.extractOrThrow(model)
      val artifacts       =
        SqlServiceCodegenRenderer
          .render(model, extraction.schema, extraction.serviceIr, backend.settingsForTests)
          .toEither
          .getOrElse(fail(s"expected $dialectKey render to succeed"))
      val serviceArtifact =
        artifacts
          .find(_.relativePath.contains(s"$dialectKey/record_repository"))
          .getOrElse(fail(s"expected $dialectKey service artifact"))

      assert(serviceArtifact.content.contains("def _json_bind_Document"))
      assert(serviceArtifact.content.contains("def _read_Document("))
      assert(serviceArtifact.content.contains("def _read_Document_col("))
      assert(serviceArtifact.content.contains("_json_bind_Document(payload)"))
    }
  }
}
