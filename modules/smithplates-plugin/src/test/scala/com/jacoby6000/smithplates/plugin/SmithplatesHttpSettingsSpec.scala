package com.jacoby6000.smithplates.plugin

import cats.data.Validated
import munit.FunSuite
import software.amazon.smithy.model.node.Node

class SmithplatesHttpSettingsSpec extends FunSuite {
  test("parses language target with default fastapi web framework") {
    val node =
      Node
        .parse("""
        {
          "python": {
            "server": {
              "sourceOutputDir": "src/generated",
              "testOutputDir": "tests",
              "packageName": "generated.rendering_pipeline_api"
            }
          }
        }
      """)
        .expectObjectNode()

    SmithplatesHttpSettings.fromNode(node) match {
      case Validated.Valid(settings) =>
        assertEquals(settings.languageTargets.keySet, Set("python"))
        val target = settings.languageTargets("python")
        assertEquals(target.webFramework, "fastapi")
        assertEquals(target.sourceOutputDir, "src/generated")
        assertEquals(target.testOutputDir, "tests")
        assertEquals(target.packageName, Some("generated.rendering_pipeline_api"))
      case Validated.Invalid(errors) =>
        fail(errors.map(_.message).toList.mkString("; "))
    }
  }

  test("rejects missing server object") {
    val node =
      Node
        .parse("""
        {
          "python": {
            "sourceOutputDir": "src/generated",
            "testOutputDir": "tests"
          }
        }
      """)
        .expectObjectNode()

    val errors = SmithplatesHttpSettings.fromNode(node).swap.toOption.getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("requires `server`")))
  }

  test("rejects unsupported web framework") {
    val node =
      Node
        .parse("""
        {
          "python": {
            "server": {
              "webFramework": "flask",
              "sourceOutputDir": "src/generated",
              "testOutputDir": "tests"
            }
          }
        }
      """)
        .expectObjectNode()

    val errors = SmithplatesHttpSettings.fromNode(node).swap.toOption.getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("webFramework")))
    assert(errors.exists(_.message.contains("flask")))
  }
}
