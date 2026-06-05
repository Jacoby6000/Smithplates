package com.jacoby6000.smithy.stache.sql.postgres

import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.munit.TestContainerForEach
import com.jacoby6000.smithy.stache.testkit.SqlDdlSupport
import com.jacoby6000.smithy.stache.testkit.SqlIntegrationSchemas
import munit.FunSuite
import org.testcontainers.utility.DockerImageName

import java.sql.Connection
import java.sql.DriverManager

final class PostgresSqlSchemaIntegrationSpec extends FunSuite with TestContainerForEach {
  override val containerDef: PostgreSQLContainer.Def =
    PostgreSQLContainer.Def(
      dockerImageName = DockerImageName.parse("postgres:16-alpine"),
      databaseName = "sql_plugin_it",
      username = "sql_plugin",
      password = "sql_plugin"
    )

  test("simple schema applies and enforces foreign keys") {
    withContainers { postgres =>
      withConnection(postgres) { connection =>
        SqlDdlSupport.applyDdl(connection, PostgresRenderer, SqlIntegrationSchemas.simpleSchema)

        assertEquals(PostgresDdlSupport.listTables(connection), SqlIntegrationSchemas.simpleTableNames)
        assertEquals(PostgresDdlSupport.countForeignKeys(connection), SqlIntegrationSchemas.simpleForeignKeyCount)

        SqlDdlSupport.executeUpdate(
          connection,
          "INSERT INTO categories (id, name) VALUES ('c1', 'books')"
        )
        SqlDdlSupport.executeUpdate(
          connection,
          "INSERT INTO items (id, category_id, name) VALUES ('i1', 'c1', 'item')"
        )
        SqlDdlSupport.assertInsertFails(
          connection,
          "INSERT INTO items (id, category_id, name) VALUES ('i2', 'missing', 'orphan')"
        )
      }
    }
  }

  test("complex schema creates all tables and foreign keys") {
    withContainers { postgres =>
      withConnection(postgres) { connection =>
        SqlDdlSupport.applyDdl(connection, PostgresRenderer, SqlIntegrationSchemas.complexSchema)

        assertEquals(PostgresDdlSupport.listTables(connection), SqlIntegrationSchemas.complexTableNames)
        assertEquals(PostgresDdlSupport.countForeignKeys(connection), SqlIntegrationSchemas.complexForeignKeyCount)

        seedComplexGraph(connection)
        SqlDdlSupport.assertInsertFails(
          connection,
          "INSERT INTO memberships (id, person_id, project_id, team_id, role) VALUES ('m-bad', 'missing', 'p1', 't1', 'dev')"
        )
      }
    }
  }

  private def withConnection(postgres: PostgreSQLContainer)(body: Connection => Unit): Unit = {
    Class.forName(postgres.driverClassName)
    val connection = DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
    try body(connection)
    finally connection.close()
  }

  private def seedComplexGraph(connection: Connection): Unit = {
    SqlDdlSupport.executeUpdate(connection, "INSERT INTO regions (id, name) VALUES ('r1', 'west')")
    SqlDdlSupport.executeUpdate(
      connection,
      "INSERT INTO offices (id, region_id, name) VALUES ('o1', 'r1', 'hq')"
    )
    SqlDdlSupport.executeUpdate(
      connection,
      "INSERT INTO teams (id, office_id, name) VALUES ('t1', 'o1', 'platform')"
    )
    SqlDdlSupport.executeUpdate(
      connection,
      "INSERT INTO people (id, team_id, home_office_id, name) VALUES ('p1', 't1', 'o1', 'ada')"
    )
    SqlDdlSupport.executeUpdate(
      connection,
      "INSERT INTO projects (id, owning_team_id, title) VALUES ('pr1', 't1', 'render')"
    )
    SqlDdlSupport.executeUpdate(
      connection,
      "INSERT INTO memberships (id, person_id, project_id, team_id, role) VALUES ('m1', 'p1', 'pr1', 't1', 'lead')"
    )
  }
}
