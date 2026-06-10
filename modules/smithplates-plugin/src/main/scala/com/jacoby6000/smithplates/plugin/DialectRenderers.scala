package com.jacoby6000.smithplates.plugin

import com.jacoby6000.smithplates.sql.ddl.renderer.postgres.PostgresRenderer
import com.jacoby6000.smithplates.sql.ddl.renderer.sqlite.SqliteRenderer
import com.jacoby6000.smithplates.sql.model.SqlSchema
import com.jacoby6000.smithplates.sql.service.SqlServiceIr
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlBindPlaceholder
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlQueryRenderOutput
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlQueryRenderer
import com.jacoby6000.smithplates.sql.service.query.renderer.postgres.PostgresSqlQueryRenderer
import com.jacoby6000.smithplates.sql.service.query.renderer.sqlite.SqliteSqlQueryRenderer
import com.jacoby6000.smithplates.sql.shared.SqlSchemaDdlRenderer

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
