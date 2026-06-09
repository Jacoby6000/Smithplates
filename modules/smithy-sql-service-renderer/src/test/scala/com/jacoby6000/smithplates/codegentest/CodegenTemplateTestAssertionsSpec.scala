package com.jacoby6000.smithplates.codegentest

import munit.FunSuite

class CodegenTemplateTestAssertionsSpec extends FunSuite {
  private val variant = CodegenTemplateVariant("python", "db", "sqlite")

  private val testCase =
    CodegenTemplateTestCase(
      name = "sample-case",
      resourceBasePath = "python/expected-outputs/sample-case",
      smithyModelId = "python/expected-outputs/sample-case/smithy/smithy-files.smithy",
      smithyContent = "",
      expectedOutputsByVariant = Map(
        variant -> List(
          CodegenTemplateExpectedFile("src/example.py", "expected\n")
        )
      )
    )

  test("CodegenTemplateTestAssertions - reports missing generated files") {
    val thrown =
      intercept[AssertionError] {
        CodegenTemplateTestAssertions.assertRenderedOutputs(
          testCase,
          variant,
          rendered = Map.empty
        )
      }

    assert(thrown.getMessage.contains("expected output file(s) were not generated"))
    assert(thrown.getMessage.contains("Missing: src/example.py"))
  }

  test("CodegenTemplateTestAssertions - reports unexpected generated files") {
    val thrown =
      intercept[AssertionError] {
        CodegenTemplateTestAssertions.assertRenderedOutputs(
          testCase,
          variant,
          rendered = Map(
            "src/example.py" -> "expected\n",
            "src/extra.py"   -> "extra\n"
          )
        )
      }

    assert(thrown.getMessage.contains("unexpected output file(s) were generated"))
    assert(thrown.getMessage.contains("Unexpected: src/extra.py"))
  }

  test("CodegenTemplateTestAssertions - reports content diff on mismatch") {
    val thrown =
      intercept[AssertionError] {
        CodegenTemplateTestAssertions.assertRenderedOutputs(
          testCase,
          variant,
          rendered = Map("src/example.py" -> "actual\n")
        )
      }

    assert(
      thrown.getMessage.contains(
        "Content mismatch for python/expected-outputs/sample-case/src/example.py"
      )
    )
    assert(thrown.getMessage.contains("| - expected"))
    assert(thrown.getMessage.contains("| + actual"))
  }
}
