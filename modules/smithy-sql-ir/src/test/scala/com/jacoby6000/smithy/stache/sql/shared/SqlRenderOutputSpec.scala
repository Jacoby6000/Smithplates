package com.jacoby6000.smithy.stache.sql.shared

import com.jacoby6000.smithy.stache.sql.*
import munit.FunSuite
import software.amazon.smithy.model.shapes.ShapeId

final class SqlRenderOutputSpec extends FunSuite {
  private def widgetTable: SqlTable =
    SqlTable(
      name = "widgets",
      shapeId = ShapeId.from("example#Widget"),
      columns = Nil,
      primaryKeys = List("id"),
      foreignKeys = Nil,
      indexes = Nil
    )

  test("format - joins DDL units and query units with section headers") {
    val units =
      List(
        SqlRenderUnit.Ddl(DDLStatement.CreateTable(widgetTable, "CREATE TABLE widgets (id TEXT);")),
        SqlRenderUnit.Query(
          ShapeId.from("example#UpdateWidget"),
          SqlParameterizedStatement(List("UPDATE widgets SET foo = ", " WHERE id = ", ";"))
        )
      )

    assertEquals(
      SqlRenderOutput.format(units, SqlBindPlaceholder.forDialect(PostgresDialect)),
      """-- example#Widget
        |CREATE TABLE widgets (id TEXT);
        |
        |-- Queries
        |
        |-- example#UpdateWidget
        |UPDATE widgets SET foo = $1 WHERE id = $2;""".stripMargin
    )
  }

  test("queryUnit - finds a rendered query by shape id") {
    val shapeId   = ShapeId.from("example#UpdateWidget")
    val statement = SqlParameterizedStatement(List("UPDATE widgets SET foo = ", " WHERE id = ", ";"))
    val units     =
      List(
        SqlRenderUnit.Query(shapeId, statement)
      )

    assertEquals(
      SqlRenderOutput.queryUnit(units, shapeId).map(_.statement),
      Some(statement)
    )
  }

  test("ddlUnit - finds a rendered DDL artifact by shape id") {
    val shapeId = ShapeId.from("example#Widget")
    val units   =
      List(
        SqlRenderUnit.Ddl(DDLStatement.CreateTable(widgetTable, "CREATE TABLE widgets (id TEXT);"))
      )

    assertEquals(
      SqlRenderOutput.ddlUnit(units, shapeId).map(_.statement),
      Some("CREATE TABLE widgets (id TEXT);")
    )
  }
}
