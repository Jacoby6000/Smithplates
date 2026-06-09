package com.jacoby6000.smithplates.sql.ddl.sqlite

import com.dimafeng.testcontainers.GenericContainer
import com.dimafeng.testcontainers.munit.TestContainerForEach
import com.jacoby6000.smithplates.testkit.SqlDdlSupport
import com.jacoby6000.smithplates.testkit.SqlIntegrationSchemas
import munit.FunSuite

final class SqliteSqlSchemaIntegrationSpec extends FunSuite with TestContainerForEach {
  override val containerDef: GenericContainer.Def[GenericContainer] =
    SqliteContainerSupport.containerDef

  test("simple schema applies and enforces foreign keys") {
    withContainers { sqlite =>
      SqliteContainerSupport.applyDdl(
        sqlite,
        SqlDdlSupport.renderSchemaDdlStatements(SqliteRenderer, SqlIntegrationSchemas.simpleSchema)
      )

      assertEquals(SqliteContainerSupport.listTables(sqlite), SqlIntegrationSchemas.simpleTableNames)

      assertEquals(
        SqliteContainerSupport.execSql(sqlite, "INSERT INTO categories (id, name) VALUES ('c1', 'books');"),
        0
      )
      assertEquals(
        SqliteContainerSupport.execSql(
          sqlite,
          "INSERT INTO items (id, category_id, name) VALUES ('i1', 'c1', 'widget');"
        ),
        0
      )
      assertNotEquals(
        SqliteContainerSupport.execSql(
          sqlite,
          "INSERT INTO items (id, category_id, name) VALUES ('i2', 'missing', 'orphan');"
        ),
        0
      )
    }
  }

  test("complex schema creates all tables and foreign keys") {
    withContainers { sqlite =>
      SqliteContainerSupport.applyDdl(
        sqlite,
        SqlDdlSupport.renderSchemaDdlStatements(SqliteRenderer, SqlIntegrationSchemas.complexSchema)
      )

      assertEquals(SqliteContainerSupport.listTables(sqlite), SqlIntegrationSchemas.complexTableNames)

      assertEquals(
        SqliteContainerSupport.execSql(sqlite, "INSERT INTO regions (id, name) VALUES ('r1', 'west');"),
        0
      )
      assertEquals(
        SqliteContainerSupport.execSql(
          sqlite,
          "INSERT INTO offices (id, region_id, name) VALUES ('o1', 'r1', 'hq');"
        ),
        0
      )
      assertEquals(
        SqliteContainerSupport.execSql(
          sqlite,
          "INSERT INTO teams (id, office_id, name) VALUES ('t1', 'o1', 'platform');"
        ),
        0
      )
      assertEquals(
        SqliteContainerSupport.execSql(
          sqlite,
          "INSERT INTO people (id, team_id, home_office_id, name) VALUES ('p1', 't1', 'o1', 'ada');"
        ),
        0
      )
      assertEquals(
        SqliteContainerSupport.execSql(
          sqlite,
          "INSERT INTO projects (id, owning_team_id, title) VALUES ('pr1', 't1', 'render');"
        ),
        0
      )
      assertNotEquals(
        SqliteContainerSupport.execSql(
          sqlite,
          "INSERT INTO memberships (id, person_id, project_id, team_id, role) VALUES ('m-bad', 'missing', 'pr1', 't1', 'dev');"
        ),
        0
      )
    }
  }

  test("varchar CHECK rejects values longer than maxLength") {
    withContainers { sqlite =>
      SqliteContainerSupport.applyDdl(
        sqlite,
        SqlDdlSupport.renderSchemaDdlStatements(SqliteRenderer, SqlIntegrationSchemas.varcharCheckSchema)
      )

      assertEquals(SqliteContainerSupport.execSql(sqlite, "INSERT INTO categories (id) VALUES ('c1');"), 0)
      assertEquals(
        SqliteContainerSupport.execSql(
          sqlite,
          "INSERT INTO items (id, category_id, name) VALUES ('i1', 'c1', '12345');"
        ),
        0
      )
      assertNotEquals(
        SqliteContainerSupport.execSql(
          sqlite,
          "INSERT INTO items (id, category_id, name) VALUES ('i2', 'c1', '123456');"
        ),
        0
      )
    }
  }
}
