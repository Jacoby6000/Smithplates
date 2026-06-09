package com.jacoby6000.smithplates.sql.ddl.postgres

import com.jacoby6000.smithplates.sql.*
import com.jacoby6000.smithplates.sql.model.*
import com.jacoby6000.smithplates.sql.shared.SqlShared
import munit.FunSuite
import software.amazon.smithy.model.shapes.ShapeId

final class PostgresDDLRendererSpec extends FunSuite {
  test("Enum Type - renders CREATE TYPE for string enums before tables") {
    val model = SqlTestModelBuilder.assemble(
      """use smithplates.codegen.sql#sqlPrimaryKey
        |use smithplates.codegen.sql#sqlTable
        |
        |enum Direction {
        |    NORTH
        |    SOUTH
        |}
        |
        |@sqlTable(name: "routes")
        |structure Route {
        |    @sqlPrimaryKey
        |    id: String
        |    direction: Direction
        |}""".stripMargin
    )

    val schema     = SqlIrExtractor.extractOrThrow(model)
    val statements = PostgresRenderer.renderSchemaDdlStatements(schema)
    val enumDdl    =
      statements
        .find(_.shapeId == ShapeId.from("example#Direction"))
        .map(_.statement)
        .getOrElse(fail("CREATE TYPE for example#Direction was not rendered"))

    assertEquals(enumDdl, "CREATE TYPE example_direction AS ENUM ('NORTH', 'SOUTH');")

    val routeDdl =
      statements
        .find(_.shapeId == ShapeId.from("example#Route"))
        .map(_.statement)
        .getOrElse(fail("CREATE TABLE for example#Route was not rendered"))

    assert(routeDdl.contains("direction example_direction"))
  }

  test("Create Table - renders example schema DDL") {
    val schema   = SqlSchemaExampleFixtures.exampleSchema
    val postgres = SqlShared.formatDdlStatements(PostgresRenderer.renderSchemaDdlStatements(schema))

    val expectedPostgresDdl =
      """-- smithplates.codegen.sql.example#Bar
        |CREATE TABLE bars (
        |    id TEXT NOT NULL,
        |    name TEXT,
        |
        |    PRIMARY KEY (id)
        |);
        |
        |-- smithplates.codegen.sql.example#Foo
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
        |-- smithplates.codegen.sql.example#Foo
        |CREATE INDEX idx_foos_created_at ON foos (created_at);""".stripMargin

    assertEquals(postgres, expectedPostgresDdl)
  }

  test("Create Table - emits CREATE TABLE statements in dependency order") {
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

  test("Create Table - fails to render when schema has no tables") {
    val thrown = intercept[IllegalStateException] {
      SqlShared.formatDdlStatements(PostgresRenderer.renderSchemaDdlStatements(SqlSchema(tables = Nil)))
    }
    assertEquals(thrown.getMessage, NoSqlTables.message)
  }
}
