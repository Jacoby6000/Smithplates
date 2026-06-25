package com.jacoby6000.smithplates.codegen.core.planning

import cats.data.Validated
import com.jacoby6000.smithplates.codegen.core.ModelId
import com.jacoby6000.smithplates.codegen.core.UnresolvedPathPlaceholder
import com.jacoby6000.smithplates.codegen.core.strategy.Conventions
import com.jacoby6000.smithplates.codegen.core.strategy.NamingConvention
import com.jacoby6000.smithplates.codegen.core.strategy.NamingStrategy
import munit.FunSuite

class PathTemplateSpec extends FunSuite {
  private val conventions =
    Conventions.fromStrategy(
      NamingStrategy(
        fileNames = NamingConvention.SnakeCase.withSuffix(".py"),
        packageSeparator = ".",
        classNames = NamingConvention.Unchanged,
        packageNames = NamingConvention.Unchanged,
        valueNames = NamingConvention.SnakeCase,
        constantNames = NamingConvention.ScreamingSnakeCase,
        functionNames = NamingConvention.SnakeCase
      ),
      Some("generated")
    )

  test("expand replaces known placeholders") {
    val bindings =
      PathBindings(
        Map(
          "smithyNamespaceDir" -> "example/api",
          "serviceFileName"    -> "widget_repository",
          "modelFileName"      -> "widget_output.py"
        )
      )

    assertEquals(
      PathTemplate.expand("{{smithyNamespaceDir}}/models/{{modelFileName}}", bindings),
      com.jacoby6000.smithplates.codegen.core.CodegenValidated.valid("example/api/models/widget_output.py")
    )
  }

  test("expand errors on unresolved placeholders") {
    val result = PathTemplate.expand("apis/{{tagName}}_api.py", PathBindings.empty)
    result match {
      case Validated.Invalid(errors) =>
        assertEquals(errors.head, UnresolvedPathPlaceholder("{{tagName}}"))
      case other                     =>
        fail(s"Expected invalid expansion, got $other")
    }
  }

  test("serviceBindings populate service placeholders from conventions") {
    val bindings = PathTemplate.serviceBindings(conventions, ModelId("example.api", "WidgetRepository"))
    assertEquals(bindings.bindings("serviceFileName"), "widget_repository.py")
    assertEquals(bindings.bindings("serviceClassName"), "WidgetRepository")
    assertEquals(bindings.bindings("smithyNamespaceDir"), "example/api")
    assertEquals(bindings.bindings("packageName"), "generated.example.api")
    assertEquals(bindings.bindings("rootNamespaceDir"), "generated")
  }

  test("modelBindings populate model placeholders from conventions") {
    val bindings = PathTemplate.modelBindings(conventions, ModelId("example.api", "WidgetOutput"))
    assertEquals(bindings.bindings("modelFileName"), "widget_output.py")
    assertEquals(bindings.bindings("modelClassName"), "WidgetOutput")
    assertEquals(bindings.bindings("rootNamespaceDir"), "generated")
  }

  test("tagBinding snake_cases tag names") {
    val bindings = PathTemplate.tagBinding("RouteGroup", conventions)
    assertEquals(bindings.bindings("tagName"), "route_group")
  }
}
