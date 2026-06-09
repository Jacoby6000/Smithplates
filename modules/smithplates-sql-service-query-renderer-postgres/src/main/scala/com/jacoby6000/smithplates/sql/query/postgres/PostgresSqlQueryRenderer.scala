package com.jacoby6000.smithplates.sql.query.postgres

import com.jacoby6000.smithplates.sql.SqlColumnType
import com.jacoby6000.smithplates.sql.SqlTimestampFormat
import com.jacoby6000.smithplates.sql.query.*
import com.jacoby6000.smithplates.sql.service.SqlQueries
import com.jacoby6000.smithplates.sql.shared.SqlShared

final class PostgresSqlQueryRenderer(
    val migrationBindPlaceholder: SqlBindPlaceholder,
    val codegenBindPlaceholder: SqlBindPlaceholder
) extends SqlQueryRenderer {
  override def key: String = "postgres"

  override def renderQueryUnits(queries: SqlQueries): List[SqlRenderedQuery] =
    SqlQueryRendering.renderQueryUnits(queries, autoUpdatedTimestampAssignment)

  private def autoUpdatedTimestampAssignment(columnName: String, columnType: SqlColumnType): String = {
    val expression = columnType match {
      case SqlColumnType.Timestamp(format) =>
        format match {
          case SqlTimestampFormat.DateTime     => "CURRENT_TIMESTAMP"
          case SqlTimestampFormat.EpochSeconds => SqlShared.postgresEpochSecondsExpression
        }
      case _                               => "CURRENT_TIMESTAMP"
    }
    s"$columnName = $expression"
  }
}
