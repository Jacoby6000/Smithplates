package com.jacoby6000.smithy.stache

import com.jacoby6000.smithy.stache.sql.PostgresDialect
import com.jacoby6000.smithy.stache.sql.SqliteDialect
import com.jacoby6000.smithy.stache.sql.codegen.SqlServiceCodegenDbArtifacts
import software.amazon.smithy.model.node.Node

class SmithyStacheSqlSettingsSpec extends munit.FunSuite {
  test("parses enabled dialect and languageTargets settings") {
    val node =
      Node
        .parse("""
        {
          "postgres": {
            "enable": true,
            "migrationLocation": "db/postgres.sql"
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

    val settings = SmithyStacheSqlSettings.fromNode(node).toEither.getOrElse(fail("expected valid settings"))
    assertEquals(settings.enabledDialects, List(PostgresDialect))
    assertEquals(
      settings.dialects.get(PostgresDialect).map(_.migrationLocation),
      Some(Some("db/postgres.sql"))
    )
    assertEquals(settings.languageTargets.keySet, Set("python"))

    assertEquals(settings.schemaDialectOutputs, Map(PostgresDialect -> "db/postgres.sql"))

    val codegenSettings = settings.toCodegenSettings("python").getOrElse(fail("expected codegen settings"))
    assertEquals(codegenSettings.templateDirectory, "classpath:sql-service-codegen/python")
    assertEquals(codegenSettings.sourceOutputDirectory, Some("src/generated"))
    assertEquals(codegenSettings.testOutputDirectory, Some("tests"))
    assertEquals(codegenSettings.artifacts, SqlServiceCodegenDbArtifacts.forEnabledDialects(List(PostgresDialect)))
  }

  test("dialect enable defaults to false") {
    val node =
      Node
        .parse("""
        {
          "sqlite": {
            "migrationLocation": "db/sqlite.sql"
          }
        }
      """)
        .expectObjectNode()

    val settings = SmithyStacheSqlSettings.fromNode(node).toEither.getOrElse(fail("expected valid settings"))
    assertEquals(settings.enabledDialects, Nil)
    assertEquals(settings.schemaDialectOutputs, Map.empty)
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

    val errors = SmithyStacheSqlSettings.fromNode(node).swap.toOption.getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("migrationLocation")))
  }

  test("accepts explicit templateDirectory when required templates exist") {
    val node =
      Node
        .parse("""
        {
          "languageTargets": {
            "python": {
              "templateDirectory": "classpath:custom-templates/python",
              "sourceOutputDir": "out/src",
              "testOutputDir": "out/test"
            }
          }
        }
      """)
        .expectObjectNode()

    val settings        = SmithyStacheSqlSettings.fromNode(node).toEither.getOrElse(fail("expected valid settings"))
    val codegenSettings = settings.toCodegenSettings("python").getOrElse(fail("expected codegen settings"))
    assertEquals(codegenSettings.templateDirectory, "classpath:custom-templates/python")
    assertEquals(codegenSettings.artifacts, SqlServiceCodegenDbArtifacts.shared)
  }

  test("combines bundled artifacts for multiple enabled dialects") {
    val node =
      Node
        .parse("""
        {
          "sqlite": {
            "enable": true,
            "migrationLocation": "db/sqlite.sql"
          },
          "postgres": {
            "enable": true,
            "migrationLocation": "db/postgres.sql"
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

    val settings        = SmithyStacheSqlSettings.fromNode(node).toEither.getOrElse(fail("expected valid settings"))
    assertEquals(settings.enabledDialects, List(SqliteDialect, PostgresDialect))
    val codegenSettings = settings.toCodegenSettings("python").getOrElse(fail("expected codegen settings"))
    assertEquals(
      codegenSettings.artifacts,
      SqlServiceCodegenDbArtifacts.forEnabledDialects(List(SqliteDialect, PostgresDialect))
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

    val errors = SmithyStacheSqlSettings.fromNode(node).swap.toOption.getOrElse(fail("expected errors"))
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

    val errors = SmithyStacheSqlSettings.fromNode(node).swap.toOption.getOrElse(fail("expected errors"))
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

    val errors = SmithyStacheSqlSettings.fromNode(node).swap.toOption.getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("missing required templates")))
  }

  test("rejects explicit templateDirectory missing dialect-specific templates") {
    val node =
      Node
        .parse("""
        {
          "postgres": {
            "enable": true,
            "migrationLocation": "db/postgres.sql"
          },
          "languageTargets": {
            "python": {
              "templateDirectory": "classpath:custom-templates/python",
              "sourceOutputDir": "src",
              "testOutputDir": "tests"
            }
          }
        }
      """)
        .expectObjectNode()

    val errors = SmithyStacheSqlSettings.fromNode(node).swap.toOption.getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("db/postgres/service_psycopg.mustache")))
  }
}

class SmithyStacheSettingsSpec extends munit.FunSuite {
  test("requires sql object") {
    val node   = Node.parse("{}").expectObjectNode()
    val errors = SmithyStacheSettings.fromNode(node).swap.toOption.getOrElse(fail("expected errors"))
    assert(errors.exists(_.message.contains("sql")))
  }
}
