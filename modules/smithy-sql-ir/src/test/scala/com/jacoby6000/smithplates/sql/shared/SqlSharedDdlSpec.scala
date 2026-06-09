package com.jacoby6000.smithplates.sql.shared

import munit.FunSuite

final class SqlSharedDdlSpec extends FunSuite {
  private val statementSeparator = "\n\n"

  private def joinGrouped(prefix: List[String], suffix: List[String]): String =
    List(prefix.filter(_.nonEmpty), suffix.filter(_.nonEmpty))
      .filter(_.nonEmpty)
      .map(_.mkString(statementSeparator))
      .mkString(statementSeparator)

  private def joinStatements(statements: List[String]): String =
    statements.filter(_.nonEmpty).mkString(statementSeparator)

  test("joinGrouped - inserts blank line between prefix and suffix sections") {
    val ddl =
      joinGrouped(
        List("CREATE TYPE example_status AS ENUM ('a');"),
        List("CREATE TABLE t (id TEXT);")
      )
    assertEquals(
      ddl,
      "CREATE TYPE example_status AS ENUM ('a');\n\nCREATE TABLE t (id TEXT);"
    )
  }

  test("joinGrouped - omits blank line when a section group is empty") {
    assertEquals(
      joinGrouped(Nil, List("CREATE TABLE t (id TEXT);")),
      "CREATE TABLE t (id TEXT);"
    )
    assertEquals(
      joinGrouped(List("CREATE TYPE t AS ENUM ('a');"), Nil),
      "CREATE TYPE t AS ENUM ('a');"
    )
  }

  test("joinStatements - joins non-empty statements") {
    assertEquals(
      joinStatements(List("CREATE TABLE a ();", "CREATE TABLE b ();")),
      "CREATE TABLE a ();\n\nCREATE TABLE b ();"
    )
  }
}
