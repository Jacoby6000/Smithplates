package com.jacoby6000.smithplates.sql.service.query.renderer.postgres

import com.jacoby6000.smithplates.sql.ddl.renderer.common.SqlShared
import com.jacoby6000.smithplates.sql.model.SqlColumnType
import com.jacoby6000.smithplates.sql.model.SqlTimestampFormat
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlBindPlaceholder
import com.jacoby6000.smithplates.sql.service.query.renderer.common.DialectSqlQueryRenderer

object PostgresSqlQueryRenderer {
  def apply(
      migrationBindPlaceholder: SqlBindPlaceholder,
      codegenBindPlaceholder: SqlBindPlaceholder
  ): DialectSqlQueryRenderer =
    new DialectSqlQueryRenderer(
      key = "postgres",
      migrationBindPlaceholder = migrationBindPlaceholder,
      codegenBindPlaceholder = codegenBindPlaceholder,
      autoUpdatedTimestampAssignment = internal.autoUpdatedTimestampAssignment
    )

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def autoUpdatedTimestampAssignment(columnName: String, columnType: SqlColumnType): String = {
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
}
