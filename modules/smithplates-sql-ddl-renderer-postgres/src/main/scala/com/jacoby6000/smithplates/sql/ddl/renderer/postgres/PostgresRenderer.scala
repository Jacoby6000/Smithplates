package com.jacoby6000.smithplates.sql.ddl.renderer.postgres

import com.jacoby6000.smithplates.sql.*
import com.jacoby6000.smithplates.sql.ddl.renderer.common.SqlSchemaDdlRenderer
import com.jacoby6000.smithplates.sql.ddl.renderer.common.SqlShared
import com.jacoby6000.smithplates.sql.model.*

object PostgresRenderer extends SqlSchemaDdlRenderer {
  override def renderSchemaDdlStatements(schema: SqlSchema): List[DDLStatement] =
    SqlShared.renderDdlStatements(
      schema = schema,
      renderColumn = renderColumn,
      preTableStatements = internal.preTableEnumStatements,
      foreignKeyRendering = SqlShared.ForeignKeyRendering.Separate(internal.renderForeignKeyConstraint)
    )

  private[postgres] def renderColumn(column: SqlColumn): String = {
    val checks          = column.columnType match {
      case enumType: SqlColumnType.IntEnum =>
        List(SqlShared.intEnumCheck(column.name, enumType.values))
      case _                               => Nil
    }
    val defaultClause   = column.autoGeneration.flatMap(
      SqlShared.autoGenerationDefaultClause(
        _,
        column.columnType,
        uuidExpression = "gen_random_uuid()",
        timestampExpression = internal.postgresTimestampExpression
      )
    )
    val isAutoIncrement = column.autoGeneration.contains(SqlAutoIncrement)
    if (isAutoIncrement) {
      s"${column.name} INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY"
    } else {
      SqlShared.renderColumnLine(
        column.name,
        internal.sqlTypeFor(column.columnType),
        column.nullable,
        checks,
        defaultClause
      )
    }
  }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def preTableEnumStatements(schema: SqlSchema): List[DDLStatement] =
      schema.tables
        .flatMap(_.columns.map(_.columnType))
        .collect { case enumType: SqlColumnType.StringEnum => enumType }
        .distinctBy(_.typeName)
        .sortBy(_.typeName)
        .map { enumType =>
          val literals = SqlShared.quotedStringLiterals(enumType.values)
          DDLStatement.CreateEnumType(
            enumType = enumType,
            statement = s"CREATE TYPE ${enumType.typeName} AS ENUM ($literals);"
          )
        }

    def postgresTimestampExpression(format: SqlTimestampFormat): String =
      format match {
        case SqlTimestampFormat.DateTime     => "CURRENT_TIMESTAMP"
        case SqlTimestampFormat.EpochSeconds => SqlShared.postgresEpochSecondsExpression
      }

    def renderForeignKeyConstraint(
        table: SqlTable,
        foreignKey: SqlForeignKey,
        referencedTable: SqlTable
    ): String = {
      val constraintName = s"fk_${table.name}_${foreignKey.column}"
      s"""ALTER TABLE ${table.name}
         |    ADD CONSTRAINT $constraintName
         |    FOREIGN KEY (${foreignKey.column}) REFERENCES ${referencedTable.name} (${foreignKey.referencesColumn});""".stripMargin
    }

    def sqlTypeFor(columnType: SqlColumnType): String =
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
}
