package com.jacoby6000.smithplates.sql.service.renderer

import com.jacoby6000.smithplates.codegen.core.Field
import com.jacoby6000.smithplates.codegen.core.ModelId
import com.jacoby6000.smithplates.codegen.core.NeutralType
import com.jacoby6000.smithplates.codegen.core.OperationModel
import com.jacoby6000.smithplates.codegen.core.ServiceMeta
import com.jacoby6000.smithplates.codegen.core.ServiceModel
import com.jacoby6000.smithplates.codegen.core.planning.TemplateView
import com.jacoby6000.smithplates.codegen.core.strategy.config.LanguageBaseConfigLoader
import com.jacoby6000.smithplates.sql.service.core.SqlOperationMeta
import com.jacoby6000.smithplates.sql.service.core.SqlServiceMeta

class SspFragmentsSpec extends munit.FunSuite {
  test("top-level template render prepends generated file header") {
    val output =
      ScalateSspTemplateEngine.renderClasspathTemplate(
        s"classpath:${SspFragmentsSpec.internal.templateRoot}/service_protocol.ssp",
        SspFragmentsSpec.internal.minimalServiceView,
        Some(SspFragmentsSpec.internal.templateRoot)
      )

    assert(
      output.startsWith(
        "# Generated from example#WidgetRepository by sql-service-codegen. Do not edit by hand.\n"
      ),
      s"expected generated header, got: $output"
    )
  }

  test("partial render does not prepend generated file header") {
    val output =
      ScalateSspTemplateEngine.renderClasspathPartial(
        SspFragmentsSpec.internal.templateRoot,
        "fragments/row_readers/read_str_postgres",
        Map.empty
      )

    assert(!output.contains("Generated from"), s"partials must not include generated header, got: $output")
  }

  test("row reader fragment preserves internal newlines") {
    val output =
      ScalateSspTemplateEngine.renderClasspathPartial(
        SspFragmentsSpec.internal.templateRoot,
        "fragments/row_readers/read_str_postgres",
        Map.empty
      )

    assert(output.contains("def _read_str"), s"expected function definition, got: $output")
    assert(output.contains("\n    value = row[index]"), s"expected indented body lines, got: $output")
  }

  test("single-line import fragment does not add blank lines") {
    val output =
      ScalateSspTemplateEngine.renderClasspathPartial(
        SspFragmentsSpec.internal.templateRoot,
        "fragments/helpers/imports_postgres",
        Map("ctx" -> SspFragmentsSpec.internal.minimalServiceView)
      )

    assertEquals(output, "")
  }

  test("member lines fragment preserves newlines") {
    val output =
      ScalateSspTemplateEngine.renderClasspathPartial(
        SspFragmentsSpec.internal.templateRoot,
        "fragments/models/member_lines",
        Map(
          "members" -> List(
            Field(name = "street", tpe = NeutralType.StringT),
            Field(name = "city", tpe = NeutralType.StringT)
          ),
          "ctx"     -> SspFragmentsSpec.internal.minimalServiceView
        )
      )

    assertEquals(
      output,
      """    street: str
        |    city: str
        |""".stripMargin
    )
  }
}
object SspFragmentsSpec {

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    val templateRoot = "python/src/db"

    def minimalServiceView: SqlNeutralServiceTemplateAttributes.ServiceView = {
      val serviceMeta      = SqlServiceMeta(
        serviceName = "WidgetRepository",
        serviceShapeId = "example#WidgetRepository",
        moduleName = "widget_repository",
        namespace = "example",
        packageName = "generated.example",
        dialectKey = "sqlite",
        bindPlaceholderStyle = com.jacoby6000.smithplates.sql.SqlBindPlaceholder("?"),
        hasSqlOperations = false,
        uuidTypeNames = Set.empty,
        enumTypeNames = Set.empty,
        integrationTest = None,
        migration = None
      )
      val service          = ServiceModel(
        id = ModelId("example", "WidgetRepository"),
        meta = ServiceMeta(documentation = None, tags = Nil, feature = serviceMeta),
        operations = List.empty[OperationModel[SqlOperationMeta]]
      )
      val baseConfigStream = getClass.getClassLoader.getResourceAsStream("python/base_config.json")
      val baseConfigText   =
        try scala.io.Source.fromInputStream(baseConfigStream, "UTF-8").mkString
        finally baseConfigStream.close()
      val baseConfig       = LanguageBaseConfigLoader.loadJson(baseConfigText).toOption.get
      TemplateView(
        subject = service,
        usedTypes = Nil,
        conventions = baseConfig.conventions(None),
        typeRenderer = baseConfig.typeRenderer
      )
    }
  }
}
