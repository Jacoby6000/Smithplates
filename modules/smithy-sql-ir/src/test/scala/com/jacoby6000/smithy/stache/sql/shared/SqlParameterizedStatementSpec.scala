package com.jacoby6000.smithy.stache.sql.shared

import munit.FunSuite

final class SqlParameterizedStatementSpec extends FunSuite {
  test("format - inserts numbered placeholders when pattern contains ?{n}") {
    assertEquals(
      SqlBindPlaceholder.format(
        List("SELECT * FROM foo WHERE id = ", ""),
        SqlBindPlaceholder("$" + SqlBindPlaceholder.NumberToken)
      ),
      "SELECT * FROM foo WHERE id = $1"
    )
  }

  test("format - supports literal placeholder patterns") {
    val segments = List("INSERT INTO widgets (foo) VALUES (", ");")
    assertEquals(
      SqlBindPlaceholder.format(segments, SqlBindPlaceholder("?")),
      "INSERT INTO widgets (foo) VALUES (?);"
    )
    assertEquals(
      SqlBindPlaceholder.format(segments, SqlBindPlaceholder("%s")),
      "INSERT INTO widgets (foo) VALUES (%s);"
    )
  }

  test("fromConfig - accepts driver placeholder strings") {
    assertEquals(
      SqlBindPlaceholder.fromConfig("%s").toOption,
      Some(SqlBindPlaceholder("%s"))
    )
  }

  test("inferForCodegen - maps dialects to bundled driver placeholders") {
    assertEquals(
      SqlBindPlaceholder.inferForCodegen(com.jacoby6000.smithy.stache.sql.SqliteDialect),
      SqlBindPlaceholder("?")
    )
    assertEquals(
      SqlBindPlaceholder.inferForCodegen(com.jacoby6000.smithy.stache.sql.PostgresDialect),
      SqlBindPlaceholder("%s")
    )
  }
}
