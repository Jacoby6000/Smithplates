package com.jacoby6000.smithy.stache.sql.service.shared

import com.jacoby6000.smithy.stache.sql.*
import com.jacoby6000.smithy.stache.sql.service.SqlExtractionResult
import com.jacoby6000.smithy.stache.sql.service.SqlModelExtractor
import com.jacoby6000.smithy.stache.sql.service.SqlQueries
import com.jacoby6000.smithy.stache.sql.shared.SqlBindPlaceholder
import software.amazon.smithy.model.shapes.ShapeId

class SqlQueryRendererSpec extends munit.FunSuite {
  private def queryStatement(queries: SqlQueries, dialect: SqlDialect, shapeId: String): String =
    SqlQueryRenderer
      .renderQueryUnits(queries)
      .find(_.shapeId == ShapeId.from(shapeId))
      .map { query =>
        SqlBindPlaceholder.format(
          query.statement.segments,
          SqlBindPlaceholder.forDialect(dialect)
        )
      }
      .getOrElse(fail(s"query '$shapeId' was not rendered"))

  private val widgetTableUses =
    """
      |use stache.codegen.sql#sqlAutoUuid
      |use stache.codegen.sql#sqlCreatedTimestamp
      |use stache.codegen.sql#sqlPrimaryKey
      |use stache.codegen.sql#sqlTable
      |use stache.codegen.sql#sqlUpdatedTimestamp
      |""".stripMargin

  private val widgetTableStructure =
    """
      |@sqlTable(name: "widgets")
      |structure Widget {
      |    @sqlPrimaryKey
      |    @sqlAutoUuid
      |    id: String
      |    foo: String
      |    bar: Long
      |    @sqlCreatedTimestamp
      |    created_at: Timestamp
      |    @sqlUpdatedTimestamp
      |    updated_at: Timestamp
      |}
      |""".stripMargin

  private def assembleWidgetModel(uses: String, shapes: String): SqlExtractionResult =
    SqlModelExtractor.extractOrThrow(
      SqlTestModelBuilder.assemble(
        s"""$widgetTableUses
           |$uses
           |
           |$widgetTableStructure
           |$shapes
           |""".stripMargin
      )
    )

  test("Postgres - renders derive insert placeholders and RETURNING") {
    val schema = assembleWidgetModel(
      """
        |use stache.codegen.sql#DerivedStruct
        |use stache.codegen.sql#sqlDeriveInsert
        |""".stripMargin,
      """
        |@sqlDeriveInsert(targetTable: "example#Widget")
        |operation CreateWidget {
        |    input: DerivedStruct
        |    output: String
        |}
        |""".stripMargin
    )

    assertEquals(
      queryStatement(schema.queries, PostgresDialect, "example#CreateWidget"),
      "INSERT INTO widgets (foo, bar) VALUES ($1, $2) RETURNING id;"
    )
  }

  test("SQLite - renders derive insert placeholders and RETURNING") {
    val schema = assembleWidgetModel(
      """
        |use stache.codegen.sql#DerivedStruct
        |use stache.codegen.sql#sqlDeriveInsert
        |""".stripMargin,
      """
        |@sqlDeriveInsert(targetTable: "example#Widget")
        |operation CreateWidget {
        |    input: DerivedStruct
        |    output: String
        |}
        |""".stripMargin
    )

    assertEquals(
      queryStatement(schema.queries, SqliteDialect, "example#CreateWidget"),
      "INSERT INTO widgets (foo, bar) VALUES (?, ?) RETURNING id;"
    )
  }

  test("Postgres - renders derive update with primary key WHERE and updatable SET columns") {
    val schema = assembleWidgetModel(
      """
        |use stache.codegen.sql#DerivedStruct
        |use stache.codegen.sql#sqlDeriveUpdate
        |""".stripMargin,
      """
        |@sqlDeriveUpdate(targetTable: "example#Widget")
        |operation UpdateWidget {
        |    input: DerivedStruct
        |    output: Boolean
        |}
        |""".stripMargin
    )

    assertEquals(
      queryStatement(schema.queries, PostgresDialect, "example#UpdateWidget"),
      """UPDATE widgets
        |SET foo = $1, bar = $2, updated_at = CURRENT_TIMESTAMP
        |WHERE id = $3 RETURNING updated_at;""".stripMargin
    )
  }

  test("SQLite - renders derive update with primary key WHERE and updatable SET columns") {
    val schema = assembleWidgetModel(
      """
        |use stache.codegen.sql#DerivedStruct
        |use stache.codegen.sql#sqlDeriveUpdate
        |""".stripMargin,
      """
        |@sqlDeriveUpdate(targetTable: "example#Widget")
        |operation UpdateWidget {
        |    input: DerivedStruct
        |    output: Boolean
        |}
        |""".stripMargin
    )

    assertEquals(
      queryStatement(schema.queries, SqliteDialect, "example#UpdateWidget"),
      """UPDATE widgets
        |SET foo = ?, bar = ?, updated_at = CURRENT_TIMESTAMP
        |WHERE id = ? RETURNING updated_at;""".stripMargin
    )
  }

  test("Postgres - renders derive delete with primary key WHERE and RETURNING") {
    val schema = assembleWidgetModel(
      """
        |use stache.codegen.sql#DerivedStruct
        |use stache.codegen.sql#sqlDeriveDelete
        |""".stripMargin,
      """
        |@sqlDeriveDelete(targetTable: "example#Widget")
        |operation DeleteWidget {
        |    input: DerivedStruct
        |    output: Boolean
        |}
        |""".stripMargin
    )

    assertEquals(
      queryStatement(schema.queries, PostgresDialect, "example#DeleteWidget"),
      "DELETE FROM widgets WHERE id = $1 RETURNING id;"
    )
  }

  test("SQLite - renders derive delete with primary key WHERE and RETURNING") {
    val schema = assembleWidgetModel(
      """
        |use stache.codegen.sql#DerivedStruct
        |use stache.codegen.sql#sqlDeriveDelete
        |""".stripMargin,
      """
        |@sqlDeriveDelete(targetTable: "example#Widget")
        |operation DeleteWidget {
        |    input: DerivedStruct
        |    output: Boolean
        |}
        |""".stripMargin
    )

    assertEquals(
      queryStatement(schema.queries, SqliteDialect, "example#DeleteWidget"),
      "DELETE FROM widgets WHERE id = ? RETURNING id;"
    )
  }

  test("Postgres - renders derive select one with all columns and primary key WHERE") {
    val schema = assembleWidgetModel(
      """
        |use stache.codegen.sql#DerivedStruct
        |use stache.codegen.sql#sqlDeriveSelectOne
        |""".stripMargin,
      """
        |@sqlDeriveSelectOne(targetTable: "example#Widget")
        |operation GetWidget {
        |    input: DerivedStruct
        |    output: Widget
        |}
        |""".stripMargin
    )

    assertEquals(
      queryStatement(schema.queries, PostgresDialect, "example#GetWidget"),
      "SELECT id, foo, bar, created_at, updated_at FROM widgets WHERE id = $1;"
    )
  }

  test("SQLite - renders derive select one with all columns and primary key WHERE") {
    val schema = assembleWidgetModel(
      """
        |use stache.codegen.sql#DerivedStruct
        |use stache.codegen.sql#sqlDeriveSelectOne
        |""".stripMargin,
      """
        |@sqlDeriveSelectOne(targetTable: "example#Widget")
        |operation GetWidget {
        |    input: DerivedStruct
        |    output: Widget
        |}
        |""".stripMargin
    )

    assertEquals(
      queryStatement(schema.queries, SqliteDialect, "example#GetWidget"),
      "SELECT id, foo, bar, created_at, updated_at FROM widgets WHERE id = ?;"
    )
  }

  test("Postgres - renders structure-based update SET and WHERE columns") {
    val schema = assembleWidgetModel(
      """
        |use stache.codegen.sql#sqlUpdate
        |""".stripMargin,
      """
        |@sqlUpdate(tableRef: "example#Widget")
        |structure WidgetUpdate {
        |    id: String
        |    foo: String
        |}
        |""".stripMargin
    )

    assertEquals(
      queryStatement(schema.queries, PostgresDialect, "example#WidgetUpdate"),
      """UPDATE widgets
        |SET foo = $1, updated_at = CURRENT_TIMESTAMP
        |WHERE id = $2 RETURNING updated_at;""".stripMargin
    )
  }

  test("SQLite - renders structure-based update SET and WHERE columns") {
    val schema = assembleWidgetModel(
      """
        |use stache.codegen.sql#sqlUpdate
        |""".stripMargin,
      """
        |@sqlUpdate(tableRef: "example#Widget")
        |structure WidgetUpdate {
        |    id: String
        |    foo: String
        |}
        |""".stripMargin
    )

    assertEquals(
      queryStatement(schema.queries, SqliteDialect, "example#WidgetUpdate"),
      """UPDATE widgets
        |SET foo = ?, updated_at = CURRENT_TIMESTAMP
        |WHERE id = ? RETURNING updated_at;""".stripMargin
    )
  }
}
