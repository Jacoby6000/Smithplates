package com.jacoby6000.smithy.stache.sql.sqlite

import com.jacoby6000.smithy.stache.sql.*
import com.jacoby6000.smithy.stache.sql.shared.SqlShared
import munit.FunSuite

final class SqliteRendererSpec extends FunSuite {
  test("Sqlite - renders example schema DDL") {
    val schema = SqlSchemaExampleFixtures.exampleSchema
    val sqlite = SqlShared.formatDdlStatements(SqliteRenderer.renderSchemaDdlStatements(schema))

    val expectedSqliteDdl =
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
        |    name TEXT CHECK(length(name) <= 128),
        |    size_bytes BIGINT,
        |    payload TEXT,
        |    created_at TEXT,
        |
        |    PRIMARY KEY (id),
        |    FOREIGN KEY (bar_id) REFERENCES bars (id)
        |);
        |
        |-- stache.codegen.sql.example#Foo
        |CREATE INDEX idx_foos_created_at ON foos (created_at);""".stripMargin

    assertEquals(sqlite, expectedSqliteDdl)
  }
}
