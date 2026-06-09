package com.jacoby6000.smithplates.sql.ddl.postgres

import com.jacoby6000.smithplates.sql.*
import com.jacoby6000.smithplates.sql.model.*
import com.jacoby6000.smithplates.sql.shared.SqlSchemaDdlRenderer
import com.jacoby6000.smithplates.sql.shared.SqlShared

object PostgresRenderer extends SqlSchemaDdlRenderer {
  override def renderSchemaDdlStatements(schema: SqlSchema): List[DDLStatement] = {
    SqlShared.requireTables(schema)
    preTableEnumStatements(schema) ++ SqlShared.renderDdlStatements(schema, renderColumn)
  }

  private def preTableEnumStatements(schema: SqlSchema): List[DDLStatement] =
    schema.tables
      .flatMap(_.columns.map(_.columnType))
      .collect { case enumType: SqlColumnType.StringEnum => enumType }
      .distinctBy(_.typeName)
      .sortBy(_.typeName)
      .map { enumType =>
        val literals = enumType.values.map(value => s"'${value.replace("'", "''")}'").mkString(", ")
        DDLStatement.CreateEnumType(
          enumType = enumType,
          statement = s"CREATE TYPE ${enumType.typeName} AS ENUM ($literals);"
        )
      }

  private[postgres] def renderColumn(column: SqlColumn): String = {
    val checks = column.columnType match {
      case enumType: SqlColumnType.IntEnum =>
        List(SqlShared.intEnumCheck(column.name, enumType.values))
      case _                               => Nil
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
      case SqlAutoUuid                               => "gen_random_uuid()"
      case SqlCreatedTimestamp | SqlUpdatedTimestamp =>
        columnType match {
          case SqlColumnType.Timestamp(format) => postgresTimestampExpression(format)
          case _                               => "CURRENT_TIMESTAMP"
        }
    }

  private def postgresTimestampExpression(format: SqlTimestampFormat): String =
    format match {
      case SqlTimestampFormat.DateTime     => "CURRENT_TIMESTAMP"
      case SqlTimestampFormat.EpochSeconds => SqlShared.postgresEpochSecondsExpression
    }

  private def sqlTypeFor(columnType: SqlColumnType): String =
    SqlShared.baseSqlType(columnType).getOrElse {
      columnType match {
        case SqlColumnType.Varchar(maxLength)   => s"VARCHAR($maxLength)"
        case SqlColumnType.Uuid                 => "UUID"
        case SqlColumnType.Timestamp(format)    =>
          format match {
            case SqlTimestampFormat.DateTime     => "TIMESTAMP"
            case SqlTimestampFormat.EpochSeconds => SqlShared.postgresEpochSecondsSqlType
          }
        case SqlColumnType.Boolean              => "BOOLEAN"
        case SqlColumnType.Json                 => "JSONB"
        case SqlColumnType.Blob                 => "BYTEA"
        case SqlColumnType.Text                 => "TEXT"
        case enumType: SqlColumnType.StringEnum => enumType.typeName
        case other                              =>
          throw new IllegalStateException(s"Unsupported Postgres column type: $other")
      }
    }
}
