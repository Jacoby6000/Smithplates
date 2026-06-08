package com.jacoby6000.smithy.stache.sql.codegen

class SspFragmentsSpec extends munit.FunSuite {
  private def minimalServiceView: ServiceTemplateView =
    ServiceTemplateView(
      serviceShapeId = "example#WidgetRepository",
      serviceName = "WidgetRepository",
      dialectKey = "sqlite",
      models = Nil,
      unions = Nil,
      operations = Nil,
      usedJsonTypeNames = Set.empty,
      usedJsonTypeNamesCol = Set.empty,
      classRowFactories = Nil,
      integrationTest = None
    )

  test("top-level template render prepends generated file header") {
    val output =
      ScalateSspTemplateEngine.renderClasspathTemplate(
        "classpath:sql-service-codegen/python/db/service_protocol.ssp",
        minimalServiceView,
        Some("sql-service-codegen/python")
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
        "sql-service-codegen/python",
        "fragments/row_readers/read_str_postgres",
        Map.empty
      )

    assert(!output.contains("Generated from"), s"partials must not include generated header, got: $output")
  }

  test("row reader fragment preserves internal newlines") {
    val output =
      ScalateSspTemplateEngine.renderClasspathPartial(
        "sql-service-codegen/python",
        "fragments/row_readers/read_str_postgres",
        Map.empty
      )

    assert(output.contains("def _read_str"), s"expected function definition, got: $output")
    assert(output.contains("\n    value = row[index]"), s"expected indented body lines, got: $output")
  }

  test("single-line import fragment does not add blank lines") {
    val output =
      ScalateSspTemplateEngine.renderClasspathPartial(
        "sql-service-codegen/python",
        "fragments/helpers/imports_postgres",
        Map(
          "ctx" -> ServiceTemplateView(
            serviceShapeId = "example#Service",
            serviceName = "ExampleService",
            dialectKey = "postgres",
            models = Nil,
            unions = Nil,
            operations = Nil,
            usedJsonTypeNames = Set.empty,
            usedJsonTypeNamesCol = Set.empty,
            classRowFactories = Nil,
            integrationTest = None
          )
        )
      )

    assertEquals(output, "")
  }

  test("member lines fragment preserves newlines") {
    val output =
      ScalateSspTemplateEngine.renderClasspathPartial(
        "sql-service-codegen/python",
        "fragments/models/member_lines",
        Map(
          "members" -> List(
            TemplateMemberView(
              name = "street",
              typeName = "String",
              optional = false,
              last = false
            ),
            TemplateMemberView(
              name = "city",
              typeName = "String",
              optional = false,
              last = true
            )
          )
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
