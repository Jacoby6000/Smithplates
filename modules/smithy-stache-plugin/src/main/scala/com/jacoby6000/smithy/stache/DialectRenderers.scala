package com.jacoby6000.smithy.stache

import com.jacoby6000.smithy.stache.sql.*
import com.jacoby6000.smithy.stache.sql.postgres.PostgresRenderer
import com.jacoby6000.smithy.stache.sql.sqlite.SqliteRenderer

object DialectRenderers {
  def forDialect(dialect: SqlDialect): DialectRenderer =
    dialect match {
      case SqliteDialect   => SqliteRenderer
      case PostgresDialect => PostgresRenderer
    }
}
