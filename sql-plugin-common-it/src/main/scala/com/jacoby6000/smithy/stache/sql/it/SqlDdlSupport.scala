package com.jacoby6000.smithy.stache.sql.it

import com.jacoby6000.smithy.stache.sql.DialectRenderer
import com.jacoby6000.smithy.stache.sql.SqlSchema

import java.sql.Connection
import scala.util.Try

object SqlDdlSupport {
  def renderDdl(renderer: DialectRenderer, schema: SqlSchema): String =
    renderer.render(schema)

  def splitStatements(ddl: String): List[String] =
    ddl
      .split("""\s*;\s*""")
      .map(_.trim)
      .filter(_.nonEmpty)
      .flatMap(stripLeadingComments)
      .map(_ + ";")
      .toList

  private def stripLeadingComments(chunk: String): Option[String] = {
    val executable =
      chunk.linesIterator
        .dropWhile(line => line.trim.isEmpty || line.trim.startsWith("--"))
        .mkString("\n")
        .trim
    Option.when(executable.nonEmpty)(executable)
  }

  def applyDdl(connection: Connection, ddl: String): Unit = {
    val statement = connection.createStatement()
    try
      splitStatements(ddl).foreach { sql =>
        statement.execute(sql)
      }
    finally statement.close()
  }

  def assertInsertFails(connection: Connection, sql: String): Unit = {
    val failed = Try(executeUpdate(connection, sql)).isFailure
    if (!failed) {
      throw new AssertionError(s"expected statement to fail: $sql")
    }
  }

  def executeUpdate(connection: Connection, sql: String): Unit = {
    val statement = connection.createStatement()
    try {
      val _ = statement.executeUpdate(sql)
    } finally statement.close()
  }
}
