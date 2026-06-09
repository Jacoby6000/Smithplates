package com.jacoby6000.smithplates

import com.jacoby6000.smithplates.sql.SqlSchema
import com.jacoby6000.smithplates.sql.postgres.PostgresRenderer
import com.jacoby6000.smithplates.sql.query.SqlBindPlaceholder
import com.jacoby6000.smithplates.sql.query.SqlQueryRenderOutput
import com.jacoby6000.smithplates.sql.query.SqlQueryRenderer
import com.jacoby6000.smithplates.sql.query.postgres.PostgresSqlQueryRenderer
import com.jacoby6000.smithplates.sql.query.sqlite.SqliteSqlQueryRenderer
import com.jacoby6000.smithplates.sql.service.SqlServiceIr
import com.jacoby6000.smithplates.sql.shared.SqlSchemaDdlRenderer
import com.jacoby6000.smithplates.sql.sqlite.SqliteRenderer

object DialectRenderers {
  def queryRendererForKey(key: String): SqlQueryRenderer =
    key match {
      case "sqlite"   => sqliteQueryRenderer()
      case "postgres" => postgresQueryRenderer()
      case other      => throw new IllegalArgumentException(s"unsupported dialect key: $other")
    }

  def schemaDdlRendererForKey(key: String): SqlSchemaDdlRenderer =
    key match {
      case "sqlite"   => SqliteRenderer
      case "postgres" => PostgresRenderer
      case other      => throw new IllegalArgumentException(s"unsupported dialect key: $other")
    }

  def queryRenderersForKeys(keys: List[String]): Map[String, SqlQueryRenderer] =
    keys.map(key => key -> queryRendererForKey(key)).toMap

  def schemaDdlRenderersForKeys(keys: List[String]): Map[String, SqlSchemaDdlRenderer] =
    keys.map(key => key -> schemaDdlRendererForKey(key)).toMap

  def render(schema: SqlSchema, serviceIr: SqlServiceIr, dialectKey: String): String = {
    val queryRenderer = queryRendererForKey(dialectKey)
    SqlQueryRenderOutput.formatWithDdl(
      schemaDdlRendererForKey(dialectKey).renderSchemaDdlStatements(schema),
      queryRenderer.renderQueryUnits(serviceIr.queries),
      queryRenderer.migrationBindPlaceholder
    )
  }

  private def sqliteQueryRenderer(): SqliteSqlQueryRenderer =
    new SqliteSqlQueryRenderer(
      migrationBindPlaceholder = SqlBindPlaceholder("?"),
      codegenBindPlaceholder = SqlBindPlaceholder("?")
    )

  private def postgresQueryRenderer(): PostgresSqlQueryRenderer =
    new PostgresSqlQueryRenderer(
      migrationBindPlaceholder = SqlBindPlaceholder("$" + SqlBindPlaceholder.NumberToken),
      codegenBindPlaceholder = SqlBindPlaceholder("%s")
    )
}
