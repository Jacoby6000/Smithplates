package com.jacoby6000.smithy.sql.sqlite

import com.jacoby6000.smithy.sql.SqlSchemaExampleFixtures
import munit.FunSuite

final class SqliteRendererSpec extends FunSuite {
  test("Sqlite - renders example schema DDL") {
    val schema = SqlSchemaExampleFixtures.exampleSchema
    val sqlite = SqliteRenderer.render(schema)

    val expectedSqliteDdl =
      """-- jacoby6000.codegen.sql.example#Bar
        |CREATE TABLE bars (
        |    id TEXT NOT NULL,
        |    name TEXT,
        |
        |    PRIMARY KEY (id)
        |);
        |
        |
        |-- jacoby6000.codegen.sql.example#Foo
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
        |CREATE INDEX idx_foos_created_at ON foos (created_at);""".stripMargin

    assertEquals(sqlite, expectedSqliteDdl)
  }
}
