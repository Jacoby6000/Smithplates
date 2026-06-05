package com.jacoby6000.smithy.stache.sql.postgres

import com.jacoby6000.smithy.stache.sql.{NoSqlTables, SqlSchema, SqlSchemaExampleFixtures}
import munit.FunSuite

final class PostgresRendererSpec extends FunSuite {
  test("Postgres - renders example schema DDL") {
    val schema = SqlSchemaExampleFixtures.exampleSchema
    val postgres = PostgresRenderer.render(schema)

    val expectedPostgresDdl =
      """-- stache.codegen.sql.example#Bar
        |CREATE TABLE bars (
        |    id TEXT NOT NULL,
        |    name TEXT,
        |
        |    PRIMARY KEY (id)
        |);
        |
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
        |CREATE INDEX idx_foos_created_at ON foos (created_at);""".stripMargin

    assertEquals(postgres, expectedPostgresDdl)
  }

  test("Postgres - fails to render when schema has no tables") {
    val thrown = intercept[IllegalStateException] {
      PostgresRenderer.render(SqlSchema(tables = Nil))
    }
    assertEquals(thrown.getMessage, NoSqlTables.message)
  }
}
