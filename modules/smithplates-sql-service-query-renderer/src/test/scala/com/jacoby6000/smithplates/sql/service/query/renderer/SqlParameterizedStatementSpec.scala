package com.jacoby6000.smithplates.sql.service.query.renderer

import com.jacoby6000.smithplates.sql.ddl.renderer.common.SqlShared
import munit.FunSuite

final class SqlParameterizedStatementSpec extends FunSuite {
  private def fromConfig(value: String): Option[SqlBindPlaceholder] =
    SqlShared.trimmedNonEmpty(value).map(SqlBindPlaceholder(_))

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
      fromConfig("%s"),
      Some(SqlBindPlaceholder("%s"))
    )
  }
}
