package com.jacoby6000.smithplates.codegentest

import munit.FunSuite

import java.nio.file.Paths

class CodegenTemplateTestAssertionsSpec extends FunSuite {
  private val variant = CodegenTemplateVariant("python", "db", "sqlite")

  private val expectedFiles =
    List(
      CodegenTemplateExpectedFile("src/db/sqlite/example.py", "expected\n")
    )

  private val testCase =
    CodegenTemplateTestCase(
      name = "sample-case",
      caseDirectory = Paths.get("templates/python/tests/sample-case"),
      smithyModelId = "smithy/smithy-files.smithy",
      smithyContent = "",
      expectedOutputsByVariant = Map(variant -> expectedFiles)
    )

  test("CodegenTemplateTestAssertions - reports missing generated files") {
    val thrown =
      intercept[AssertionError] {
        CodegenTemplateTestAssertions.assertRenderedOutputs(
          testCase,
          variant,
          expectedFiles,
          rendered = Map.empty
        )
      }

    assert(thrown.getMessage.contains("expected output file(s) were not generated"))
    assert(thrown.getMessage.contains("src/db/sqlite/example.py"))
  }

  test("CodegenTemplateTestAssertions - reports unexpected generated files") {
    val thrown =
      intercept[AssertionError] {
        CodegenTemplateTestAssertions.assertRenderedOutputs(
          testCase,
          variant,
          expectedFiles,
          rendered = Map(
            "src/db/sqlite/example.py" -> "expected\n",
            "src/db/sqlite/extra.py"   -> "extra\n"
          )
        )
      }

    assert(thrown.getMessage.contains("unexpected output file(s) were generated"))
    assert(thrown.getMessage.contains("Unexpected: src/db/sqlite/extra.py"))
  }

  test("CodegenTemplateTestAssertions - reports content diff on mismatch") {
    val thrown =
      intercept[AssertionError] {
        CodegenTemplateTestAssertions.assertRenderedOutputs(
          testCase,
          variant,
          expectedFiles,
          rendered = Map("src/db/sqlite/example.py" -> "actual\n")
        )
      }

    assert(
      thrown.getMessage.contains(
        "Content mismatch for templates/python/tests/sample-case/expected/src/db/sqlite/example.py"
      )
    )
    assert(thrown.getMessage.contains("| - expected"))
    assert(thrown.getMessage.contains("| + actual"))
  }
}
