package com.jacoby6000.smithplates.codegen.core.strategy

import com.jacoby6000.smithplates.codegen.core.ModelId
import munit.FunSuite

class ConventionsSpec extends FunSuite {
  private val strategy =
    NamingStrategy(
      fileNames = NamingConvention.SnakeCase.withSuffix(".py"),
      packageSeparator = ".",
      classNames = NamingConvention.Unchanged,
      packageNames = NamingConvention.Unchanged,
      valueNames = NamingConvention.SnakeCase,
      constantNames = NamingConvention.ScreamingSnakeCase,
      functionNames = NamingConvention.SnakeCase,
      illegalCharRemaps = Map("-" -> "_"),
      reservedKeywordRemaps = Map("class" -> "class_", "from" -> "from_", "id" -> "id_")
    )

  private val conventions = Conventions.fromStrategy(strategy, Some("generated"))

  test("snake_case member and function names") {
    assertEquals(conventions.memberName("parentNodeId"), "parent_node_id")
    assertEquals(conventions.functionName("CreateWidget"), "create_widget")
  }

  test("screaming snake constant names") {
    assertEquals(conventions.constantName("maxRetries"), "MAX_RETRIES")
  }

  test("class names stay unchanged for Smithy PascalCase identifiers") {
    assertEquals(conventions.className(ModelId("example", "WidgetOutput")), "WidgetOutput")
  }

  test("file names use configured suffix") {
    assertEquals(conventions.fileName(ModelId("example", "WidgetOutput")), "widget_output.py")
  }

  test("package and module paths join namespace segments with root namespace") {
    assertEquals(conventions.packageName("example.api"), "generated.example.api")
    assertEquals(conventions.modulePath(ModelId("example.api", "WidgetOutput")), "generated.example.api.widget_output")
  }

  test("illegal char remaps run before reserved keyword remaps") {
    val remapped = Conventions.fromStrategy(
      strategy.copy(
        illegalCharRemaps = Map("-" -> "_"),
        reservedKeywordRemaps = Map("class_name" -> "class_name_")
      )
    )
    assertEquals(remapped.memberName("class-name"), "class_name_")
  }

  test("reserved keyword remaps apply to whole identifiers") {
    assertEquals(conventions.memberName("class"), "class_")
    assertEquals(conventions.memberName("from"), "from_")
    assertEquals(conventions.memberName("id"), "id_")
  }

  test("NamingConvention.withSuffix appends after case conversion") {
    val withSuffix = NamingConvention.SnakeCase.withSuffix(".py")
    assertEquals(withSuffix.suffix, ".py")
    assertEquals(
      Conventions.internal.formatIdentifier(strategy, withSuffix, "WidgetOutput"),
      "widget_output.py"
    )
  }
}
