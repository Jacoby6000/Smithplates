package com.jacoby6000.smithy.stache.sql.query

import com.jacoby6000.smithy.stache.sql.service.SqlQueries

/** Renders dialect-specific DML from validated service query IR. */
trait SqlQueryRenderer {
  def key: String

  def migrationBindPlaceholder: SqlBindPlaceholder

  def codegenBindPlaceholder: SqlBindPlaceholder

  def renderQueryUnits(queries: SqlQueries): List[SqlRenderedQuery]
}
