package com.jacoby6000.smithplates.sql.service.renderer

import com.jacoby6000.smithplates.codegen.core.planning.TemplateView
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

  private def buildServiceView(dialectKey: String): SqlNeutralServiceTemplateAttributes.ServiceView = {
    val model      = SqlTestModelBuilder.assemble(documentRecordSmithy)
    val extraction = SqlModelExtractor.extractOrThrow(model)
    val backend    =
      dialectKey match {
        case "sqlite"   => SqlServiceCodegenTemplateBackend.pythonSqlite
        case "postgres" => SqlServiceCodegenTemplateBackend.pythonPostgres
        case other      => fail(s"unknown dialect: $other")
      }
    val context    =
      SqlServiceCodegenContextBuilder
        .build(
          model = model,
          schema = extraction.schema,
          queries = extraction.serviceIr.queries,
          service = extraction.serviceIr.services.head,
          queryRenderer = Some(backend.settingsForTests.queryRenderers(dialectKey)),
          bindPlaceholderStyle = backend.settingsForTests
            .queryRenderers(dialectKey)
            .codegenBindPlaceholder,
          settings = backend.settingsForTests
        )
        .toEither
        .getOrElse(fail("expected context build to succeed"))

    val (_, services)                                             = com.jacoby6000.smithplates.sql.service.core.SqlCoreModelExtractor
      .extract(model)
      .toEither
      .getOrElse(fail("expected core extraction to succeed"))
    val serviceModel                                              = services.head
    val baseView: SqlNeutralServiceTemplateAttributes.ServiceView = TemplateView(
      subject = serviceModel,
      usedTypes = Nil,
      conventions = SspFragmentsSpec.internal.minimalServiceView.conventions,
      typeRenderer = SspFragmentsSpec.internal.minimalServiceView.typeRenderer
    )
    SqlServiceCodegenRenderer.internal.enrichView(baseView, context)
  }

  test("documentUsedAsJson - true when Document column is bound or read") {
    val view = buildServiceView("sqlite")

    assert(SqlNeutralServiceTemplateAttributes.documentUsedAsJson(view))
    assert(SqlNeutralServiceTemplateAttributes.documentUsedAsJsonCol(view))
  }

  test("documentUsedAsJson - false when no Document JSON columns are used") {
    val view = SspFragmentsSpec.internal.minimalServiceView

    assert(!SqlNeutralServiceTemplateAttributes.documentUsedAsJson(view))
    assert(!SqlNeutralServiceTemplateAttributes.documentUsedAsJsonCol(view))
  }

  test("row reader helpers - emit Document JSON bind and read helpers for sqlite and postgres") {
    List("sqlite", "postgres").foreach { dialectKey =>
      val view   = buildServiceView(dialectKey)
      val output =
        ScalateSspTemplateEngine.renderClasspathPartial(
          SspFragmentsSpec.internal.templateRoot,
          "fragments/helpers/row_reader_helpers",
          Map("ctx" -> view)
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
          .fold(
            errors => fail(s"expected $dialectKey render to succeed: ${errors.toList.map(_.message).mkString("; ")}"),
            identity
          )
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
