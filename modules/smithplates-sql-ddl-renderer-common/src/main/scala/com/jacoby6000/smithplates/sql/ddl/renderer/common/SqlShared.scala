package com.jacoby6000.smithplates.sql.ddl.renderer.common

import com.jacoby6000.smithplates.sql.SqlTableTree
import com.jacoby6000.smithplates.sql.SqlText
import com.jacoby6000.smithplates.sql.model.DDLStatement
import com.jacoby6000.smithplates.sql.model.NoSqlTables
import com.jacoby6000.smithplates.sql.model.SqlColumn
import com.jacoby6000.smithplates.sql.model.SqlColumnType
import com.jacoby6000.smithplates.sql.model.SqlForeignKey
import com.jacoby6000.smithplates.sql.model.SqlSchema
import com.jacoby6000.smithplates.sql.model.SqlTable
import software.amazon.smithy.model.shapes.ShapeId

/** Shared SQL DDL helpers, rendering, and Smithy enum utilities for dialect plugins. */
object SqlShared {
  private val StatementSeparator: String = "\n\n"

  enum ForeignKeyRendering {
    case Inline
    case Separate(renderStatement: (SqlTable, SqlForeignKey, SqlTable) => String)
  }

  def requireTables(schema: SqlSchema): Unit =
    if (schema.tables.isEmpty) {
      throw new IllegalStateException(NoSqlTables.message)
    }

  def trimmedNonEmpty(text: String): Option[String] =
    SqlText.trimmedNonEmpty(text)

  def trimmedNonEmpty(opt: Option[String]): Option[String] =
    SqlText.trimmedNonEmpty(opt)

  def formatDdlStatements(statements: List[DDLStatement]): String =
    statements.map(_.formatted).filter(_.nonEmpty).mkString(StatementSeparator)

  def renderSchema(
      schema: SqlSchema,
      renderColumn: SqlColumn => String,
      preTableStatements: SqlSchema => List[DDLStatement] = _ => Nil
  ): String =
    formatDdlStatements(renderDdlStatements(schema, renderColumn, preTableStatements))

  def renderDdlStatements(
      schema: SqlSchema,
      renderColumn: SqlColumn => String,
      preTableStatements: SqlSchema => List[DDLStatement] = _ => Nil,
      foreignKeyRendering: ForeignKeyRendering = ForeignKeyRendering.Inline
  ): List[DDLStatement] = {
    requireTables(schema)
    val prefix      = preTableStatements(schema)
    val tableById   = schema.tables.map(table => table.shapeId -> table).toMap
    val tables      =
      SqlTableTree
        .tablesInRenderOrder(schema)
        .flatMap { table =>
          val columnLines     =
            table.columns.map(column => renderColumnWithForeignKeyComment(table, column, schema, renderColumn))
          val primaryKeyLine  = s"PRIMARY KEY (${table.primaryKeys.mkString(", ")})"
          val foreignKeyLines =
            foreignKeyRendering match {
              case ForeignKeyRendering.Inline      =>
                table.foreignKeys.map(foreignKey => renderInlineForeignKey(tableById, foreignKey))
              case ForeignKeyRendering.Separate(_) =>
                Nil
            }
          val keyLines        = primaryKeyLine :: foreignKeyLines
          val createTable     =
            DDLStatement.CreateTable(
              table = table,
              statement = s"""CREATE TABLE ${table.name} (
                   |    ${columnLines.mkString(",\n    ")},
                   |
                   |    ${keyLines.mkString(",\n    ")}
                   |);""".stripMargin
            )
          val indexStatements =
            table.indexes.map { index =>
              val indexPrefix   = if (index.unique) "uidx" else "idx"
              val indexName     =
                index.name.getOrElse(s"${indexPrefix}_${table.name}_${index.columns.mkString("_")}")
              val uniqueKeyword = if (index.unique) "UNIQUE " else ""
              DDLStatement.CreateIndex(
                table = table,
                index = index,
                statement =
                  s"CREATE ${uniqueKeyword}INDEX $indexName ON ${table.name} (${index.columns.mkString(", ")});"
              )
            }
          createTable :: indexStatements
        }
        .filter(_.statement.nonEmpty)
    val foreignKeys =
      foreignKeyRendering match {
        case ForeignKeyRendering.Inline                    =>
          Nil
        case ForeignKeyRendering.Separate(renderStatement) =>
          schema.tables
            .sortBy(_.name)
            .flatMap { table =>
              table.foreignKeys
                .sortBy(_.sourceMember.toString)
                .map { foreignKey =>
                  val referencedTable = requireReferencedTable(tableById, foreignKey)
                  DDLStatement.AddForeignKey(
                    table = table,
                    foreignKey = foreignKey,
                    statement = renderStatement(table, foreignKey, referencedTable)
                  )
                }
            }
      }
    prefix ++ tables ++ foreignKeys
  }

  private def renderColumnWithForeignKeyComment(
      table: SqlTable,
      column: SqlColumn,
      schema: SqlSchema,
      renderColumn: SqlColumn => String
  ): String = {
    val base = renderColumn(column)
    table.foreignKeys.find(_.column == column.name) match {
      case Some(foreignKey) =>
        val referencedTable = schema.tables.find(_.shapeId == foreignKey.referencesShape).getOrElse {
          throw new IllegalStateException(
            s"Missing referenced sql table for shape ${foreignKey.referencesShape.toString}"
          )
        }
        s"$base /* FK -> ${referencedTable.name} (${foreignKey.referencesColumn}) */"
      case None             =>
        base
    }
  }

  private def renderInlineForeignKey(
      tableById: Map[ShapeId, SqlTable],
      foreignKey: SqlForeignKey
  ): String = {
    val referencedTable = requireReferencedTable(tableById, foreignKey)
    s"FOREIGN KEY (${foreignKey.column}) REFERENCES ${referencedTable.name} (${foreignKey.referencesColumn})"
  }

  private def requireReferencedTable(
      tableById: Map[ShapeId, SqlTable],
      foreignKey: SqlForeignKey
  ): SqlTable =
    tableById.getOrElse(
      foreignKey.referencesShape,
      throw new IllegalStateException(
        s"Missing referenced sql table for shape ${foreignKey.referencesShape.toString}"
      )
    )

  def appendSection(prefix: String, suffix: String): String =
    if (suffix.isEmpty) {
      prefix
    } else if (prefix.isEmpty) {
      suffix
    } else {
      s"$prefix$StatementSeparator$suffix"
    }

  def renderColumnLine(
      name: String,
      sqlType: String,
      nullable: Boolean,
      checks: List[String],
      defaultClause: Option[String] = None
  ): String = {
    val nullableSql = Option.unless(nullable)(" NOT NULL").getOrElse("")
    val defaultSql  = defaultClause.map(value => s" DEFAULT $value").getOrElse("")
    val checkSql    = checks.filter(_.nonEmpty).mkString
    s"$name $sqlType$nullableSql$defaultSql$checkSql"
  }

  def intEnumCheck(columnName: String, values: List[Int]): String =
    s" CHECK($columnName IN (${values.mkString(", ")}))"

  def baseSqlType(columnType: SqlColumnType): Option[String] =
    columnType match {
      case SqlColumnType.Integer           => Some("INTEGER")
      case SqlColumnType.BigInt            => Some("BIGINT")
      case enumType: SqlColumnType.IntEnum =>
        val storage =
          if (enumType.values.forall(value => value >= Int.MinValue && value <= Int.MaxValue)) "INTEGER"
          else "BIGINT"
        Some(storage)
      case _                               => None
    }

  def enumTypeName(shapeId: ShapeId): String =
    SqlText.enumTypeName(shapeId)

  // DESNOTE(jbarber, 2026-06-05): Smithy epoch-seconds allows millisecond fractional seconds;
  //                                                  DECIMAL(13, 3) stores whole seconds plus millis.
  val postgresEpochSecondsSqlType: String = "DECIMAL(13, 3)"

  def postgresEpochSecondsExpression: String =
    "ROUND(EXTRACT(EPOCH FROM CURRENT_TIMESTAMP)::numeric, 3)"
}
