package com.jacoby6000.smithplates.http.service.renderer

import com.jacoby6000.smithplates.codegen.TemplateOutputPrefix
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

  test("derives package names from root namespace and template layout") {
    assertEquals(
      TemplateOutputPrefix.toPackageName("http/server", Some("generated")),
      "generated.http.server"
    )
  }
}
