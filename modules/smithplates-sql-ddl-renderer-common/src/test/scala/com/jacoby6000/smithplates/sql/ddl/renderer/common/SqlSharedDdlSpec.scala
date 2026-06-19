package com.jacoby6000.smithplates.sql.ddl.renderer.common

import munit.FunSuite

final class SqlSharedDdlSpec extends FunSuite {
  test("SqlSharedDdlSpec.internal.joinGrouped - inserts blank line between prefix and suffix sections") {
    val ddl =
      SqlSharedDdlSpec.internal.joinGrouped(
        List("CREATE TYPE example_status AS ENUM ('a');"),
        List("CREATE TABLE t (id TEXT);")
      )
    assertEquals(
      ddl,
      "CREATE TYPE example_status AS ENUM ('a');\n\nCREATE TABLE t (id TEXT);"
    )
  }

  test("SqlSharedDdlSpec.internal.joinGrouped - omits blank line when a section group is empty") {
    assertEquals(
      SqlSharedDdlSpec.internal.joinGrouped(Nil, List("CREATE TABLE t (id TEXT);")),
      "CREATE TABLE t (id TEXT);"
    )
    assertEquals(
      SqlSharedDdlSpec.internal.joinGrouped(List("CREATE TYPE t AS ENUM ('a');"), Nil),
      "CREATE TYPE t AS ENUM ('a');"
    )
  }

  test("SqlSharedDdlSpec.internal.joinStatements - joins non-empty statements") {
    assertEquals(
      SqlSharedDdlSpec.internal.joinStatements(List("CREATE TABLE a ();", "CREATE TABLE b ();")),
      "CREATE TABLE a ();\n\nCREATE TABLE b ();"
    )
  }
}
object SqlSharedDdlSpec {

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    val statementSeparator                                              = "\n\n"
    def joinGrouped(prefix: List[String], suffix: List[String]): String =
      List(prefix.filter(_.nonEmpty), suffix.filter(_.nonEmpty))
        .filter(_.nonEmpty)
        .map(_.mkString(statementSeparator))
        .mkString(statementSeparator)
    def joinStatements(statements: List[String]): String                =
      statements.filter(_.nonEmpty).mkString(statementSeparator)
  }
}
