package com.jacoby6000.smithplates.sql.sqlite

import com.jacoby6000.smithplates.sql.*
import com.jacoby6000.smithplates.sql.shared.DDLStatement
import com.jacoby6000.smithplates.sql.shared.SqlSchemaDdlRenderer
import com.jacoby6000.smithplates.sql.shared.SqlShared

object SqliteRenderer extends SqlSchemaDdlRenderer {
  override def renderSchemaDdlStatements(schema: SqlSchema): List[DDLStatement] = {
    SqlShared.requireTables(schema)
    SqlShared.renderDdlStatements(schema, renderColumn)
  }

  private[sqlite] def renderColumn(column: SqlColumn): String = {
    val checks = column.columnType match {
      case SqlColumnType.Varchar(maxLength)   =>
        List(s" CHECK(length(${column.name}) <= $maxLength)")
      case enumType: SqlColumnType.StringEnum =>
        val literals = enumType.values.map(value => s"'${value.replace("'", "''")}'").mkString(", ")
        List(s" CHECK(${column.name} IN ($literals))")
      case enumType: SqlColumnType.IntEnum    =>
        List(SqlShared.intEnumCheck(column.name, enumType.values))
      case _                                  => Nil
    }
    SqlShared.renderColumnLine(
      column.name,
      sqlTypeFor(column.columnType),
      column.nullable,
      checks,
      column.autoGeneration.map(autoGenerationDefaultClause(_, column.columnType))
    )
  }

  private def autoGenerationDefaultClause(
      autoGeneration: SqlAutoGeneration,
      columnType: SqlColumnType
  ): String =
    autoGeneration match {
      case SqlAutoUuid                               => sqliteAutoUuidDefault
      case SqlCreatedTimestamp | SqlUpdatedTimestamp =>
        columnType match {
          case SqlColumnType.Timestamp(format) => sqliteTimestampExpression(format)
          case _                               => "CURRENT_TIMESTAMP"
        }
    }

  private def sqliteTimestampExpression(format: SqlTimestampFormat): String =
    format match {
      case SqlTimestampFormat.DateTime     => "CURRENT_TIMESTAMP"
      case SqlTimestampFormat.EpochSeconds => "unixepoch('subsec')"
    }

  private val sqliteAutoUuidDefault: String =
    "(lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || " +
      "substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || " +
      "substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6))))"

  private def sqlTypeFor(columnType: SqlColumnType): String =
    columnType match {
      case SqlColumnType.Integer                                    => "INTEGER"
      case SqlColumnType.BigInt                                     => "BIGINT"
      case enumType: SqlColumnType.IntEnum                          =>
        if (enumType.values.forall(value => value >= Int.MinValue && value <= Int.MaxValue)) {
          "INTEGER"
        } else {
          "BIGINT"
        }
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
    }
}
