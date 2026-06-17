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
            "sourceOutputDir": "src/generated",
            "testOutputDir": "tests",
            "http": {
              "server": {
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
        val languageTarget = settings.languageTargets("python")
        assertEquals(languageTarget.sourceOutputDir, "src/generated")
        assertEquals(languageTarget.testOutputDir, "tests")
        val target         = languageTarget.target
        assertEquals(target.server.map(_.webFramework), Some("fastapi"))
        assertEquals(target.client, None)
        val server         = target.server.getOrElse(fail("expected server target"))
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
            "sourceOutputDir": "src/generated",
            "testOutputDir": "tests",
            "http": {
              "client": {
                "packageName": "generated.warehouse_api_client"
              }
            }
          }
        }
      """)
        .expectObjectNode()

    SmithplatesSettings.fromNode(node).map(_.http.getOrElse(fail("expected HTTP settings"))) match {
      case Validated.Valid(settings) =>
        val languageTarget = settings.languageTargets("python")
        assertEquals(languageTarget.sourceOutputDir, "src/generated")
        assertEquals(languageTarget.testOutputDir, "tests")
        val target         = languageTarget.target
        assertEquals(target.server, None)
        assertEquals(target.client.map(_.httpLibrary), Some("httpx"))
        val client         = target.client.getOrElse(fail("expected client target"))
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
            "sourceOutputDir": "src/generated",
            "testOutputDir": "tests",
            "http": {
              "server": {
                "packageName": "generated.api"
              },
              "client": {
                "packageName": "generated.api_client"
              }
            }
          }
        }
      """)
        .expectObjectNode()

    SmithplatesSettings.fromNode(node).map(_.http.getOrElse(fail("expected HTTP settings"))) match {
      case Validated.Valid(settings) =>
        val target = settings.languageTargets("python").target
        assert(target.server.isDefined)
        assert(target.client.isDefined)
      case Validated.Invalid(errors) =>
        fail(errors.map(_.message).toList.mkString("; "))
    }
  }

  test("rejects nested sourceOutputDir under http.server") {
    val node =
      Node
        .parse("""
        {
          "python": {
            "sourceOutputDir": "src/generated",
            "testOutputDir": "tests",
            "http": {
              "server": {
                "sourceOutputDir": "src/generated"
              }
            }
          }
        }
      """)
        .expectObjectNode()

    val errors = SmithplatesSettings.fromNode(node).swap.toOption.getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("http.server must not set sourceOutputDir")))
  }

  test("rejects missing server and client objects") {
    val node =
      Node
        .parse("""
        {
          "python": {
            "sourceOutputDir": "src/generated",
            "testOutputDir": "tests",
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
            "sourceOutputDir": "src/generated",
            "testOutputDir": "tests",
            "http": {
              "server": {
                "webFramework": "flask"
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
            "sourceOutputDir": "src/generated",
            "testOutputDir": "tests",
            "http": {
              "client": {
                "httpLibrary": "aiohttp"
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

  test("rejects unknown key under http.server") {
    val node =
      Node
        .parse("""
        {
          "python": {
            "sourceOutputDir": "src/generated",
            "testOutputDir": "tests",
            "http": {
              "server": {
                "httpLibrary": "httpx"
              }
            }
          }
        }
      """)
        .expectObjectNode()

    val errors = SmithplatesSettings.fromNode(node).swap.toOption.getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("http.server")))
    assert(errors.exists(_.message.contains("httpLibrary")))
    assert(errors.exists(_.message.contains("unknown key")))
  }

  test("rejects unknown key under http.client") {
    val node =
      Node
        .parse("""
        {
          "python": {
            "sourceOutputDir": "src/generated",
            "testOutputDir": "tests",
            "http": {
              "client": {
                "webFramework": "fastapi"
              }
            }
          }
        }
      """)
        .expectObjectNode()

    val errors = SmithplatesSettings.fromNode(node).swap.toOption.getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("http.client")))
    assert(errors.exists(_.message.contains("webFramework")))
    assert(errors.exists(_.message.contains("unknown key")))
  }

  test("rejects unbundled client-only config when required model templates are missing") {
    val node =
      Node
        .parse("""
        {
          "kotlin": {
            "sourceOutputDir": "src",
            "testOutputDir": "tests",
            "http": {
              "client": {
                "templateDirectory": "classpath:python/src/http/client"
              }
            }
          }
        }
      """)
        .expectObjectNode()

    val errors = SmithplatesSettings.fromNode(node).swap.toOption.getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("missing required templates")))
    assert(errors.exists(_.message.contains("http/models")))
  }

  test("rejects old feature-first HTTP shape") {
    val node =
      Node
        .parse("""
        {
          "http": {
            "python": {
              "server": {}
            }
          }
        }
      """)
        .expectObjectNode()

    val errors = SmithplatesSettings.fromNode(node).swap.toOption.getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("requires `sourceOutputDir`")))
  }
}
