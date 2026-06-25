package com.jacoby6000.smithplates.codegen.scalate

import cats.data.Validated
import com.jacoby6000.smithplates.codegen.core.planning.TemplateView
import com.jacoby6000.smithplates.codegen.core.strategy.Conventions
import com.jacoby6000.smithplates.codegen.core.strategy.NamingConvention
import com.jacoby6000.smithplates.codegen.core.strategy.NamingStrategy
import munit.FunSuite

class ScalateTemplateRendererSpec extends FunSuite {
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
      )
    )

  test("render passes the template view as ctx") {
    val renderer =
      ScalateTemplateRenderer(
        { (templatePath, attributes, templateRoot) =>
          assertEquals(templatePath, "templates/example.ssp")
          assertEquals(templateRoot, Some("root"))
          val view = attributes("ctx").asInstanceOf[TemplateView[String, Unit]]
          assertEquals(view.subject, "subject")
          assertEquals(view.conventions, conventions)
          "rendered"
        },
        templateRoot = Some("root")
      )

    val result =
      renderer.render(
        "templates/example.ssp",
        TemplateView(subject = "subject", usedTypes = Nil, conventions = conventions)
      )

    assertEquals(result, Validated.Valid("rendered"))
  }

  test("render surfaces template failures") {
    val renderer =
      ScalateTemplateRenderer((_, _, _) => throw new RuntimeException())

    renderer.render("templates/broken.ssp", TemplateView("subject", Nil, conventions)) match {
      case Validated.Invalid(errors) =>
        assert(errors.head.message.contains("templates/broken.ssp"))
      case other                     =>
        fail(s"Expected render failure, got $other")
    }
  }
}
