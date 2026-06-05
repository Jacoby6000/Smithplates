package com.jacoby6000.smithy.stache.sql.shared

import com.jacoby6000.smithy.stache.sql.NoSqlTables
import com.jacoby6000.smithy.stache.sql.SqlColumn
import com.jacoby6000.smithy.stache.sql.SqlColumnType
import com.jacoby6000.smithy.stache.sql.SqlSchema
import software.amazon.smithy.model.shapes.ShapeId

/** Shared SQL DDL helpers, rendering, and Smithy enum utilities for dialect plugins. */
object SqlShared {
  private val StatementSeparator: String = "\n\n"

  def requireTables(schema: SqlSchema): Unit =
    if (schema.tables.isEmpty) {
      throw new IllegalStateException(NoSqlTables.message)
    }

  def trimmedNonEmpty(text: String): Option[String] =
    Option(text).filter(_.trim.nonEmpty)

  def trimmedNonEmpty(opt: Option[String]): Option[String] =
    opt.filter(_.trim.nonEmpty)

  def renderSchema(
      schema: SqlSchema,
      renderColumn: SqlColumn => String,
      preTableStatements: SqlSchema => List[DDLStatement] = _ => Nil
  ): String =
    renderDdlStatements(schema, renderColumn, preTableStatements)
      .map(SqlRenderUnit.Ddl(_))
      .map(_.formatted)
      .filter(_.nonEmpty)
      .mkString(StatementSeparator)

  def renderDdlStatements(
      schema: SqlSchema,
      renderColumn: SqlColumn => String,
      preTableStatements: SqlSchema => List[DDLStatement] = _ => Nil
  ): List[DDLStatement] = {
    requireTables(schema)
    val prefix = preTableStatements(schema)
    val tables =
      SqlTableTree
        .tablesInRenderOrder(schema)
        .flatMap { table =>
          val columnLines     = table.columns.map(renderColumn)
          val primaryKeyLine  = s"PRIMARY KEY (${table.primaryKeys.mkString(", ")})"
          val foreignKeyLines = table.foreignKeys.map { foreignKey =>
            val referencedTable = schema.tables.find(_.shapeId == foreignKey.referencesShape).getOrElse {
              throw new IllegalStateException(
                s"Missing referenced sql table for shape ${foreignKey.referencesShape.toString}"
              )
            }
            s"FOREIGN KEY (${foreignKey.column}) REFERENCES ${referencedTable.name} (${foreignKey.referencesColumn})"
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
    prefix ++ tables
  }

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

  def enumTypeName(shapeId: ShapeId): String = {
    val namespace = shapeId.getNamespace.replace('.', '_').replace('-', '_')
    s"${namespace}_${shapeId.getName}".toLowerCase
  }
}
