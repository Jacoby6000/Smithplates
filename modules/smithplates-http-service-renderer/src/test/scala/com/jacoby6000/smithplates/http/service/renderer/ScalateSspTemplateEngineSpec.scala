package com.jacoby6000.smithplates.http.service.renderer

import munit.FunSuite

class ScalateSspTemplateEngineSpec extends FunSuite {
  test("bundled HTTP client naming preamble is on the module classpath") {
    val classLoader  = getClass.getClassLoader
    val resourcePath = "python/src/http/fragments/naming/preamble.ssp"
    assert(
      Option(classLoader.getResource(resourcePath)).nonEmpty,
      s"missing classpath resource: $resourcePath"
    )
    assert(
      Option(classLoader.getResourceAsStream(resourcePath)).nonEmpty,
      s"missing classpath stream: $resourcePath"
    )
  }
}
