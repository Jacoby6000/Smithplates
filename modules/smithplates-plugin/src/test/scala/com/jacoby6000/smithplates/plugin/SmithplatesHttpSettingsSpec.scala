package com.jacoby6000.smithplates.plugin

import cats.data.Validated
import com.jacoby6000.smithplates.codegen.core.planning.OutputId
import com.jacoby6000.smithplates.http.service.renderer.HttpServiceCodegenApiArtifacts
import munit.FunSuite

class SmithplatesHttpSettingsSpec extends FunSuite {
  test("parses language-first HTTP server target with web framework resolved from bundled deck") {
    SmithplatesSettings
      .parseJson("""
        {
          "python": {
            "http": {
              "server": {
                "packageName": "generated.rendering_pipeline_api"
              },
              "outputs": [
                { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
              ]
            }
          }
        }
      """)
      .map(_.http.getOrElse(fail("expected HTTP settings"))) match {
      case Validated.Valid(settings) =>
        assertEquals(settings.languageTargets.keySet, Set("python"))
        val languageTarget = settings.languageTargets("python")
        val outputs        = languageTarget.target.outputs
        assertEquals(outputs.length, 1)
        assertEquals(outputs.head.sourceOutputDir, "src/generated")
        assertEquals(outputs.head.testOutputDir, "tests")
        val target         = languageTarget.target
        assertEquals(target.server.map(_.webFramework), Some(None))
        assertEquals(target.client, None)
        val server         = target.server.getOrElse(fail("expected server target"))
        assertEquals(server.packageName, Some("generated.rendering_pipeline_api"))
      case Validated.Invalid(errors) =>
        fail(errors.map(_.message).toList.mkString("; "))
    }
  }

  test("parses language-first HTTP client target with http library resolved from bundled deck") {
    SmithplatesSettings
      .parseJson("""
        {
          "python": {
            "http": {
              "client": {
                "packageName": "generated.warehouse_api_client"
              },
              "outputs": [
                { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
              ]
            }
          }
        }
      """)
      .map(_.http.getOrElse(fail("expected HTTP settings"))) match {
      case Validated.Valid(settings) =>
        val languageTarget = settings.languageTargets("python")
        val outputs        = languageTarget.target.outputs
        assertEquals(outputs.length, 1)
        assertEquals(outputs.head.sourceOutputDir, "src/generated")
        assertEquals(outputs.head.testOutputDir, "tests")
        val target         = languageTarget.target
        assertEquals(target.server, None)
        assertEquals(target.client.map(_.httpLibrary), Some(None))
        val client         = target.client.getOrElse(fail("expected client target"))
        assertEquals(client.packageName, Some("generated.warehouse_api_client"))
        assertEquals(client.mode, HttpClientMode.Async)
      case Validated.Invalid(errors) =>
        fail(errors.map(_.message).toList.mkString("; "))
    }
  }

  test("parses async, sync, and both HTTP client modes") {
    val modes = List(
      "async" -> HttpClientMode.Async,
      "sync"  -> HttpClientMode.Sync,
      "both"  -> HttpClientMode.Both
    )

    modes.foreach { case (value, expected) =>
      SmithplatesSettings
        .parseJson(s"""
          {
            "python": {
              "http": {
                "client": { "mode": "$value" },
                "outputs": [
                  { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
                ]
              }
            }
          }
        """)
        .map(_.http.getOrElse(fail("expected HTTP settings"))) match {
        case Validated.Valid(settings) =>
          val client = settings.languageTargets("python").target.client.getOrElse(fail("expected client target"))
          assertEquals(client.mode, expected)
        case Validated.Invalid(errors) =>
          fail(errors.map(_.message).toList.mkString("; "))
      }
    }
  }

  test("rejects unsupported HTTP client mode") {
    val errors =
      SmithplatesSettings
        .parseJson("""
          {
            "python": {
              "http": {
                "client": { "mode": "blocking" },
                "outputs": [
                  { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
                ]
              }
            }
          }
        """)
        .swap
        .toOption
        .getOrElse(fail("expected errors"))

    assert(errors.exists(_.message.contains("unsupported HTTP client mode 'blocking'")))
  }

  test("parses combined HTTP server and client targets") {
    SmithplatesSettings
      .parseJson("""
        {
          "python": {
            "http": {
              "server": {
                "packageName": "generated.api"
              },
              "client": {
                "packageName": "generated.api_client"
              },
              "outputs": [
                { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
              ]
            }
          }
        }
      """)
      .map(_.http.getOrElse(fail("expected HTTP settings"))) match {
      case Validated.Valid(settings) =>
        val target = settings.languageTargets("python").target
        assert(target.server.isDefined)
        assert(target.client.isDefined)
      case Validated.Invalid(errors) =>
        fail(errors.map(_.message).toList.mkString("; "))
    }
  }

  test("rejects nested sourceOutputDir under http.server") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "http": {
              "server": {
                "sourceOutputDir": "src/generated"
              },
              "outputs": [
                { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
              ]
            }
          }
        }
      """)
        .swap
        .toOption
        .getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("http.server contains unknown key(s) 'sourceOutputDir'")))
  }

  test("rejects missing server and client objects") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "http": {
              "outputs": [
                { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
              ]
            }
          }
        }
      """)
        .swap
        .toOption
        .getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("requires `server` and/or `client`")))
  }

  test("rejects unsupported web framework") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "http": {
              "server": {
                "webFramework": "flask"
              },
              "outputs": [
                { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
              ]
            }
          }
        }
      """)
        .swap
        .toOption
        .getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("flask")))
    assert(errors.exists(_.message.contains("unknown deck variant")))
  }

  test("rejects unsupported HTTP client library") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "http": {
              "client": {
                "httpLibrary": "aiohttp"
              },
              "outputs": [
                { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
              ]
            }
          }
        }
      """)
        .swap
        .toOption
        .getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("aiohttp")))
    assert(errors.exists(_.message.contains("unknown deck variant")))
  }

  test("rejects unknown key under http.server") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "http": {
              "server": {
                "httpLibrary": "httpx"
              },
              "outputs": [
                { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
              ]
            }
          }
        }
      """)
        .swap
        .toOption
        .getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("http.server")))
    assert(errors.exists(_.message.contains("httpLibrary")))
    assert(errors.exists(_.message.contains("unknown key")))
  }

  test("rejects unknown key under http.client") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "http": {
              "client": {
                "webFramework": "fastapi"
              },
              "outputs": [
                { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
              ]
            }
          }
        }
      """)
        .swap
        .toOption
        .getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("http.client")))
    assert(errors.exists(_.message.contains("webFramework")))
    assert(errors.exists(_.message.contains("unknown key")))
  }

  test("rejects unbundled client-only config when required model templates are missing") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "kotlin": {
            "http": {
              "client": {
                "templateDirectory": "classpath:python/src/http/client"
              },
              "outputs": [
                { "sourceOutputDir": "src", "testOutputDir": "tests" }
              ]
            }
          }
        }
      """)
        .swap
        .toOption
        .getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("missing codegen output deck")))
    assert(errors.exists(_.message.contains("http/models")))
  }

  test("rejects old feature-first HTTP shape") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "http": {
            "python": {
              "server": {}
            }
          }
        }
      """)
        .swap
        .toOption
        .getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("unknown key")))
    assert(errors.exists(_.message.contains("python")))
  }

  test("loads additionalTemplatesDirectory deck and appends HTTP server outputs") {
    SmithplatesSettings
      .parseJson("""
        {
          "python": {
            "http": {
              "server": {
                "additionalTemplatesDirectory": "classpath:additional-templates/python/src/http/server"
              },
              "outputs": [
                { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
              ]
            }
          }
        }
      """)
      .map(_.http.getOrElse(fail("expected HTTP settings"))) match {
      case Validated.Valid(settings) =>
        val server     = settings.languageTargets("python").target.server.getOrElse(fail("expected server target"))
        assertEquals(server.additionalTemplatesDirectory, Some("classpath:additional-templates/python/src/http/server"))
        val bundled    =
          HttpServiceCodegenApiArtifacts.forEnabledFrameworks(
            serverTemplateDirectory = "classpath:python/src/http/server",
            modelsTemplateDirectory = "classpath:python/src/http/models",
            frameworkKeys = List("fastapi"),
            emitModels = true
          )
        val additional =
          ConsumerCodegenOutputs
            .additionalOutputs(
              "classpath:additional-templates/python/src/http/server",
              List("fastapi"),
              getClass.getClassLoader)
            .fold(errors => fail(errors.map(_.message).toList.mkString("; ")), identity)
        settings
          .toServerCodegenSettings(
            "python",
            com.jacoby6000.smithplates.http.model.HttpServiceIr(Nil, Nil),
            "src/generated",
            "tests") match {
          case Validated.Valid(Some(codegenSettings)) =>
            assertEquals(codegenSettings.artifacts, ConsumerCodegenOutputs.compose(bundled, additional))
            assert(codegenSettings.artifacts.exists(_.id.value == "custom.http.server.once"))
          case Validated.Valid(None)                  => fail("expected codegen settings")
          case Validated.Invalid(errors)              => fail(errors.map(_.message).toList.mkString("; "))
        }
      case Validated.Invalid(errors) =>
        fail(errors.map(_.message).toList.mkString("; "))
    }
  }

  test("rejects additional HTTP operation binding with kind filter") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "http": {
              "server": {
                "additionalTemplatesDirectory": "classpath:additional-templates/python/src/http/server-bad-binding"
              },
              "outputs": [
                { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
              ]
            }
          }
        }
      """)
        .swap
        .toOption
        .getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("Operation bindings do not support kind filter")))
  }

  test("accepts additional HTTP deck that overrides a bundled id") {
    SmithplatesSettings
      .parseJson("""
        {
          "python": {
            "http": {
              "server": {
                "additionalTemplatesDirectory": "classpath:additional-templates/python/src/http/server-override"
              },
              "outputs": [
                { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
              ]
            }
          }
        }
      """)
      .map(_.http.getOrElse(fail("expected HTTP settings"))) match {
      case Validated.Valid(settings) =>
        val additional =
          ConsumerCodegenOutputs
            .additionalOutputs(
              "classpath:additional-templates/python/src/http/server-override",
              List("fastapi"),
              getClass.getClassLoader
            )
            .fold(errors => fail(errors.map(_.message).toList.mkString("; ")), identity)
        val overrideId = additional.headOption.flatMap(_.overrides).getOrElse(fail("expected override"))
        assertEquals(overrideId, OutputId("python.http.models.structure"))
        settings
          .toServerCodegenSettings(
            "python",
            com.jacoby6000.smithplates.http.model.HttpServiceIr(Nil, Nil),
            "src/generated",
            "tests") match {
          case Validated.Valid(Some(_))  => ()
          case Validated.Valid(None)     => fail("expected codegen settings")
          case Validated.Invalid(errors) => fail(errors.map(_.message).toList.mkString("; "))
        }
      case Validated.Invalid(errors) =>
        fail(errors.map(_.message).toList.mkString("; "))
    }
  }

  test("loads additionalTemplatesDirectory deck for HTTP client outputs") {
    SmithplatesSettings
      .parseJson("""
        {
          "python": {
            "http": {
              "client": {
                "additionalTemplatesDirectory": "classpath:additional-templates/python/src/http/client"
              },
              "outputs": [
                { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
              ]
            }
          }
        }
      """)
      .map(_.http.getOrElse(fail("expected HTTP settings"))) match {
      case Validated.Valid(settings) =>
        val client = settings.languageTargets("python").target.client.getOrElse(fail("expected client target"))
        assertEquals(client.additionalTemplatesDirectory, Some("classpath:additional-templates/python/src/http/client"))
        settings
          .toClientCodegenSettings(
            "python",
            com.jacoby6000.smithplates.http.model.HttpServiceIr(Nil, Nil),
            "src/generated",
            "tests") match {
          case Validated.Valid(Some(codegenSettings)) =>
            assert(codegenSettings.artifacts.exists(_.id.value == "custom.http.client.once"))
          case Validated.Valid(None)                  => fail("expected codegen settings")
          case Validated.Invalid(errors)              => fail(errors.map(_.message).toList.mkString("; "))
        }
      case Validated.Invalid(errors) =>
        fail(errors.map(_.message).toList.mkString("; "))
    }
  }

  test("rejects removed inline outputs key under http.server") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "http": {
              "server": {
                "outputs": []
              },
              "outputs": [
                { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
              ]
            }
          }
        }
      """)
        .swap
        .toOption
        .getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("http.server contains unknown key(s) 'outputs'")))
  }

  test("parses enableExternalTemplates on the language block for HTTP") {
    SmithplatesSettings
      .parseJson("""
        {
          "python": {
            "enableExternalTemplates": true,
            "http": {
              "server": {},
              "outputs": [
                { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
              ]
            }
          }
        }
      """)
      .map(_.http.getOrElse(fail("expected HTTP settings"))) match {
      case Validated.Valid(settings) =>
        assertEquals(settings.languageTargets("python").enableExternalTemplates, true)
      case Validated.Invalid(errors) =>
        fail(errors.map(_.message).toList.mkString("; "))
    }
  }

  test("rejects additional HTTP deck output id that collides with a bundled id") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "http": {
              "server": {
                "additionalTemplatesDirectory": "classpath:custom-templates/python/src/http/server"
              },
              "outputs": [
                { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
              ]
            }
          }
        }
      """)
        .swap
        .toOption
        .getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("collides with a bundled output id")))
  }

  test("rejects additional HTTP deck with unknown bundled override id") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "http": {
              "server": {
                "additionalTemplatesDirectory": "classpath:additional-templates/python/src/http/server-unknown-override"
              },
              "outputs": [
                { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
              ]
            }
          }
        }
      """)
        .swap
        .toOption
        .getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("unknown bundled output override id")))
  }

  test("rejects HTTP additionalTemplatesDirectory missing outputs.json") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "http": {
              "server": {
                "additionalTemplatesDirectory": "classpath:missing-additional-deck"
              },
              "outputs": [
                { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
              ]
            }
          }
        }
      """)
        .swap
        .toOption
        .getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("missing codegen output deck")))
  }

  test("rejects duplicate ids in additional HTTP deck outputs.json") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "http": {
              "server": {
                "additionalTemplatesDirectory": "classpath:additional-templates/python/src/http/server-duplicate-id"
              },
              "outputs": [
                { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
              ]
            }
          }
        }
      """)
        .swap
        .toOption
        .getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("duplicate output id 'custom.http.dup'")))
  }

  test("rejects additional HTTP deck referencing missing classpath template without enableExternalTemplates") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "http": {
              "server": {
                "additionalTemplatesDirectory": "classpath:additional-templates/python/src/http/server-missing-template"
              },
              "outputs": [
                { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
              ]
            }
          }
        }
      """)
        .swap
        .toOption
        .getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("enableExternalTemplates")))
  }

  test("rejects filesystem HTTP additionalTemplatesDirectory without enableExternalTemplates") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "http": {
              "server": {
                "additionalTemplatesDirectory": "templates/python/tests/http-additional-templates-external/additional-templates"
              },
              "outputs": [
                { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
              ]
            }
          }
        }
      """)
        .swap
        .toOption
        .getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("enableExternalTemplates")))
  }

  test("accepts filesystem HTTP additionalTemplatesDirectory when enableExternalTemplates is true") {
    SmithplatesSettings
      .parseJson("""
        {
          "python": {
            "enableExternalTemplates": true,
            "http": {
              "server": {
                "additionalTemplatesDirectory": "templates/python/tests/http-additional-templates-external/additional-templates"
              },
              "outputs": [
                { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
              ]
            }
          }
        }
      """)
      .toEither
      .getOrElse(fail("expected valid settings"))
  }

  test("rejects additional HTTP client operation binding with kind filter") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "http": {
              "client": {
                "additionalTemplatesDirectory": "classpath:additional-templates/python/src/http/server-bad-binding"
              },
              "outputs": [
                { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
              ]
            }
          }
        }
      """)
        .swap
        .toOption
        .getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("Operation bindings do not support kind filter")))
  }

  test("rejects additional HTTP client deck output id that collides with a bundled id") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "http": {
              "client": {
                "additionalTemplatesDirectory": "classpath:custom-templates/python/src/http/client"
              },
              "outputs": [
                { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
              ]
            }
          }
        }
      """)
        .swap
        .toOption
        .getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("collides with a bundled output id")))
  }

  test("rejects additional HTTP client deck with unknown bundled override id") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "http": {
              "client": {
                "additionalTemplatesDirectory": "classpath:additional-templates/python/src/http/server-unknown-override"
              },
              "outputs": [
                { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
              ]
            }
          }
        }
      """)
        .swap
        .toOption
        .getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("unknown bundled output override id")))
  }

  test("rejects duplicate ids in additional HTTP client deck outputs.json") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "http": {
              "client": {
                "additionalTemplatesDirectory": "classpath:additional-templates/python/src/http/server-duplicate-id"
              },
              "outputs": [
                { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
              ]
            }
          }
        }
      """)
        .swap
        .toOption
        .getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("duplicate output id 'custom.http.dup'")))
  }

  test("rejects additional HTTP client deck referencing missing classpath template without enableExternalTemplates") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "http": {
              "client": {
                "additionalTemplatesDirectory": "classpath:additional-templates/python/src/http/server-missing-template"
              },
              "outputs": [
                { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
              ]
            }
          }
        }
      """)
        .swap
        .toOption
        .getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("enableExternalTemplates")))
  }

  test("rejects removed inline outputs key under http.client") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "http": {
              "client": {
                "outputs": []
              },
              "outputs": [
                { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
              ]
            }
          }
        }
      """)
        .swap
        .toOption
        .getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("http.client contains unknown key(s) 'outputs'")))
  }

  test("parses http.outputs array for per-service codegen scoping") {
    SmithplatesSettings
      .parseJson("""
        {
          "python": {
            "http": {
              "server": { "webFramework": "fastapi" },
              "outputs": [
                {
                  "services": ["ServerApi"],
                  "sourceOutputDir": "src/generated/server",
                  "testOutputDir": "tests/server",
                  "packageName": "generated.server"
                },
                {
                  "services": ["RunnerApi"],
                  "sourceOutputDir": "src/generated/runner",
                  "testOutputDir": "tests/runner"
                }
              ]
            }
          }
        }
      """)
      .map(_.http.getOrElse(fail("expected HTTP settings"))) match {
      case Validated.Valid(settings) =>
        val target  = settings.languageTargets("python").target
        val outputs = target.outputs
        assertEquals(outputs.length, 2)

        val first = outputs.head
        assertEquals(first.services, Some(List("ServerApi")))
        assertEquals(first.sourceOutputDir, "src/generated/server")
        assertEquals(first.testOutputDir, "tests/server")
        assertEquals(first.packageName, Some("generated.server"))

        val second = outputs(1)
        assertEquals(second.services, Some(List("RunnerApi")))
        assertEquals(second.sourceOutputDir, "src/generated/runner")
        assertEquals(second.testOutputDir, "tests/runner")
        assertEquals(second.packageName, None)
      case Validated.Invalid(errors) =>
        fail(errors.map(_.message).toList.mkString("; "))
    }
  }

  test("withOutputEntryOverrides applies packageName overrides") {
    val target = SmithplatesHttpLanguageTarget(
      target = HttpLanguageTarget(
        server = Some(
          HttpServerTarget(
            webFramework = Some("fastapi"),
            templateDirectory = None,
            additionalTemplatesDirectory = None,
            packageName = Some("generated.default")
          )),
        client = None,
        rootNamespace = None,
        modelsPackageName = None,
        outputs = Nil
      ),
      enableExternalTemplates = false
    )

    val overrides = HttpServiceOutputEntry(
      services = Some(List("ServerApi")),
      sourceOutputDir = "src/generated/server",
      testOutputDir = "server/tests",
      packageName = Some("generated.server")
    )

    val overridden = target.withOutputEntryOverrides(overrides)
    assertEquals(overridden.target.server.getOrElse(fail("expected server")).packageName, Some("generated.server"))
  }

  test("empty services list is treated as None (all services)") {
    SmithplatesSettings
      .parseJson("""
        {
          "python": {
            "http": {
              "server": {},
              "outputs": [
                { "services": [], "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
              ]
            }
          }
        }
      """)
      .map(_.http.getOrElse(fail("expected HTTP settings"))) match {
      case Validated.Valid(settings) =>
        val outputs = settings.languageTargets("python").target.outputs
        assertEquals(outputs.head.services, None)
      case Validated.Invalid(errors) =>
        fail(errors.map(_.message).toList.mkString("; "))
    }
  }

  test("services list with empty strings filters out blanks") {
    SmithplatesSettings
      .parseJson("""
        {
          "python": {
            "http": {
              "server": {},
              "outputs": [
                { "services": ["", "ServerApi", ""], "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
              ]
            }
          }
        }
      """)
      .map(_.http.getOrElse(fail("expected HTTP settings"))) match {
      case Validated.Valid(settings) =>
        val outputs = settings.languageTargets("python").target.outputs
        assertEquals(outputs.head.services, Some(List("ServerApi")))
      case Validated.Invalid(errors) =>
        fail(errors.map(_.message).toList.mkString("; "))
    }
  }

  test("rejects SQL outputs with empty outputs array") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "sql": {
              "rootNamespace": "generated",
              "outputs": []
            }
          }
        }
      """)
        .swap
        .toOption
        .getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("requires at least one entry in `outputs`")))
  }
}
