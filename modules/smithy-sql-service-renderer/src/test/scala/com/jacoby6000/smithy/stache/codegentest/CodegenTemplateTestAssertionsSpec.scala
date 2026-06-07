package com.jacoby6000.smithy.stache.codegentest

import munit.FunSuite

class CodegenTemplateTestAssertionsSpec extends FunSuite {
  private val variant = CodegenTemplateVariant("python", "db", "sqlite")

  private val testCase =
    CodegenTemplateTestCase(
      name = "sample-case",
      resourceBasePath = "codegen-template-tests/sample-case",
      smithyModelId = "codegen-template-tests/sample-case/smithy/smithy-files.smithy",
      smithyContent = "",
      expectedOutputsByVariant = Map(
        variant -> List(
          CodegenTemplateExpectedFile("python/src/example.py", "expected\n")
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
    assert(thrown.getMessage.contains("Missing: python/src/example.py"))
  }

  test("CodegenTemplateTestAssertions - reports unexpected generated files") {
    val thrown =
      intercept[AssertionError] {
        CodegenTemplateTestAssertions.assertRenderedOutputs(
          testCase,
          variant,
          rendered = Map(
            "python/src/example.py" -> "expected\n",
            "python/src/extra.py"   -> "extra\n"
          )
        )
      }

    assert(thrown.getMessage.contains("unexpected output file(s) were generated"))
    assert(thrown.getMessage.contains("Unexpected: python/src/extra.py"))
  }

  test("CodegenTemplateTestAssertions - reports content diff on mismatch") {
    val thrown =
      intercept[AssertionError] {
        CodegenTemplateTestAssertions.assertRenderedOutputs(
          testCase,
          variant,
          rendered = Map("python/src/example.py" -> "actual\n")
        )
      }

    assert(
      thrown.getMessage.contains(
        "Content mismatch for codegen-template-tests/sample-case/python/src/example.py"
      )
    )
    assert(thrown.getMessage.contains("| - expected"))
    assert(thrown.getMessage.contains("| + actual"))
  }
}
