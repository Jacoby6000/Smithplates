package com.jacoby6000.smithplates.codegen

import munit.FunSuite

class TemplateOutputPrefixSpec extends FunSuite {
  test("strips bundled python/src prefix from template directory") {
    assertEquals(
      TemplateOutputPrefix.fromTemplateDirectory("classpath:python/src/http/server"),
      "http/server"
    )
    assertEquals(
      TemplateOutputPrefix.stripTemplateSourcePrefix("python/src/http/models"),
      "http/models"
    )
  }

  test("strips templates/<language>/src prefix from custom template directory") {
    assertEquals(
      TemplateOutputPrefix.fromTemplateDirectory("classpath:templates/kotlin/src/http/client"),
      "http/client"
    )
  }

  test("derives namespace-aware package names") {
    assertEquals(
      CodegenPackageNames.packageName(Some("generated"), "com.example.api"),
      "generated.com.example.api"
    )
    assertEquals(
      CodegenPackageNames.outputPathPrefix("com.example.inventory"),
      "com/example/inventory"
    )
    assertEquals(
      CodegenPackageNames.packageName(Some("generated"), "foo.bar"),
      "generated.foo.bar"
    )
  }
}
