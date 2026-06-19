package com.jacoby6000.smithplates.sql.ddl.renderer.sqlite

import com.jacoby6000.smithplates.sql.*
import com.jacoby6000.smithplates.sql.ddl.renderer.common.SqlSchemaDdlRenderer
import com.jacoby6000.smithplates.sql.ddl.renderer.common.SqlShared
import com.jacoby6000.smithplates.sql.model.*

object SqliteRenderer extends SqlSchemaDdlRenderer {
  override def renderSchemaDdlStatements(schema: SqlSchema): List[DDLStatement] =
    SqlShared.renderDdlStatements(schema, renderColumn)

  private[sqlite] def renderColumn(column: SqlColumn): String = {
    val checks = column.columnType match {
      case SqlColumnType.Varchar(maxLength)   =>
        List(s" CHECK(length(${column.name}) <= $maxLength)")
      case enumType: SqlColumnType.StringEnum =>
        List(s" CHECK(${column.name} IN (${SqlShared.quotedStringLiterals(enumType.values)}))")
      case enumType: SqlColumnType.IntEnum    =>
        List(SqlShared.intEnumCheck(column.name, enumType.values))
      case _                                  => Nil
    }
    SqlShared.renderColumnLine(
      column.name,
      internal.sqlTypeFor(column.columnType),
      column.nullable,
      checks,
      column.autoGeneration.map(
        SqlShared.autoGenerationDefaultClause(
          _,
          column.columnType,
          uuidExpression = internal.sqliteAutoUuidDefault,
          timestampExpression = internal.sqliteTimestampExpression
        )
      )
    )
  }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def sqliteTimestampExpression(format: SqlTimestampFormat): String =
      format match {
        case SqlTimestampFormat.DateTime     => "CURRENT_TIMESTAMP"
        case SqlTimestampFormat.EpochSeconds => "unixepoch('subsec')"
      }

    val sqliteAutoUuidDefault: String =
      "(lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || " +
        "substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || " +
        "substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6))))"

    def sqlTypeFor(columnType: SqlColumnType): String =
      SqlShared.baseSqlType(columnType) match {
        case Some(storage) => storage
        case None          =>
          columnType match {
            case SqlColumnType.Timestamp(SqlTimestampFormat.DateTime)     => "TEXT"
            // DESNOTE(jbarber, 2026-06-05): Smithy epoch-seconds allows fractional seconds (e.g. millis).
            //                               SQLite REAL matches that wire format; precision beyond milliseconds
            //                               is truncated by the spec. REAL is a 64-bit floating point number.
            //                               Eventually, this may cause floating point rounding issues, but sqlite
            //                               has no better alternative format to match the spec.
            case SqlColumnType.Timestamp(SqlTimestampFormat.EpochSeconds) => "REAL"
            case SqlColumnType.Blob                                       => "BLOB"
            case SqlColumnType.Text | SqlColumnType.Uuid | SqlColumnType.Boolean | SqlColumnType.Json |
                SqlColumnType.Varchar(_) | _: SqlColumnType.StringEnum =>
              "TEXT"
            case other                                                    =>
              throw new IllegalStateException(s"Unsupported SQLite column type: $other")
          }
      }
  }
}
