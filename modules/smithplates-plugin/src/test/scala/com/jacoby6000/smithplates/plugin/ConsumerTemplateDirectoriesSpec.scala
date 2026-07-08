package com.jacoby6000.smithplates.plugin

import munit.FunSuite

class ConsumerTemplateDirectoriesSpec extends FunSuite {
  test("normalize preserves classpath roots") {
    assertEquals(
      ConsumerTemplateDirectories.normalize("classpath:additional-templates/python/src/db"),
      "classpath:additional-templates/python/src/db"
    )
  }

  test("normalize preserves file roots") {
    assertEquals(
      ConsumerTemplateDirectories.normalize("file:/tmp/consumer-templates"),
      "file:/tmp/consumer-templates"
    )
  }

  test("normalize rewrites bare filesystem paths to absolute file roots") {
    val normalized = ConsumerTemplateDirectories.normalize("/tmp/consumer-templates")
    assert(normalized.startsWith("file:"))
    assert(normalized.endsWith("/tmp/consumer-templates"))
  }

  test("requiresEnableExternalTemplates is false for classpath directories") {
    assertEquals(
      ConsumerTemplateDirectories.requiresEnableExternalTemplates("classpath:additional-templates/python/src/db"),
      false
    )
  }

  test("requiresEnableExternalTemplates is true for filesystem directories") {
    assertEquals(
      ConsumerTemplateDirectories.requiresEnableExternalTemplates("/tmp/consumer-templates"),
      true
    )
    assertEquals(
      ConsumerTemplateDirectories.requiresEnableExternalTemplates("file:/tmp/consumer-templates"),
      true
    )
  }
}
