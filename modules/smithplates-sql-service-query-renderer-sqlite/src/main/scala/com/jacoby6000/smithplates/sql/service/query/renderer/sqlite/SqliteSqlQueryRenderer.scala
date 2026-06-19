package com.jacoby6000.smithplates.sql.service.query.renderer.sqlite

import com.jacoby6000.smithplates.sql.model.SqlColumnType
import com.jacoby6000.smithplates.sql.model.SqlTimestampFormat
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlBindPlaceholder
import com.jacoby6000.smithplates.sql.service.query.renderer.common.DialectSqlQueryRenderer

object SqliteSqlQueryRenderer {
  def apply(
      migrationBindPlaceholder: SqlBindPlaceholder,
      codegenBindPlaceholder: SqlBindPlaceholder
  ): DialectSqlQueryRenderer =
    new DialectSqlQueryRenderer(
      key = "sqlite",
      migrationBindPlaceholder = migrationBindPlaceholder,
      codegenBindPlaceholder = codegenBindPlaceholder,
      autoUpdatedTimestampAssignment = autoUpdatedTimestampAssignment
    )

  private def autoUpdatedTimestampAssignment(columnName: String, columnType: SqlColumnType): String = {
    val expression = columnType match {
      case SqlColumnType.Timestamp(format) =>
        format match {
          case SqlTimestampFormat.DateTime     => "CURRENT_TIMESTAMP"
          case SqlTimestampFormat.EpochSeconds => "unixepoch('subsec')"
        }
      case _                               => "CURRENT_TIMESTAMP"
    }
    s"$columnName = $expression"
  }
}
