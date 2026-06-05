package com.jacoby6000.smithy.sql.postgres

import com.jacoby6000.smithy.sql._
import com.jacoby6000.smithy.sql.shared.{SqlQueryRenderer, SqlRenderUnit, SqlShared}

object PostgresRenderer extends DialectRenderer {
  override val dialect: SqlDialect = PostgresDialect

  override def renderUnits(schema: SqlSchema): List[SqlRenderUnit] = {
    SqlShared.requireTables(schema)
    renderSchemaDdlUnits(schema) ++
      SqlQueryRenderer.renderQueryUnits(schema.queries)
  }

  def renderSchemaDdl(schema: SqlSchema): String = {
    SqlShared.requireTables(schema)
    renderSchemaDdlUnits(schema)
      .map(_.formatted)
      .filter(_.nonEmpty)
      .mkString("\n\n")
  }

  private def renderSchemaDdlUnits(schema: SqlSchema): List[SqlRenderUnit.Ddl] =
    preTableUnits(schema) ++ SqlShared.renderDdlUnits(schema, renderColumn)

  private def preTableUnits(schema: SqlSchema): List[SqlRenderUnit.Ddl] =
    schema.tables
      .flatMap(_.columns.map(_.columnType))
      .collect { case enumType: SqlColumnType.StringEnum => enumType }
      .distinctBy(_.typeName)
      .sortBy(_.typeName)
      .map { enumType =>
        val literals = enumType.values.map(value => s"'${value.replace("'", "''")}'").mkString(", ")
        SqlRenderUnit.Ddl(
          enumType.shapeId,
          s"CREATE TYPE ${enumType.typeName} AS ENUM ($literals);"
        )
      }

  private def renderColumn(column: SqlColumn): String = {
    val checks = column.columnType match {
      case enumType: SqlColumnType.IntEnum =>
        List(SqlShared.intEnumCheck(column.name, enumType.values))
      case _ => Nil
    }
    SqlShared.renderColumnLine(
      column.name,
      sqlTypeFor(column.columnType),
      column.nullable,
      checks,
      column.autoGeneration.map(SqlQueryRenderer.defaultClause(PostgresDialect, _))
    )
  }

  private def sqlTypeFor(columnType: SqlColumnType): String =
    SqlShared.baseSqlType(columnType).getOrElse {
      columnType match {
        case SqlColumnType.Varchar(maxLength)   => s"VARCHAR($maxLength)"
        case SqlColumnType.Uuid                 => "UUID"
        case SqlColumnType.Timestamp            => "TIMESTAMP"
        case SqlColumnType.Boolean              => "BOOLEAN"
        case SqlColumnType.Json                 => "JSONB"
        case SqlColumnType.Blob                 => "BYTEA"
        case SqlColumnType.Text                 => "TEXT"
        case enumType: SqlColumnType.StringEnum => enumType.typeName
        case other =>
          throw new IllegalStateException(s"Unsupported Postgres column type: $other")
      }
    }
}
