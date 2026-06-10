package com.jacoby6000.smithplates.sql.ddl.renderer.sqlite

import com.jacoby6000.smithplates.sql.*
import com.jacoby6000.smithplates.sql.model.*
import com.jacoby6000.smithplates.sql.shared.SqlShared
import munit.FunSuite
import software.amazon.smithy.model.shapes.ShapeId

final class SqliteDDLRendererSpec extends FunSuite {
  test("Enum Type - renders CHECK constraints for string enum columns") {
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

    val schema = SqlIrExtractor.extractOrThrow(model)
    val ddl    = SqlShared.formatDdlStatements(SqliteRenderer.renderSchemaDdlStatements(schema))

    assert(ddl.contains("direction TEXT CHECK(direction IN ('NORTH', 'SOUTH'))"))
  }

  test("Create Table - renders example schema DDL") {
    val schema = SqlSchemaExampleFixtures.exampleSchema
    val sqlite = SqlShared.formatDdlStatements(SqliteRenderer.renderSchemaDdlStatements(schema))

    val expectedSqliteDdl =
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
        |    name TEXT CHECK(length(name) <= 128),
        |    size_bytes BIGINT,
        |    payload TEXT,
        |    created_at TEXT,
        |
        |    PRIMARY KEY (id),
        |    FOREIGN KEY (bar_id) REFERENCES bars (id)
        |);
        |
        |-- smithplates.codegen.sql.example#Foo
        |CREATE INDEX idx_foos_created_at ON foos (created_at);""".stripMargin

    assertEquals(sqlite, expectedSqliteDdl)
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

    val ddl = SqlShared.formatDdlStatements(SqliteRenderer.renderSchemaDdlStatements(schema))
    assert(ddl.indexOf("CREATE TABLE aaa_parent") < ddl.indexOf("CREATE TABLE zzz_child"))
  }

  test("Create Table - fails to render when schema has no tables") {
    val thrown = intercept[IllegalStateException] {
      SqlShared.formatDdlStatements(SqliteRenderer.renderSchemaDdlStatements(SqlSchema(tables = Nil)))
    }
    assertEquals(thrown.getMessage, NoSqlTables.message)
  }
}
