package com.jacoby6000.smithplates.plugin

import cats.data.Validated
import com.jacoby6000.smithplates.codegen.core.planning.OutputId
import com.jacoby6000.smithplates.http.service.renderer.HttpServiceCodegenApiArtifacts
import munit.FunSuite

class SmithplatesHttpSettingsSpec extends FunSuite {
  test("parses language-first HTTP server target with default fastapi web framework") {
    SmithplatesSettings
      .parseJson("""
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
      .map(_.http.getOrElse(fail("expected HTTP settings"))) match {
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
    SmithplatesSettings
      .parseJson("""
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
      .map(_.http.getOrElse(fail("expected HTTP settings"))) match {
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
    SmithplatesSettings
      .parseJson("""
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
            "sourceOutputDir": "src/generated",
            "testOutputDir": "tests",
            "http": {}
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
        .swap
        .toOption
        .getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("webFramework")))
    assert(errors.exists(_.message.contains("flask")))
  }

  test("rejects unsupported HTTP client library") {
    val errors =
      SmithplatesSettings
        .parseJson("""
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
        .swap
        .toOption
        .getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("httpLibrary")))
    assert(errors.exists(_.message.contains("aiohttp")))
  }

  test("rejects unknown key under http.server") {
    val errors =
      SmithplatesSettings
        .parseJson("""
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
    assert(errors.exists(_.message.contains("requires `sourceOutputDir`")))
  }

  test("loads additionalTemplatesDirectory deck and appends HTTP server outputs") {
    SmithplatesSettings
      .parseJson("""
        {
          "python": {
            "sourceOutputDir": "src/generated",
            "testOutputDir": "tests",
            "http": {
              "server": {
                "additionalTemplatesDirectory": "classpath:additional-templates/python/src/http/server"
              }
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
          .toServerCodegenSettings("python", com.jacoby6000.smithplates.http.model.HttpServiceIr(Nil, Nil)) match {
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
            "sourceOutputDir": "src/generated",
            "testOutputDir": "tests",
            "http": {
              "server": {
                "additionalTemplatesDirectory": "classpath:additional-templates/python/src/http/server-bad-binding"
              }
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
            "sourceOutputDir": "src/generated",
            "testOutputDir": "tests",
            "http": {
              "server": {
                "additionalTemplatesDirectory": "classpath:additional-templates/python/src/http/server-override"
              }
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
          .toServerCodegenSettings("python", com.jacoby6000.smithplates.http.model.HttpServiceIr(Nil, Nil)) match {
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
            "sourceOutputDir": "src/generated",
            "testOutputDir": "tests",
            "http": {
              "client": {
                "additionalTemplatesDirectory": "classpath:additional-templates/python/src/http/client"
              }
            }
          }
        }
      """)
      .map(_.http.getOrElse(fail("expected HTTP settings"))) match {
      case Validated.Valid(settings) =>
        val client = settings.languageTargets("python").target.client.getOrElse(fail("expected client target"))
        assertEquals(client.additionalTemplatesDirectory, Some("classpath:additional-templates/python/src/http/client"))
        settings
          .toClientCodegenSettings("python", com.jacoby6000.smithplates.http.model.HttpServiceIr(Nil, Nil)) match {
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
            "sourceOutputDir": "src/generated",
            "testOutputDir": "tests",
            "http": {
              "server": {
                "outputs": []
              }
            }
          }
        }
      """)
        .swap
        .toOption
        .getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("http.server contains unknown key(s) 'outputs'")))
  }
}
