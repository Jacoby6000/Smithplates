package com.jacoby6000.smithplates.sql.service.renderer.codegentest

import munit.FunSuite

import java.nio.file.Paths

class CodegenTemplateTestAssertionsSpec extends FunSuite {
  test("reports missing generated files") {
    val thrown =
      intercept[AssertionError] {
        CodegenTemplateTestAssertions.assertRenderedOutputs(
          CodegenTemplateTestAssertionsSpec.internal.testCase,
          CodegenTemplateTestAssertionsSpec.internal.variant,
          CodegenTemplateTestAssertionsSpec.internal.expectedFiles,
          rendered = Map.empty
        )
      }

    assert(thrown.getMessage.contains("expected output file(s) were not generated"))
    assert(thrown.getMessage.contains("src/generated/example/sqlite/example.py"))
  }

  test("reports unexpected generated files") {
    val thrown =
      intercept[AssertionError] {
        CodegenTemplateTestAssertions.assertRenderedOutputs(
          CodegenTemplateTestAssertionsSpec.internal.testCase,
          CodegenTemplateTestAssertionsSpec.internal.variant,
          CodegenTemplateTestAssertionsSpec.internal.expectedFiles,
          rendered = Map(
            "src/generated/example/sqlite/example.py" -> "expected\n",
            "src/generated/example/sqlite/extra.py"   -> "extra\n"
          )
        )
      }

    assert(thrown.getMessage.contains("unexpected output file(s) were generated"))
    assert(thrown.getMessage.contains("Unexpected: src/generated/example/sqlite/extra.py"))
  }

  test("reports content diff on mismatch") {
    val thrown =
      intercept[AssertionError] {
        CodegenTemplateTestAssertions.assertRenderedOutputs(
          CodegenTemplateTestAssertionsSpec.internal.testCase,
          CodegenTemplateTestAssertionsSpec.internal.variant,
          CodegenTemplateTestAssertionsSpec.internal.expectedFiles,
          rendered = Map("src/generated/example/sqlite/example.py" -> "actual\n")
        )
      }

    assert(
      thrown.getMessage.contains(
        "Content mismatch for templates/python/tests/sample-case/expected/src/generated/example/sqlite/example.py"
      )
    )
    assert(thrown.getMessage.contains("| - expected"))
    assert(thrown.getMessage.contains("| + actual"))
  }
}
object CodegenTemplateTestAssertionsSpec {

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    val variant       = CodegenTemplateVariant("python", "db", "sqlite")
    val expectedFiles =
      List(
        CodegenTemplateExpectedFile("src/generated/example/sqlite/example.py", "expected\n")
      )
    val testCase      =
      CodegenTemplateTestCase(
        name = "sample-case",
        caseDirectory = Paths.get("templates/python/tests/sample-case"),
        smithyModelId = "smithy/smithy-files.smithy",
        smithyContent = "namespace example",
        smithyNamespace = "example",
        expectedOutputsByVariant = Map(variant -> expectedFiles)
      )
  }
}
