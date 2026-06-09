package com.jacoby6000.smithplates.sql.query

import com.jacoby6000.smithplates.sql.*
import com.jacoby6000.smithplates.sql.model.*
import munit.FunSuite
import software.amazon.smithy.model.shapes.ShapeId

final class SqlQueryRenderOutputSpec extends FunSuite {
  private def widgetTable: SqlTable =
    SqlTable(
      name = "widgets",
      shapeId = ShapeId.from("example#Widget"),
      columns = Nil,
      primaryKeys = List("id"),
      foreignKeys = Nil,
      indexes = Nil
    )

  test("formatWithDdl - joins DDL statements and query units with section headers") {
    val ddlStatements =
      List(
        DDLStatement.CreateTable(widgetTable, "CREATE TABLE widgets (id TEXT);")
      )
    val queries       =
      List(
        SqlRenderedQuery(
          ShapeId.from("example#UpdateWidget"),
          SqlParameterizedStatement(List("UPDATE widgets SET foo = ", " WHERE id = ", ";"))
        )
      )

    assertEquals(
      SqlQueryRenderOutput.formatWithDdl(
        ddlStatements,
        queries,
        SqlBindPlaceholder("$" + SqlBindPlaceholder.NumberToken)
      ),
      """-- example#Widget
        |CREATE TABLE widgets (id TEXT);
        |
        |-- Queries
        |
        |-- example#UpdateWidget
        |UPDATE widgets SET foo = $1 WHERE id = $2;""".stripMargin
    )
  }

  test("query - finds a rendered query by shape id") {
    val shapeId   = ShapeId.from("example#UpdateWidget")
    val statement = SqlParameterizedStatement(List("UPDATE widgets SET foo = ", " WHERE id = ", ";"))
    val queries   =
      List(
        SqlRenderedQuery(shapeId, statement)
      )

    assertEquals(
      SqlQueryRenderOutput.query(queries, shapeId).map(_.statement),
      Some(statement)
    )
  }
}
