package com.jacoby6000.smithy.stache.sql.codegen

class MustachePartialsSpec extends munit.FunSuite {
  test("row reader partial preserves internal newlines") {
    val output =
      MustacheTemplateEngine.renderClasspathPartial(
        "sql-service-codegen/python",
        "partials/row_readers/read_str_postgres",
        Map(
          "rowTypeName" -> "tuple[Any, ...]"
        )
      )

    assert(output.contains("def _read_str"), s"expected function definition, got: $output")
    assert(output.contains("\n    value = row[index]"), s"expected indented body lines, got: $output")
  }

  test("single-line import partial does not add blank lines") {
    val output =
      MustacheTemplateEngine.renderClasspathPartial(
        "sql-service-codegen/python",
        "partials/helpers/imports",
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

  test("member lines partial preserves newlines") {
    val output =
      MustacheTemplateEngine.renderClasspathPartial(
        "sql-service-codegen/python",
        "partials/models/member_lines",
        Map(
          "members" -> List(
            Map(
              "name"           -> "street",
              "pythonTypeName" -> "str",
              "optional"       -> false,
              "last"           -> false
            ),
            Map(
              "name"           -> "city",
              "pythonTypeName" -> "str",
              "optional"       -> false,
              "last"           -> true
            )
          ),
          "i4"      -> "    "
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
