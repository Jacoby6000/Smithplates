package com.jacoby6000.smithplates.plugin.codegentest

import munit.FunSuite

class TemplateBuildLogSpec extends FunSuite {
  test("emits build progress lines") {
    TemplateBuildLog.phase("test-case", "hello from log4j", System.nanoTime())
  }
}
