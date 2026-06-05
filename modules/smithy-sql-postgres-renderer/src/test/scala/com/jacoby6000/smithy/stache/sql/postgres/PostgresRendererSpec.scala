package com.jacoby6000.smithy.stache.sql.postgres

import com.jacoby6000.smithy.stache.sql.*
import com.jacoby6000.smithy.stache.sql.shared.SqlShared
import munit.FunSuite
import software.amazon.smithy.model.shapes.ShapeId

final class PostgresRendererSpec extends FunSuite {
  test("Postgres - renders example schema DDL") {
    val schema   = SqlSchemaExampleFixtures.exampleSchema
    val postgres = SqlShared.formatDdlStatements(PostgresRenderer.renderSchemaDdlStatements(schema))

    val expectedPostgresDdl =
      """-- stache.codegen.sql.example#Bar
        |CREATE TABLE bars (
        |    id TEXT NOT NULL,
        |    name TEXT,
        |
        |    PRIMARY KEY (id)
        |);
        |
        |-- stache.codegen.sql.example#Foo
        |CREATE TABLE foos (
        |    id TEXT NOT NULL,
        |    bar_id TEXT,
        |    name VARCHAR(128),
        |    size_bytes BIGINT,
        |    payload JSONB,
        |    created_at TIMESTAMP,
        |
        |    PRIMARY KEY (id),
        |    FOREIGN KEY (bar_id) REFERENCES bars (id)
        |);
        |
        |-- stache.codegen.sql.example#Foo
        |CREATE INDEX idx_foos_created_at ON foos (created_at);""".stripMargin

    assertEquals(postgres, expectedPostgresDdl)
  }

  test("Postgres - emits CREATE TABLE statements in dependency order") {
    val childShape  = ShapeId.from("example#Child")
    val parentShape = ShapeId.from("example#Parent")

    val schema = SqlSchema(
      tables = List(
        SqlTable(
          name = "zzz_child",
          shapeId = childShape,
          columns = List(SqlColumn("id", SqlColumnType.Text, nullable = false)),
          primaryKeys = List("id"),
          foreignKeys = List(
            SqlForeignKey(column = "parent_id", referencesShape = parentShape, referencesColumn = "id")
          ),
          indexes = Nil
        ),
        SqlTable(
          name = "aaa_parent",
          shapeId = parentShape,
          columns = List(SqlColumn("id", SqlColumnType.Text, nullable = false)),
          primaryKeys = List("id"),
          foreignKeys = Nil,
          indexes = Nil
        )
      )
    )

    val ddl = SqlShared.formatDdlStatements(PostgresRenderer.renderSchemaDdlStatements(schema))
    assert(ddl.indexOf("CREATE TABLE aaa_parent") < ddl.indexOf("CREATE TABLE zzz_child"))
  }

  test("Postgres - fails to render when schema has no tables") {
    val thrown = intercept[IllegalStateException] {
      SqlShared.formatDdlStatements(PostgresRenderer.renderSchemaDdlStatements(SqlSchema(tables = Nil)))
    }
    assertEquals(thrown.getMessage, NoSqlTables.message)
  }
}
