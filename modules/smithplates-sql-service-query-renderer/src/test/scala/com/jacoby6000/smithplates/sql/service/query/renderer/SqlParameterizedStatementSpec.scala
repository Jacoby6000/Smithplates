package com.jacoby6000.smithplates.sql.service.query.renderer

import com.jacoby6000.smithplates.sql.ddl.renderer.common.SqlShared
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

  test("SqlParameterizedStatementSpec.internal.fromConfig - accepts driver placeholder strings") {
    assertEquals(
      SqlParameterizedStatementSpec.internal.fromConfig("%s"),
      Some(SqlBindPlaceholder("%s"))
    )
  }
}
object SqlParameterizedStatementSpec {

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def fromConfig(value: String): Option[SqlBindPlaceholder] =
      SqlShared.trimmedNonEmpty(value).map(SqlBindPlaceholder(_))
  }
}
