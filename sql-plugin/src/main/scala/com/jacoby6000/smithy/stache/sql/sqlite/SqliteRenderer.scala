package com.jacoby6000.smithy.stache.sql.sqlite

import com.jacoby6000.smithy.stache.sql.*
import com.jacoby6000.smithy.stache.sql.shared.SqlQueryRenderer
import com.jacoby6000.smithy.stache.sql.shared.SqlRenderUnit
import com.jacoby6000.smithy.stache.sql.shared.SqlShared

object SqliteRenderer extends DialectRenderer {
  override val dialect: SqlDialect = SqliteDialect

  override def renderUnits(schema: SqlSchema): List[SqlRenderUnit] = {
    SqlShared.requireTables(schema)
    SqlShared.renderDdlUnits(schema, renderColumn) ++
      SqlQueryRenderer.renderQueryUnits(schema.queries)
  }

  def renderSchemaDdl(schema: SqlSchema): String = {
    SqlShared.requireTables(schema)
    SqlShared
      .renderDdlUnits(schema, renderColumn)
      .map(_.formatted)
      .filter(_.nonEmpty)
      .mkString("\n\n")
  }

  private def renderColumn(column: SqlColumn): String = {
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
      column.autoGeneration.map(SqlQueryRenderer.defaultClause(SqliteDialect, _))
    )
  }

  private def sqlTypeFor(columnType: SqlColumnType): String =
    SqlShared.baseSqlType(columnType).getOrElse {
      columnType match {
        case SqlColumnType.Timestamp => "TEXT"
        case SqlColumnType.Blob      => "BLOB"
        case SqlColumnType.Text | SqlColumnType.Uuid | SqlColumnType.Boolean | SqlColumnType.Varchar(_) |
            SqlColumnType.Json | _: SqlColumnType.StringEnum =>
          "TEXT"
        case other                   =>
          throw new IllegalStateException(s"Unsupported SQLite column type: $other")
      }
    }
}
