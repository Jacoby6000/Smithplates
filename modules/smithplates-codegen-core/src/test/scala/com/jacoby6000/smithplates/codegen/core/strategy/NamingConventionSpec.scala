package com.jacoby6000.smithplates.codegen.core.strategy

import munit.FunSuite

class NamingConventionSpec extends FunSuite {
  test("SnakeCase splits acronyms and words") {
    assertEquals(Conventions.internal.applyCase(NamingConventionStyle.SnakeCase, "parentNodeId"), "parent_node_id")
    assertEquals(Conventions.internal.applyCase(NamingConventionStyle.SnakeCase, "HTTPStatus"), "http_status")
    assertEquals(Conventions.internal.applyCase(NamingConventionStyle.SnakeCase, "WidgetOutput"), "widget_output")
  }

  test("PascalCase joins segments") {
    assertEquals(Conventions.internal.applyCase(NamingConventionStyle.PascalCase, "widget_output"), "WidgetOutput")
    assertEquals(Conventions.internal.applyCase(NamingConventionStyle.PascalCase, "WidgetOutput"), "WidgetOutput")
  }

  test("CamelCase lowercases the first segment") {
    assertEquals(Conventions.internal.applyCase(NamingConventionStyle.CamelCase, "widget_output"), "widgetOutput")
  }

  test("ScreamingSnakeCase uppercases snake segments") {
    assertEquals(Conventions.internal.applyCase(NamingConventionStyle.ScreamingSnakeCase, "maxRetries"), "MAX_RETRIES")
  }

  test("Unchanged preserves the input") {
    assertEquals(Conventions.internal.applyCase(NamingConventionStyle.Unchanged, "WidgetOutput"), "WidgetOutput")
  }
}
