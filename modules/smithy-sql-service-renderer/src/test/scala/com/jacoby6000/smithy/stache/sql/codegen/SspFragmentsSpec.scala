package com.jacoby6000.smithy.stache.sql.codegen

class SspFragmentsSpec extends munit.FunSuite {
  test("row reader fragment preserves internal newlines") {
    val output =
      ScalateSspTemplateEngine.renderClasspathPartial(
        "sql-service-codegen/python",
        "fragments/row_readers/read_str",
        Map(
          "dialectKey"        -> "postgres",
          "isPostgresDialect" -> true,
          "isSqliteDialect"   -> false
        )
      )

    assert(output.contains("def _read_str"), s"expected function definition, got: $output")
    assert(output.contains("\n    value = row[index]"), s"expected indented body lines, got: $output")
  }

  test("single-line import fragment does not add blank lines") {
    val output =
      ScalateSspTemplateEngine.renderClasspathPartial(
        "sql-service-codegen/python",
        "fragments/helpers/imports",
        Map(
          "needsRowReaderModuleImport" -> false,
          "needsCastImport"            -> true,
          "needsUuidImport"            -> true,
          "usesJson"                   -> false,
          "needsDatetimeImports"       -> false,
          "needsDecimalImport"         -> false
        )
      )

    assertEquals(
      output,
      """from typing import cast
        |import uuid
        |""".stripMargin
    )
  }

  test("member lines fragment preserves newlines") {
    val output =
      ScalateSspTemplateEngine.renderClasspathPartial(
        "sql-service-codegen/python",
        "fragments/models/member_lines",
        Map(
          "members" -> List(
            Map(
              "name"     -> "street",
              "typeName" -> "String",
              "optional" -> false,
              "last"     -> false
            ),
            Map(
              "name"     -> "city",
              "typeName" -> "String",
              "optional" -> false,
              "last"     -> true
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
