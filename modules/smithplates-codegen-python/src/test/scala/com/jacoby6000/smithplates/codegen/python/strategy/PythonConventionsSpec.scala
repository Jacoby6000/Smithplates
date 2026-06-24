package com.jacoby6000.smithplates.codegen.python.strategy

import com.jacoby6000.smithplates.codegen.core.ModelId
import munit.FunSuite

class PythonConventionsSpec extends FunSuite {
  private val conventions = PythonConventions.default(Some("generated"))

  test("default strategy uses snake_case files and members") {
    assertEquals(conventions.fileName(ModelId("example", "WidgetRepository")), "widget_repository.py")
    assertEquals(conventions.memberName("parentNodeId"), "parent_node_id")
    assertEquals(conventions.functionName("GetWidget"), "get_widget")
  }

  test("default strategy remaps Python reserved keywords") {
    assertEquals(conventions.memberName("class"), "class_")
    assertEquals(conventions.memberName("from"), "from_")
    assertEquals(conventions.memberName("id"), "id_")
  }

  test("default strategy preserves Smithy class names and namespace segments") {
    assertEquals(conventions.className(ModelId("example", "WidgetOutput")), "WidgetOutput")
    assertEquals(conventions.packageName("example.api"), "generated.example.api")
  }
}
