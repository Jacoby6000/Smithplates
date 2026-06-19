package com.jacoby6000.smithplates.sql.service.query.renderer.sqlite

import com.jacoby6000.smithplates.sql.*
import com.jacoby6000.smithplates.sql.service.SqlExtractionResult
import com.jacoby6000.smithplates.sql.service.SqlModelExtractor
import com.jacoby6000.smithplates.sql.service.SqlQueries
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlBindPlaceholder
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlQueryRenderer
import software.amazon.smithy.model.shapes.ShapeId

final class SqliteSqlQueryRendererSpec extends munit.FunSuite {
  private lazy val renderer =
    SqliteSqlQueryRenderer(
      migrationBindPlaceholder = SqlBindPlaceholder("?"),
      codegenBindPlaceholder = SqlBindPlaceholder("?")
    )

  private def queryStatement(queries: SqlQueries, renderer: SqlQueryRenderer, shapeId: String): String =
    renderer
      .renderQueryUnits(queries)
      .find(_.shapeId == ShapeId.from(shapeId))
      .map { query =>
        SqlBindPlaceholder.format(
          query.statement.segments,
          renderer.migrationBindPlaceholder
        )
      }
      .getOrElse(fail(s"query '$shapeId' was not rendered"))

  private val widgetTableUses =
    """
      |use smithplates.codegen.sql#sqlAutoUuid
      |use smithplates.codegen.sql#sqlCreatedTimestamp
      |use smithplates.codegen.sql#sqlPrimaryKey
      |use smithplates.codegen.sql#sqlTable
      |use smithplates.codegen.sql#sqlUpdatedTimestamp
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
  test("DeriveInsert - renders placeholders and RETURNING") {
    val schema = assembleWidgetModel(
      """
        |use smithplates.codegen.sql#DerivedStruct
        |use smithplates.codegen.sql#sqlDeriveInsert
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
      queryStatement(schema.queries, renderer, "example#CreateWidget"),
      "INSERT INTO widgets (foo, bar) VALUES (?, ?) RETURNING id;"
    )
  }
  test("DeriveUpdate - renders primary key WHERE and updatable SET columns") {
    val schema = assembleWidgetModel(
      """
        |use smithplates.codegen.sql#DerivedStruct
        |use smithplates.codegen.sql#sqlDeriveUpdate
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
      queryStatement(schema.queries, renderer, "example#UpdateWidget"),
      """UPDATE widgets
        |SET foo = ?, bar = ?, updated_at = CURRENT_TIMESTAMP
        |WHERE id = ? RETURNING updated_at;""".stripMargin
    )
  }
  test("DeriveDelete - renders primary key WHERE and RETURNING") {
    val schema = assembleWidgetModel(
      """
        |use smithplates.codegen.sql#DerivedStruct
        |use smithplates.codegen.sql#sqlDeriveDelete
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
      queryStatement(schema.queries, renderer, "example#DeleteWidget"),
      "DELETE FROM widgets WHERE id = ? RETURNING id;"
    )
  }
  test("DeriveSelectOne - renders all columns and primary key WHERE") {
    val schema = assembleWidgetModel(
      """
        |use smithplates.codegen.sql#DerivedStruct
        |use smithplates.codegen.sql#sqlDeriveSelectOne
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
      queryStatement(schema.queries, renderer, "example#GetWidget"),
      """SELECT widgets.id, widgets.foo, widgets.bar, widgets.created_at, widgets.updated_at
        |FROM widgets
        |WHERE id = ?;""".stripMargin
    )
  }
  test("Update - renders structure-based SET and WHERE columns") {
    val schema = assembleWidgetModel(
      """
        |use smithplates.codegen.sql#sqlUpdate
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
      queryStatement(schema.queries, renderer, "example#WidgetUpdate"),
      """UPDATE widgets
        |SET foo = ?, updated_at = CURRENT_TIMESTAMP
        |WHERE id = ? RETURNING updated_at;""".stripMargin
    )
  }
}
