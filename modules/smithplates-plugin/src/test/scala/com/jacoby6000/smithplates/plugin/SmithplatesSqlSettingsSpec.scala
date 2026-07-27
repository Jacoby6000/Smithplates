package com.jacoby6000.smithplates.plugin

import com.jacoby6000.smithplates.codegen.core.planning.OutputId
import com.jacoby6000.smithplates.sql.service.renderer.SqlServiceCodegenDbArtifacts
import com.jacoby6000.smithplates.sql.service.renderer.SqlServiceCodegenSettings

class SmithplatesSqlSettingsSpec extends munit.FunSuite {
  test("parses enabled dialect and language-first SQL settings") {
    val settings =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "sql": {
              "postgres": {
                "enable": true,
                "migrationLocation": "db/migrations/postgres"
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
        .sql
        .getOrElse(fail("expected SQL settings"))
    assertEquals(settings.enabledDialectKeys, List("postgres"))
    assertEquals(
      settings.languageTargets.get("python").flatMap(_.target.dialects.get("postgres")).map(_.migrationLocation),
      Some(Some("db/migrations/postgres"))
    )
    assertEquals(settings.languageTargets.keySet, Set("python"))

    assertEquals(settings.dialectMigrationDirectories, Map("postgres" -> "db/migrations/postgres"))

    val codegenSettings =
      settings
        .toCodegenSettings("python", "src/generated", "tests")
        .fold(
          errors => fail(errors.map(_.message).toList.mkString("; ")),
          _.getOrElse(fail("expected codegen settings")))
    assertEquals(codegenSettings.templateDirectory, "classpath:python/src/db")
    assertEquals(codegenSettings.rootNamespace, Some("generated"))
    assertEquals(codegenSettings.packageNameOverride, None)
    assertEquals(codegenSettings.sourceOutputDirectory, Some("src/generated"))
    assertEquals(codegenSettings.testOutputDirectory, Some("tests"))
    assertEquals(
      codegenSettings.artifacts,
      SqlServiceCodegenDbArtifacts.forEnabledDialects("classpath:python/src/db", List("postgres")))
  }

  test("dialect enable defaults to false") {
    val settings =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "sql": {
              "sqlite": {
                "migrationLocation": "db/migrations/sqlite"
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
        .sql
        .getOrElse(fail("expected SQL settings"))
    assertEquals(settings.enabledDialectKeys, Nil)
    assertEquals(settings.dialectMigrationDirectories, Map.empty)
  }

  test("rejects migrationLocation ending in .sql when dialect is enabled") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "sql": {
              "sqlite": {
                "enable": true,
                "migrationLocation": "db/sqlite.sql"
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
    assert(errors.exists(_.message.contains("directory path")))
  }

  test("requires migrationLocation when dialect is enabled") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "sql": {
              "sqlite": {
                "enable": true
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
    assert(errors.exists(_.message.contains("migrationLocation")))
  }

  test("accepts explicit templateDirectory when required templates exist") {
    val settings        =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "sql": {
              "templateDirectory": "classpath:custom-templates/python/src/db",
              "outputs": [
                { "sourceOutputDir": "out/src", "testOutputDir": "out/test" }
              ]
            }
          }
        }
      """)
        .toEither
        .getOrElse(fail("expected valid settings"))
        .sql
        .getOrElse(fail("expected SQL settings"))
    val codegenSettings =
      settings
        .toCodegenSettings("python", "out/src", "out/test")
        .fold(
          errors => fail(errors.map(_.message).toList.mkString("; ")),
          _.getOrElse(fail("expected codegen settings")))
    assertEquals(codegenSettings.templateDirectory, "classpath:custom-templates/python/src/db")
    assertEquals(codegenSettings.defaultDialectKey, SqlServiceCodegenSettings.SharedDialectKey)
    assertEquals(codegenSettings.enabledDialectKeys, Nil)
    assertEquals(codegenSettings.queryRenderers, Map.empty)
    assertEquals(codegenSettings.schemaDdlRenderers, Map.empty)
    assertEquals(
      codegenSettings.artifacts,
      SqlServiceCodegenDbArtifacts.forEnabledDialects("classpath:custom-templates/python/src/db", Nil))
  }

  test("combines bundled artifacts for multiple enabled dialects") {
    val settings        =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "sql": {
              "sqlite": {
                "enable": true,
                "migrationLocation": "db/migrations/sqlite"
              },
              "postgres": {
                "enable": true,
                "migrationLocation": "db/migrations/postgres"
              },
              "outputs": [
                { "sourceOutputDir": "src", "testOutputDir": "tests" }
              ]
            }
          }
        }
      """)
        .toEither
        .getOrElse(fail("expected valid settings"))
        .sql
        .getOrElse(fail("expected SQL settings"))
    assertEquals(settings.enabledDialectKeys, List("sqlite", "postgres"))
    val codegenSettings =
      settings
        .toCodegenSettings("python", "src", "tests")
        .fold(
          errors => fail(errors.map(_.message).toList.mkString("; ")),
          _.getOrElse(fail("expected codegen settings")))
    assertEquals(
      codegenSettings.artifacts,
      SqlServiceCodegenDbArtifacts.forEnabledDialects("classpath:python/src/db", List("sqlite", "postgres"))
    )
  }

  test("rejects nested sourceOutputDir under sql") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "sql": {
              "sourceOutputDir": "src/generated",
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
    assert(errors.exists(_.message.contains("sql contains unknown key(s) 'sourceOutputDir'")))
  }

  test("rejects old feature-first SQL shape") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "sql": {
            "python": {
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
    assert(errors.exists(_.message.contains("unknown key")))
    assert(errors.exists(_.message.contains("python")))
  }

  test("rejects unbundled language without templateDirectory") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "kotlin": {
            "sql": {
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
    assert(errors.exists(_.message.contains("templateDirectory")))
    assert(errors.exists(_.message.contains("kotlin")))
  }

  test("rejects explicit templateDirectory missing required templates") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "kotlin": {
            "sql": {
              "templateDirectory": "classpath:missing-templates/kotlin",
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
  }

  test("rejects explicit templateDirectory missing dialect-specific templates") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "sql": {
              "postgres": {
                "enable": true,
                "migrationLocation": "db/migrations/postgres"
              },
              "templateDirectory": "classpath:custom-templates/python/src/db",
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
    assert(errors.exists(_.message.contains("postgres/service_psycopg.ssp")))
  }

  test("rejects conflicting migration locations for the same dialect across languages") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "sql": {
              "sqlite": {
                "enable": true,
                "migrationLocation": "db/migrations/sqlite"
              },
              "outputs": [
                { "sourceOutputDir": "src/python", "testOutputDir": "tests/python" }
              ]
            }
          },
          "kotlin": {
            "sql": {
              "sqlite": {
                "enable": true,
                "migrationLocation": "db/migrations/kotlin-sqlite"
              },
              "templateDirectory": "classpath:custom-templates/kotlin/src/db",
              "outputs": [
                { "sourceOutputDir": "src/kotlin", "testOutputDir": "tests/kotlin" }
              ]
            }
          }
        }
      """)
        .swap
        .toOption
        .getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("conflicting sqlite migrationLocation")))
  }

  test("loads additionalTemplatesDirectory deck and appends outputs to bundled artifacts") {
    val settings =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "sql": {
              "additionalTemplatesDirectory": "classpath:additional-templates/python/src/db",
              "outputs": [
                { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
              ]
            }
          }
        }
      """)
        .toEither
        .getOrElse(fail("expected valid settings"))
        .sql
        .getOrElse(fail("expected SQL settings"))

    val languageTarget = settings.languageTargets("python")
    assertEquals(
      languageTarget.target.additionalTemplatesDirectory,
      Some("classpath:additional-templates/python/src/db"))

    val codegenSettings =
      settings
        .toCodegenSettings("python", "src/generated", "tests")
        .fold(
          errors => fail(errors.map(_.message).toList.mkString("; ")),
          _.getOrElse(fail("expected codegen settings")))
    val bundled         = SqlServiceCodegenDbArtifacts.forEnabledDialects("classpath:python/src/db", Nil)
    val additional      =
      ConsumerCodegenOutputs
        .additionalOutputs("classpath:additional-templates/python/src/db", Nil, getClass.getClassLoader)
        .fold(errors => fail(errors.map(_.message).toList.mkString("; ")), identity)
    assertEquals(codegenSettings.artifacts, ConsumerCodegenOutputs.compose(bundled, additional))
    assert(codegenSettings.artifacts.exists(_.id.value == "custom.sql.middleware"))
  }

  test("parses enableExternalTemplates on the language block") {
    val settings =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "enableExternalTemplates": true,
            "sql": {
              "outputs": [
                { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
              ]
            }
          }
        }
      """)
        .toEither
        .getOrElse(fail("expected valid settings"))
        .sql
        .getOrElse(fail("expected SQL settings"))
    assertEquals(settings.languageTargets("python").enableExternalTemplates, true)
  }

  test("rejects additional deck output id that collides with a bundled id") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "sql": {
              "additionalTemplatesDirectory": "classpath:custom-templates/python/src/db",
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

  test("rejects additional deck with unknown bundled override id") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "sql": {
              "additionalTemplatesDirectory": "classpath:additional-templates/python/src/db-unknown-override",
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

  test("rejects additionalTemplatesDirectory missing outputs.json") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "sql": {
              "additionalTemplatesDirectory": "classpath:missing-additional-deck",
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

  test("rejects duplicate ids in additional deck outputs.json") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "sql": {
              "additionalTemplatesDirectory": "classpath:additional-templates/python/src/db-duplicate-id",
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
    assert(errors.exists(_.message.contains("duplicate output id 'custom.sql.dup'")))
  }

  test("rejects additional deck referencing missing classpath template without enableExternalTemplates") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "sql": {
              "additionalTemplatesDirectory": "classpath:additional-templates/python/src/db-missing-template",
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

  test("rejects filesystem additionalTemplatesDirectory without enableExternalTemplates") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "sql": {
              "additionalTemplatesDirectory": "templates/python/tests/sql-additional-templates-external/additional-templates",
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

  test("accepts filesystem additionalTemplatesDirectory when enableExternalTemplates is true") {
    SmithplatesSettings
      .parseJson("""
        {
          "python": {
            "enableExternalTemplates": true,
            "sql": {
              "additionalTemplatesDirectory": "templates/python/tests/sql-additional-templates-external/additional-templates",
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

  test("accepts additional deck that overrides a bundled output by id") {
    SmithplatesSettings
      .parseJson("""
        {
          "python": {
            "sql": {
              "additionalTemplatesDirectory": "classpath:additional-templates/python/src/db-override",
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

  test("rejects additional SQL operation binding with kind filter") {
    val errors =
      SmithplatesSettings
        .parseJson("""
        {
          "python": {
            "sql": {
              "additionalTemplatesDirectory": "classpath:additional-templates/python/src/db-bad-binding",
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
}

class SmithplatesSettingsSpec extends munit.FunSuite {
  test("requires at least one of sql or http") {
    val errors =
      SmithplatesSettings.parseJson("{}").swap.toOption.getOrElse(fail("expected errors"))
    assert(errors.exists(error => error.message.contains("sql") && error.message.contains("http")))
  }
}
