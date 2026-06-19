package com.jacoby6000.smithplates.plugin

import com.jacoby6000.smithplates.sql.ddl.renderer.common.SqlSchemaDdlRenderer
import com.jacoby6000.smithplates.sql.ddl.renderer.common.SqlShared
import com.jacoby6000.smithplates.sql.ddl.renderer.postgres.PostgresRenderer
import com.jacoby6000.smithplates.sql.ddl.renderer.sqlite.SqliteRenderer
import com.jacoby6000.smithplates.sql.model.SqlSchema
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlBindPlaceholder
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlQueryRenderer
import com.jacoby6000.smithplates.sql.service.query.renderer.postgres.PostgresSqlQueryRenderer
import com.jacoby6000.smithplates.sql.service.query.renderer.sqlite.SqliteSqlQueryRenderer

object DialectRenderers {
  def queryRendererForKey(key: String): SqlQueryRenderer =
    key match {
      case "sqlite"   => internal.sqliteQueryRenderer()
      case "postgres" => internal.postgresQueryRenderer()
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

  def renderDdlOnly(schema: SqlSchema, dialectKey: String): String =
    SqlShared.formatDdlStatements(schemaDdlRendererForKey(dialectKey).renderSchemaDdlStatements(schema))

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def sqliteQueryRenderer(): SqlQueryRenderer =
      SqliteSqlQueryRenderer(
        migrationBindPlaceholder = SqlBindPlaceholder("?"),
        codegenBindPlaceholder = SqlBindPlaceholder("?")
      )

    def postgresQueryRenderer(): SqlQueryRenderer =
      PostgresSqlQueryRenderer(
        migrationBindPlaceholder = SqlBindPlaceholder("$" + SqlBindPlaceholder.NumberToken),
        codegenBindPlaceholder = SqlBindPlaceholder("%s")
      )
  }
}
