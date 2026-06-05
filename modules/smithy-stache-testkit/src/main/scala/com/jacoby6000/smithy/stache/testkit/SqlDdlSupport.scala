package com.jacoby6000.smithy.stache.testkit

import com.jacoby6000.smithy.stache.sql.SqlSchema
import com.jacoby6000.smithy.stache.sql.service.DialectRenderer
import com.jacoby6000.smithy.stache.sql.shared.DDLStatement

import java.sql.Connection
import scala.util.Try

object SqlDdlSupport {
  def renderSchemaDdlStatements(renderer: DialectRenderer, schema: SqlSchema): List[DDLStatement] =
    renderer.renderSchemaDdlStatements(schema)

  def applyDdl(connection: Connection, statements: Iterable[DDLStatement]): Unit = {
    val statement = connection.createStatement()
    try
      statements.foreach { ddl =>
        statement.execute(ddl.statement)
      }
    finally statement.close()
  }

  def applyDdl(connection: Connection, renderer: DialectRenderer, schema: SqlSchema): Unit =
    applyDdl(connection, renderSchemaDdlStatements(renderer, schema))

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
