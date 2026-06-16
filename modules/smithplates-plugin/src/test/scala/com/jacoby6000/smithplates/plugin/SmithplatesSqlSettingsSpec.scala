package com.jacoby6000.smithplates.plugin

import com.jacoby6000.smithplates.sql.service.renderer.SqlServiceCodegenDbArtifacts
import com.jacoby6000.smithplates.sql.service.renderer.SqlServiceCodegenSettings
import software.amazon.smithy.model.node.Node

class SmithplatesSqlSettingsSpec extends munit.FunSuite {
  test("parses enabled dialect and language-first SQL settings") {
    val node =
      Node
        .parse("""
        {
          "python": {
            "sql": {
              "postgres": {
                "enable": true,
                "migrationLocation": "db/migrations/postgres"
              },
              "sourceOutputDir": "src/generated",
              "testOutputDir": "tests"
            }
          }
        }
      """)
        .expectObjectNode()

    val settings =
      SmithplatesSettings.fromNode(node).toEither.getOrElse(fail("expected valid settings")).sql.getOrElse {
        fail("expected SQL settings")
      }
    assertEquals(settings.enabledDialectKeys, List("postgres"))
    assertEquals(
      settings.languageTargets.get("python").flatMap(_.dialects.get("postgres")).map(_.migrationLocation),
      Some(Some("db/migrations/postgres"))
    )
    assertEquals(settings.languageTargets.keySet, Set("python"))

    assertEquals(settings.dialectMigrationDirectories, Map("postgres" -> "db/migrations/postgres"))

    val codegenSettings = settings.toCodegenSettings("python").getOrElse(fail("expected codegen settings"))
    assertEquals(codegenSettings.templateDirectory, "classpath:python/src/db")
    assertEquals(codegenSettings.outputPrefix, "db")
    assertEquals(codegenSettings.packageName, "generated.db")
    assertEquals(codegenSettings.sourceOutputDirectory, Some("src/generated"))
    assertEquals(codegenSettings.testOutputDirectory, Some("tests"))
    assertEquals(codegenSettings.artifacts, SqlServiceCodegenDbArtifacts.forEnabledDialects(List("postgres")))
  }

  test("dialect enable defaults to false") {
    val node =
      Node
        .parse("""
        {
          "python": {
            "sql": {
              "sqlite": {
                "migrationLocation": "db/migrations/sqlite"
              },
              "sourceOutputDir": "src/generated",
              "testOutputDir": "tests"
            }
          }
        }
      """)
        .expectObjectNode()

    val settings =
      SmithplatesSettings.fromNode(node).toEither.getOrElse(fail("expected valid settings")).sql.getOrElse {
        fail("expected SQL settings")
      }
    assertEquals(settings.enabledDialectKeys, Nil)
    assertEquals(settings.dialectMigrationDirectories, Map.empty)
  }

  test("rejects migrationLocation ending in .sql when dialect is enabled") {
    val node =
      Node
        .parse("""
        {
          "python": {
            "sql": {
              "sqlite": {
                "enable": true,
                "migrationLocation": "db/sqlite.sql"
              },
              "sourceOutputDir": "src/generated",
              "testOutputDir": "tests"
            }
          }
        }
      """)
        .expectObjectNode()

    val errors = SmithplatesSettings.fromNode(node).swap.toOption.getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("directory path")))
  }

  test("requires migrationLocation when dialect is enabled") {
    val node =
      Node
        .parse("""
        {
          "python": {
            "sql": {
              "sqlite": {
                "enable": true
              },
              "sourceOutputDir": "src/generated",
              "testOutputDir": "tests"
            }
          }
        }
      """)
        .expectObjectNode()

    val errors = SmithplatesSettings.fromNode(node).swap.toOption.getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("migrationLocation")))
  }

  test("accepts explicit templateDirectory when required templates exist") {
    val node =
      Node
        .parse("""
        {
          "python": {
            "sql": {
              "templateDirectory": "classpath:custom-templates/python/src/db",
              "sourceOutputDir": "out/src",
              "testOutputDir": "out/test"
            }
          }
        }
      """)
        .expectObjectNode()

    val settings        =
      SmithplatesSettings.fromNode(node).toEither.getOrElse(fail("expected valid settings")).sql.getOrElse {
        fail("expected SQL settings")
      }
    val codegenSettings = settings.toCodegenSettings("python").getOrElse(fail("expected codegen settings"))
    assertEquals(codegenSettings.templateDirectory, "classpath:custom-templates/python/src/db")
    assertEquals(codegenSettings.defaultDialectKey, SqlServiceCodegenSettings.SharedDialectKey)
    assertEquals(codegenSettings.enabledDialectKeys, Nil)
    assertEquals(codegenSettings.queryRenderers, Map.empty)
    assertEquals(codegenSettings.schemaDdlRenderers, Map.empty)
    assertEquals(codegenSettings.artifacts, SqlServiceCodegenDbArtifacts.shared)
  }

  test("combines bundled artifacts for multiple enabled dialects") {
    val node =
      Node
        .parse("""
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
              "sourceOutputDir": "src",
              "testOutputDir": "tests"
            }
          }
        }
      """)
        .expectObjectNode()

    val settings        =
      SmithplatesSettings.fromNode(node).toEither.getOrElse(fail("expected valid settings")).sql.getOrElse {
        fail("expected SQL settings")
      }
    assertEquals(settings.enabledDialectKeys, List("sqlite", "postgres"))
    val codegenSettings = settings.toCodegenSettings("python").getOrElse(fail("expected codegen settings"))
    assertEquals(
      codegenSettings.artifacts,
      SqlServiceCodegenDbArtifacts.forEnabledDialects(List("sqlite", "postgres"))
    )
  }

  test("rejects old feature-first SQL shape") {
    val node =
      Node
        .parse("""
        {
          "sql": {
            "languageTargets": {
              "python": {
                "sourceOutputDir": "src",
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

  test("rejects unbundled language without templateDirectory") {
    val node =
      Node
        .parse("""
        {
          "kotlin": {
            "sql": {
              "sourceOutputDir": "src",
              "testOutputDir": "tests"
            }
          }
        }
      """)
        .expectObjectNode()

    val errors = SmithplatesSettings.fromNode(node).swap.toOption.getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("templateDirectory")))
    assert(errors.exists(_.message.contains("kotlin")))
  }

  test("rejects explicit templateDirectory missing required templates") {
    val node =
      Node
        .parse("""
        {
          "kotlin": {
            "sql": {
              "templateDirectory": "classpath:missing-templates/kotlin",
              "sourceOutputDir": "src",
              "testOutputDir": "tests"
            }
          }
        }
      """)
        .expectObjectNode()

    val errors = SmithplatesSettings.fromNode(node).swap.toOption.getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("missing required templates")))
  }

  test("rejects explicit templateDirectory missing dialect-specific templates") {
    val node =
      Node
        .parse("""
        {
          "python": {
            "sql": {
              "postgres": {
                "enable": true,
                "migrationLocation": "db/migrations/postgres"
              },
              "templateDirectory": "classpath:custom-templates/python/src/db",
              "sourceOutputDir": "src",
              "testOutputDir": "tests"
            }
          }
        }
      """)
        .expectObjectNode()

    val errors = SmithplatesSettings.fromNode(node).swap.toOption.getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("postgres/service_psycopg.ssp")))
  }

  test("rejects conflicting migration locations for the same dialect across languages") {
    val node =
      Node
        .parse("""
        {
          "python": {
            "sql": {
              "sqlite": {
                "enable": true,
                "migrationLocation": "db/migrations/sqlite"
              },
              "sourceOutputDir": "src/python",
              "testOutputDir": "tests/python"
            }
          },
          "kotlin": {
            "sql": {
              "sqlite": {
                "enable": true,
                "migrationLocation": "db/migrations/kotlin-sqlite"
              },
              "templateDirectory": "classpath:custom-templates/kotlin/src/db",
              "sourceOutputDir": "src/kotlin",
              "testOutputDir": "tests/kotlin"
            }
          }
        }
      """)
        .expectObjectNode()

    val errors = SmithplatesSettings.fromNode(node).swap.toOption.getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("conflicting sqlite migrationLocation")))
  }
}

class SmithplatesSettingsSpec extends munit.FunSuite {
  test("requires at least one of sql or http") {
    val node   = Node.parse("{}").expectObjectNode()
    val errors = SmithplatesSettings.fromNode(node).swap.toOption.getOrElse(fail("expected errors"))
    assert(errors.exists(error => error.message.contains("sql") && error.message.contains("http")))
  }
}
