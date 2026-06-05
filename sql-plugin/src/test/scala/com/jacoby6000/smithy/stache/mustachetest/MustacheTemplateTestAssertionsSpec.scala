package com.jacoby6000.smithy.stache.mustachetest

import munit.FunSuite

class MustacheTemplateTestAssertionsSpec extends FunSuite {
  private val variant = MustacheTemplateVariant("python", "db", "sqlite")

  private val testCase =
    MustacheTemplateTestCase(
      name = "sample-case",
      resourceBasePath = "mustache-template-tests/sample-case",
      smithyModelId = "mustache-template-tests/sample-case/smithy/smithy-files.smithy",
      smithyContent = "",
      expectedOutputsByVariant = Map(
        variant -> List(
          MustacheTemplateExpectedFile("python/src/example.py", "expected\n")
        )
      )
    )

  test("MustacheTemplateTestAssertions - reports missing generated files") {
    val thrown =
      intercept[AssertionError] {
        MustacheTemplateTestAssertions.assertRenderedOutputs(
          testCase,
          variant,
          rendered = Map.empty
        )
      }

    assert(thrown.getMessage.contains("expected output file(s) were not generated"))
    assert(thrown.getMessage.contains("Missing: python/src/example.py"))
  }

  test("MustacheTemplateTestAssertions - reports unexpected generated files") {
    val thrown =
      intercept[AssertionError] {
        MustacheTemplateTestAssertions.assertRenderedOutputs(
          testCase,
          variant,
          rendered = Map(
            "python/src/example.py" -> "expected\n",
            "python/src/extra.py" -> "extra\n"
          )
        )
      }

    assert(thrown.getMessage.contains("unexpected output file(s) were generated"))
    assert(thrown.getMessage.contains("Unexpected: python/src/extra.py"))
  }

  test("MustacheTemplateTestAssertions - reports content diff on mismatch") {
    val thrown =
      intercept[AssertionError] {
        MustacheTemplateTestAssertions.assertRenderedOutputs(
          testCase,
          variant,
          rendered = Map("python/src/example.py" -> "actual\n")
        )
      }

    assert(
      thrown.getMessage.contains(
        "Content mismatch for mustache-template-tests/sample-case/python/src/example.py"
      )
    )
    assert(thrown.getMessage.contains("| - expected"))
    assert(thrown.getMessage.contains("| + actual"))
  }
}
