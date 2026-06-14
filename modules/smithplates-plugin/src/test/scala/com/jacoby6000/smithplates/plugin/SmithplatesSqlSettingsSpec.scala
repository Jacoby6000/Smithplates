package com.jacoby6000.smithplates.plugin

import com.jacoby6000.smithplates.sql.service.renderer.PythonTemplateNamespaces
import com.jacoby6000.smithplates.sql.service.renderer.SqlServiceCodegenDbArtifacts
import software.amazon.smithy.model.node.Node

class SmithplatesSqlSettingsSpec extends munit.FunSuite {
  test("parses enabled dialect and languageTargets settings") {
    val node =
      Node
        .parse("""
        {
          "postgres": {
            "enable": true,
            "migrationLocation": "db/migrations/postgres"
          },
          "languageTargets": {
            "python": {
              "sourceOutputDir": "src/generated",
              "testOutputDir": "tests"
            }
          }
        }
      """)
        .expectObjectNode()

    val settings = SmithplatesSqlSettings.fromNode(node).toEither.getOrElse(fail("expected valid settings"))
    assertEquals(settings.enabledDialectKeys, List("postgres"))
    assertEquals(
      settings.dialects.get("postgres").map(_.migrationLocation),
      Some(Some("db/migrations/postgres"))
    )
    assertEquals(settings.languageTargets.keySet, Set("python"))

    assertEquals(settings.dialectMigrationDirectories, Map("postgres" -> "db/migrations/postgres"))

    val codegenSettings = settings.toCodegenSettings("python").getOrElse(fail("expected codegen settings"))
    assertEquals(codegenSettings.templateDirectory, PythonTemplateNamespaces.bundledDbTemplateDirectory)
    assertEquals(codegenSettings.sourceOutputDirectory, Some("src/generated"))
    assertEquals(codegenSettings.testOutputDirectory, Some("tests"))
    assertEquals(codegenSettings.artifacts, SqlServiceCodegenDbArtifacts.forEnabledDialects(List("postgres")))
  }

  test("dialect enable defaults to false") {
    val node =
      Node
        .parse("""
        {
          "sqlite": {
            "migrationLocation": "db/migrations/sqlite"
          }
        }
      """)
        .expectObjectNode()

    val settings = SmithplatesSqlSettings.fromNode(node).toEither.getOrElse(fail("expected valid settings"))
    assertEquals(settings.enabledDialectKeys, Nil)
    assertEquals(settings.dialectMigrationDirectories, Map.empty)
  }

  test("rejects migrationLocation ending in .sql when dialect is enabled") {
    val node =
      Node
        .parse("""
        {
          "sqlite": {
            "enable": true,
            "migrationLocation": "db/sqlite.sql"
          }
        }
      """)
        .expectObjectNode()

    val errors = SmithplatesSqlSettings.fromNode(node).swap.toOption.getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("directory path")))
  }

  test("requires migrationLocation when dialect is enabled") {
    val node =
      Node
        .parse("""
        {
          "sqlite": {
            "enable": true
          }
        }
      """)
        .expectObjectNode()

    val errors = SmithplatesSqlSettings.fromNode(node).swap.toOption.getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("migrationLocation")))
  }

  test("accepts explicit templateDirectory when required templates exist") {
    val node =
      Node
        .parse("""
        {
          "languageTargets": {
            "python": {
              "templateDirectory": "classpath:custom-templates/python/src/db",
              "sourceOutputDir": "out/src",
              "testOutputDir": "out/test"
            }
          }
        }
      """)
        .expectObjectNode()

    val settings        = SmithplatesSqlSettings.fromNode(node).toEither.getOrElse(fail("expected valid settings"))
    val codegenSettings = settings.toCodegenSettings("python").getOrElse(fail("expected codegen settings"))
    assertEquals(codegenSettings.templateDirectory, "classpath:custom-templates/python/src/db")
    assertEquals(codegenSettings.artifacts, SqlServiceCodegenDbArtifacts.shared)
  }

  test("combines bundled artifacts for multiple enabled dialects") {
    val node =
      Node
        .parse("""
        {
          "sqlite": {
            "enable": true,
            "migrationLocation": "db/migrations/sqlite"
          },
          "postgres": {
            "enable": true,
            "migrationLocation": "db/migrations/postgres"
          },
          "languageTargets": {
            "python": {
              "sourceOutputDir": "src",
              "testOutputDir": "tests"
            }
          }
        }
      """)
        .expectObjectNode()

    val settings        = SmithplatesSqlSettings.fromNode(node).toEither.getOrElse(fail("expected valid settings"))
    assertEquals(settings.enabledDialectKeys, List("sqlite", "postgres"))
    val codegenSettings = settings.toCodegenSettings("python").getOrElse(fail("expected codegen settings"))
    assertEquals(
      codegenSettings.artifacts,
      SqlServiceCodegenDbArtifacts.forEnabledDialects(List("sqlite", "postgres"))
    )
  }

  test("rejects language keys at sql root") {
    val node =
      Node
        .parse("""
        {
          "python": {
            "sourceOutputDir": "src",
            "testOutputDir": "tests"
          }
        }
      """)
        .expectObjectNode()

    val errors = SmithplatesSqlSettings.fromNode(node).swap.toOption.getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("languageTargets")))
  }

  test("rejects unbundled language without templateDirectory") {
    val node =
      Node
        .parse("""
        {
          "languageTargets": {
            "kotlin": {
              "sourceOutputDir": "src",
              "testOutputDir": "tests"
            }
          }
        }
      """)
        .expectObjectNode()

    val errors = SmithplatesSqlSettings.fromNode(node).swap.toOption.getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("templateDirectory")))
    assert(errors.exists(_.message.contains("kotlin")))
  }

  test("rejects explicit templateDirectory missing required templates") {
    val node =
      Node
        .parse("""
        {
          "languageTargets": {
            "kotlin": {
              "templateDirectory": "classpath:missing-templates/kotlin",
              "sourceOutputDir": "src",
              "testOutputDir": "tests"
            }
          }
        }
      """)
        .expectObjectNode()

    val errors = SmithplatesSqlSettings.fromNode(node).swap.toOption.getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("missing required templates")))
  }

  test("rejects explicit templateDirectory missing dialect-specific templates") {
    val node =
      Node
        .parse("""
        {
          "postgres": {
            "enable": true,
            "migrationLocation": "db/migrations/postgres"
          },
          "languageTargets": {
            "python": {
              "templateDirectory": "classpath:custom-templates/python/src/db",
              "sourceOutputDir": "src",
              "testOutputDir": "tests"
            }
          }
        }
      """)
        .expectObjectNode()

    val errors = SmithplatesSqlSettings.fromNode(node).swap.toOption.getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("postgres/service_psycopg.ssp")))
  }
}

class SmithplatesSettingsSpec extends munit.FunSuite {
  test("requires at least one of sql or http") {
    val node   = Node.parse("{}").expectObjectNode()
    val errors = SmithplatesSettings.fromNode(node).swap.toOption.getOrElse(fail("expected errors"))
    assert(errors.exists(error => error.message.contains("sql") && error.message.contains("http")))
  }
}
