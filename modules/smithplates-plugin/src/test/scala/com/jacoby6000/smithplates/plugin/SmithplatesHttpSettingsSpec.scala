package com.jacoby6000.smithplates.plugin

import cats.data.Validated
import munit.FunSuite
import software.amazon.smithy.model.node.Node

class SmithplatesHttpSettingsSpec extends FunSuite {
  test("parses language-first HTTP target with default fastapi web framework") {
    val node =
      Node
        .parse("""
        {
          "python": {
            "http": {
              "server": {
                "sourceOutputDir": "src/generated",
                "testOutputDir": "tests",
                "packageName": "generated.rendering_pipeline_api"
              }
            }
          }
        }
      """)
        .expectObjectNode()

    SmithplatesSettings.fromNode(node).map(_.http.getOrElse(fail("expected HTTP settings"))) match {
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
            "http": {
              "sourceOutputDir": "src/generated",
              "testOutputDir": "tests"
            }
          }
        }
      """)
        .expectObjectNode()

    val errors = SmithplatesSettings.fromNode(node).swap.toOption.getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("requires `server`")))
  }

  test("rejects unsupported web framework") {
    val node =
      Node
        .parse("""
        {
          "python": {
            "http": {
              "server": {
                "webFramework": "flask",
                "sourceOutputDir": "src/generated",
                "testOutputDir": "tests"
              }
            }
          }
        }
      """)
        .expectObjectNode()

    val errors = SmithplatesSettings.fromNode(node).swap.toOption.getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("webFramework")))
    assert(errors.exists(_.message.contains("flask")))
  }

  test("rejects old feature-first HTTP shape") {
    val node =
      Node
        .parse("""
        {
          "http": {
            "python": {
              "server": {
                "sourceOutputDir": "src/generated",
                "testOutputDir": "tests"
              }
            }
          }
        }
      """)
        .expectObjectNode()

    val errors = SmithplatesSettings.fromNode(node).swap.toOption.getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("expected `sql` or `http`")))
  }
}
