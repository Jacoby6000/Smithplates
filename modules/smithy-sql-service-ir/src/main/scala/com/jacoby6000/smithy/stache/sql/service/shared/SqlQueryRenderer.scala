package com.jacoby6000.smithy.stache.sql.service.shared

import cats.data.NonEmptyList
import com.jacoby6000.smithy.stache.sql.PostgresDialect
import com.jacoby6000.smithy.stache.sql.SqlAutoGeneration
import com.jacoby6000.smithy.stache.sql.SqlAutoUuid
import com.jacoby6000.smithy.stache.sql.SqlCreatedTimestamp
import com.jacoby6000.smithy.stache.sql.SqlDialect
import com.jacoby6000.smithy.stache.sql.SqlUpdatedTimestamp
import com.jacoby6000.smithy.stache.sql.SqliteDialect
import com.jacoby6000.smithy.stache.sql.service.SqlDeleteQuery
import com.jacoby6000.smithy.stache.sql.service.SqlInsertQuery
import com.jacoby6000.smithy.stache.sql.service.SqlJoinType
import com.jacoby6000.smithy.stache.sql.service.SqlQueries
import com.jacoby6000.smithy.stache.sql.service.SqlSelectOneQuery
import com.jacoby6000.smithy.stache.sql.service.SqlSelectQuery
import com.jacoby6000.smithy.stache.sql.service.SqlSortDirection
import com.jacoby6000.smithy.stache.sql.service.SqlUpdateQuery
import com.jacoby6000.smithy.stache.sql.shared.SqlBindPlaceholder
import com.jacoby6000.smithy.stache.sql.shared.SqlQuerySegmentBuilder
import com.jacoby6000.smithy.stache.sql.shared.SqlRenderOutput
import com.jacoby6000.smithy.stache.sql.shared.SqlRenderUnit

/** Renders INSERT, UPDATE, and SELECT statements from validated query models. */
object SqlQueryRenderer {
  def renderQueryUnits(queries: SqlQueries): List[SqlRenderUnit.Query] =
    queries.inserts.map(renderInsertQuery) ++
      queries.updates.map(renderUpdateQuery) ++
      queries.deletes.map(renderDeleteQuery) ++
      queries.selectOnes.map(renderSelectOneQuery) ++
      queries.selects.map(renderSelectQuery)

  def renderQueries(queries: SqlQueries, dialect: SqlDialect): String =
    SqlRenderOutput.format(renderQueryUnits(queries), SqlBindPlaceholder.forDialect(dialect))

  def defaultClause(dialect: SqlDialect, autoGeneration: SqlAutoGeneration): String =
    autoGeneration match {
      case SqlAutoUuid                               =>
        dialect match {
          case SqliteDialect   => sqliteAutoUuidDefault
          case PostgresDialect => "gen_random_uuid()"
        }
      case SqlCreatedTimestamp | SqlUpdatedTimestamp =>
        "CURRENT_TIMESTAMP"
    }

  private val sqliteAutoUuidDefault: String =
    "(lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || " +
      "substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || " +
      "substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6))))"

  private def renderInsertQuery(query: SqlInsertQuery): SqlRenderUnit.Query = {
    val builder = SqlQuerySegmentBuilder.empty
    builder.appendText(
      s"INSERT INTO ${query.table.name} (${query.columns.map(_.columnName).mkString(", ")}) VALUES ("
    )
    query.columns.zipWithIndex.foreach { case (_, index) =>
      if (index > 0) {
        builder.appendText(", ")
      }
      builder.appendParameter()
    }
    builder.appendText(")")
    renderOptionalClause(query.returningColumns, builder, " RETURNING ", ", ")(identity)
    builder.appendText(";")
    SqlRenderUnit.Query(query.shapeId, builder.build)
  }

  private def renderUpdateQuery(query: SqlUpdateQuery): SqlRenderUnit.Query = {
    val autoUpdatedColumns =
      query.table.columns
        .filter(_.autoGeneration.contains(SqlUpdatedTimestamp))
        .map(column => s"${column.name} = CURRENT_TIMESTAMP")
    val builder            = SqlQuerySegmentBuilder.empty
    builder.appendText(s"UPDATE ${query.table.name}\nSET ")
    query.setColumns.zipWithIndex.foreach { case (column, index) =>
      if (index > 0) {
        builder.appendText(", ")
      }
      builder.appendText(s"${column.columnName} = ")
      builder.appendParameter()
    }
    if (autoUpdatedColumns.nonEmpty) {
      if (query.setColumns.nonEmpty) {
        builder.appendText(", ")
      }
      builder.appendText(autoUpdatedColumns.mkString(", "))
    }
    builder.appendText("\nWHERE ")
    appendPlaceholderEqualities(builder, query.whereColumns)(_.columnName)
    renderOptionalClause(query.returningColumns, builder, " RETURNING ", ", ")(identity)
    builder.appendText(";")
    SqlRenderUnit.Query(query.shapeId, builder.build)
  }

  private def renderDeleteQuery(query: SqlDeleteQuery): SqlRenderUnit.Query = {
    val builder = SqlQuerySegmentBuilder.empty
    builder.appendText(s"DELETE FROM ${query.table.name} WHERE ")
    appendPlaceholderEqualities(builder, query.whereColumns)(_.columnName)
    renderOptionalClause(query.returningColumns, builder, " RETURNING ", ", ")(identity)
    builder.appendText(";")
    SqlRenderUnit.Query(query.shapeId, builder.build)
  }

  private def renderSelectOneQuery(query: SqlSelectOneQuery): SqlRenderUnit.Query = {
    val builder = SqlQuerySegmentBuilder.empty
    builder.appendText(
      s"SELECT ${query.selectColumns.map(_.columnName).mkString(", ")} FROM ${query.table.name} WHERE "
    )
    appendPlaceholderEqualities(builder, query.whereColumns)(_.columnName)
    builder.appendText(";")
    SqlRenderUnit.Query(query.shapeId, builder.build)
  }

  private def renderSelectQuery(query: SqlSelectQuery): SqlRenderUnit.Query = {
    val projections =
      query.selectColumns.map { projection =>
        val expression = projection.renderExpression
        if (projection.resultAlias == expression) {
          expression
        } else {
          s"$expression AS ${projection.resultAlias}"
        }
      }
    val builder     = SqlQuerySegmentBuilder.empty
    builder.appendText(
      s"SELECT ${projections.mkString(", ")}\nFROM ${renderTableReference(query.primaryTable.name, query.primaryTableAlias)}"
    )
    query.joins.foreach(join => builder.appendText(renderJoinClause(join)))
    appendComparisonClause(builder, query.wherePredicates, "WHERE")
    renderOptionalClause(query.groupByColumns, builder, "\nGROUP BY ", ", ") { column =>
      s"${column.column.tableAlias}.${column.column.columnName}"
    }
    appendComparisonClause(builder, query.havingPredicates, "HAVING")
    renderOptionalClause(query.orderBy, builder, "\nORDER BY ", ", ") { order =>
      val expression =
        query.selectColumns
          .find(_.resultAlias == order.projectionAlias)
          .map(_.renderExpression)
          .getOrElse(order.projectionAlias)
      s"$expression ${SqlSortDirection.sqlKeyword(order.direction)}"
    }
    query.limitInputMember.foreach { _ =>
      builder.appendText("\nLIMIT ")
      builder.appendParameter()
    }
    query.offsetInputMember.foreach { _ =>
      builder.appendText(" OFFSET ")
      builder.appendParameter()
    }
    builder.appendText(";")
    SqlRenderUnit.Query(query.shapeId, builder.build)
  }

  private def renderTableReference(tableName: String, tableAlias: Option[String]): String =
    tableAlias match {
      case Some(alias) => s"$tableName AS $alias"
      case None        => tableName
    }

  private def appendComparisonClause(
      builder: SqlQuerySegmentBuilder,
      predicates: List[com.jacoby6000.smithy.stache.sql.service.SqlSelectPredicate],
      keyword: String,
      prefix: String = "\n"
  ): Unit =
    NonEmptyList.fromList(predicates).foreach { predicateList =>
      builder.appendText(s"$prefix$keyword ")
      predicateList.toList.zipWithIndex.foreach { case (predicate, index) =>
        if (index > 0) {
          builder.appendText(" AND ")
        }
        appendPredicateOperand(builder, predicate.left)
        builder.appendText(s" ${predicate.operator.sqlSymbol} ")
        appendPredicateOperand(builder, predicate.right)
      }
    }

  private def appendPredicateOperand(
      builder: SqlQuerySegmentBuilder,
      operand: com.jacoby6000.smithy.stache.sql.service.SqlPredicateOperand
  ): Unit =
    operand match {
      case com.jacoby6000.smithy.stache.sql.service.SqlPredicateOperand.InputMember(_)         =>
        builder.appendParameter()
      case com.jacoby6000.smithy.stache.sql.service.SqlPredicateOperand.TableColumn(column)    =>
        builder.appendText(s"${column.tableAlias}.${column.columnName}")
      case com.jacoby6000.smithy.stache.sql.service.SqlPredicateOperand.Projection(projection) =>
        builder.appendText(projection.renderExpression)
    }

  private def renderOptionalClause[A](
      items: List[A],
      builder: SqlQuerySegmentBuilder,
      prefix: String,
      separator: String
  )(renderItem: A => String): Unit =
    NonEmptyList.fromList(items).foreach { itemList =>
      builder.appendText(s"$prefix${itemList.toList.map(renderItem).mkString(separator)}")
    }

  private def appendPlaceholderEqualities[A](
      builder: SqlQuerySegmentBuilder,
      items: List[A]
  )(leftSide: A => String): Unit =
    NonEmptyList.fromList(items).foreach { itemList =>
      itemList.toList.zipWithIndex.foreach { case (item, index) =>
        if (index > 0) {
          builder.appendText(" AND ")
        }
        builder.appendText(s"${leftSide(item)} = ")
        builder.appendParameter()
      }
    }

  private def renderJoinClause(join: com.jacoby6000.smithy.stache.sql.service.SqlSelectJoin): String = {
    val joinKeyword =
      join.joinType match {
        case SqlJoinType.Inner => "INNER JOIN"
        case SqlJoinType.Left  => "LEFT JOIN"
        case SqlJoinType.Right => "RIGHT JOIN"
        case SqlJoinType.Full  => "FULL OUTER JOIN"
        case SqlJoinType.Cross => "CROSS JOIN"
      }
    val joinedTable = renderTableReference(join.table.name, join.tableAlias)
    join.on match {
      case Some(condition) =>
        s"\n$joinKeyword $joinedTable ON ${condition.left.tableAlias}.${condition.left.columnName} = ${condition.right.tableAlias}.${condition.right.columnName}"
      case None            =>
        s"\n$joinKeyword $joinedTable"
    }
  }
}
