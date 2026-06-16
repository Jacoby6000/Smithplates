package com.jacoby6000.smithplates.plugin

import cats.data.Validated
import munit.FunSuite
import software.amazon.smithy.model.node.Node

class SmithplatesHttpSettingsSpec extends FunSuite {
  test("parses language-first HTTP server target with default fastapi web framework") {
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
        assertEquals(target.server.map(_.webFramework), Some("fastapi"))
        assertEquals(target.client, None)
        val server = target.server.getOrElse(fail("expected server target"))
        assertEquals(server.sourceOutputDir, "src/generated")
        assertEquals(server.testOutputDir, "tests")
        assertEquals(server.packageName, Some("generated.rendering_pipeline_api"))
      case Validated.Invalid(errors) =>
        fail(errors.map(_.message).toList.mkString("; "))
    }
  }

  test("parses language-first HTTP client target with default httpx library") {
    val node =
      Node
        .parse("""
        {
          "python": {
            "http": {
              "client": {
                "sourceOutputDir": "src/generated",
                "testOutputDir": "tests",
                "packageName": "generated.warehouse_api_client"
              }
            }
          }
        }
      """)
        .expectObjectNode()

    SmithplatesSettings.fromNode(node).map(_.http.getOrElse(fail("expected HTTP settings"))) match {
      case Validated.Valid(settings) =>
        val target = settings.languageTargets("python")
        assertEquals(target.server, None)
        assertEquals(target.client.map(_.httpLibrary), Some("httpx"))
        val client = target.client.getOrElse(fail("expected client target"))
        assertEquals(client.sourceOutputDir, "src/generated")
        assertEquals(client.testOutputDir, "tests")
        assertEquals(client.packageName, Some("generated.warehouse_api_client"))
      case Validated.Invalid(errors) =>
        fail(errors.map(_.message).toList.mkString("; "))
    }
  }

  test("parses combined HTTP server and client targets") {
    val node =
      Node
        .parse("""
        {
          "python": {
            "http": {
              "server": {
                "sourceOutputDir": "src/generated",
                "testOutputDir": "tests",
                "packageName": "generated.api"
              },
              "client": {
                "sourceOutputDir": "src/generated",
                "testOutputDir": "tests",
                "packageName": "generated.api_client"
              }
            }
          }
        }
      """)
        .expectObjectNode()

    SmithplatesSettings.fromNode(node).map(_.http.getOrElse(fail("expected HTTP settings"))) match {
      case Validated.Valid(settings) =>
        val target = settings.languageTargets("python")
        assert(target.server.isDefined)
        assert(target.client.isDefined)
      case Validated.Invalid(errors) =>
        fail(errors.map(_.message).toList.mkString("; "))
    }
  }

  test("rejects missing server and client objects") {
    val node =
      Node
        .parse("""
        {
          "python": {
            "http": {}
          }
        }
      """)
        .expectObjectNode()

    val errors = SmithplatesSettings.fromNode(node).swap.toOption.getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("requires `server` and/or `client`")))
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

  test("rejects unsupported HTTP client library") {
    val node =
      Node
        .parse("""
        {
          "python": {
            "http": {
              "client": {
                "httpLibrary": "aiohttp",
                "sourceOutputDir": "src/generated",
                "testOutputDir": "tests"
              }
            }
          }
        }
      """)
        .expectObjectNode()

    val errors = SmithplatesSettings.fromNode(node).swap.toOption.getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("httpLibrary")))
    assert(errors.exists(_.message.contains("aiohttp")))
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
