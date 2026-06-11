package com.jacoby6000.smithplates.sql.service.renderer.codegentest

import munit.FunSuite

class TextContentDiffSpec extends FunSuite {
  test("formats first difference with surrounding context") {
    val message =
      TextContentDiff.formatMismatch(
        fileLabel = "python/expected-outputs/sample/src/example.py",
        expected = "line one\nline two\nline three\n",
        actual = "line one\nline TWO\nline three\n",
        contextLines = 1
      )

    assert(message.contains("Content mismatch for python/expected-outputs/sample/src/example.py"))
    assert(message.contains("First difference near line 2"))
    assert(message.contains("| - line two"))
    assert(message.contains("| + line TWO"))
  }
}
