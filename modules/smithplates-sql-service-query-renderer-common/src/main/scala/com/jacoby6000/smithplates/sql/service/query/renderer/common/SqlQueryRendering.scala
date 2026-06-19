package com.jacoby6000.smithplates.sql.service.query.renderer.common

import cats.data.NonEmptyList
import com.jacoby6000.smithplates.sql.model.SqlColumnType
import com.jacoby6000.smithplates.sql.model.SqlUpdatedTimestamp
import com.jacoby6000.smithplates.sql.service.SqlDeleteQuery
import com.jacoby6000.smithplates.sql.service.SqlInsertQuery
import com.jacoby6000.smithplates.sql.service.SqlJoinType
import com.jacoby6000.smithplates.sql.service.SqlQueries
import com.jacoby6000.smithplates.sql.service.SqlSelectOneQuery
import com.jacoby6000.smithplates.sql.service.SqlSelectQuery
import com.jacoby6000.smithplates.sql.service.SqlSortDirection
import com.jacoby6000.smithplates.sql.service.SqlUpdateQuery
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlQuerySegmentBuilder
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlRenderedQuery

/** Dialect-neutral query rendering with dialect-specific timestamp assignments injected by implementations. */
private[query] object SqlQueryRendering {
  def renderQueryUnits(
      queries: SqlQueries,
      autoUpdatedTimestampAssignment: (String, SqlColumnType) => String
  ): List[SqlRenderedQuery] =
    queries.inserts.map(internal.renderInsertQuery) ++
      queries.updates.map(internal.renderUpdateQuery(autoUpdatedTimestampAssignment)) ++
      queries.deletes.map(internal.renderDeleteQuery) ++
      queries.selectOnes.map(internal.renderSelectOneQuery) ++
      queries.selects.map(internal.renderSelectQuery)

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def renderInsertQuery(query: SqlInsertQuery): SqlRenderedQuery = {
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
      renderOptionalClause(query.returningColumns, builder, " RETURNING ", ", ")(_.columnName)
      builder.appendText(";")
      SqlRenderedQuery(query.shapeId, builder.build)
    }

    def renderUpdateQuery(
        autoUpdatedTimestampAssignment: (String, SqlColumnType) => String
    )(query: SqlUpdateQuery): SqlRenderedQuery = {
      val autoUpdatedColumns =
        query.table.columns
          .filter(_.autoGeneration.contains(SqlUpdatedTimestamp))
          .map(column => autoUpdatedTimestampAssignment(column.name, column.columnType))
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
      renderOptionalClause(query.returningColumns, builder, " RETURNING ", ", ")(_.columnName)
      builder.appendText(";")
      SqlRenderedQuery(query.shapeId, builder.build)
    }

    def renderDeleteQuery(query: SqlDeleteQuery): SqlRenderedQuery = {
      val builder = SqlQuerySegmentBuilder.empty
      builder.appendText(s"DELETE FROM ${query.table.name} WHERE ")
      appendPlaceholderEqualities(builder, query.whereColumns)(_.columnName)
      renderOptionalClause(query.returningColumns, builder, " RETURNING ", ", ")(_.columnName)
      builder.appendText(";")
      SqlRenderedQuery(query.shapeId, builder.build)
    }

    def renderSelectOneQuery(query: SqlSelectOneQuery): SqlRenderedQuery = {
      val builder       = SqlQuerySegmentBuilder.empty
      val projections   =
        query.effectiveProjectedColumns.map { projected =>
          val expression = s"${projected.tableAlias}.${projected.column.columnName}"
          projected.resultAlias match {
            case Some(alias) => s"$expression AS $alias"
            case None        => expression
          }
        }
      builder.appendText(
        s"SELECT ${projections.mkString(", ")}\nFROM ${renderTableReference(query.table.name, query.tableAlias)}"
      )
      query.joins.foreach(join => builder.appendText(renderJoinClause(join)))
      builder.appendText("\nWHERE ")
      val whereTableRef =
        if (query.joins.nonEmpty) {
          query.tableAlias.getOrElse(query.table.name)
        } else {
          query.table.name
        }
      appendPlaceholderEqualities(builder, query.whereColumns) { column =>
        if (query.joins.nonEmpty) {
          s"$whereTableRef.${column.columnName}"
        } else {
          column.columnName
        }
      }
      builder.appendText(";")
      SqlRenderedQuery(query.shapeId, builder.build)
    }

    def renderSelectQuery(query: SqlSelectQuery): SqlRenderedQuery = {
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
      SqlRenderedQuery(query.shapeId, builder.build)
    }

    def renderTableReference(tableName: String, tableAlias: Option[String]): String =
      tableAlias match {
        case Some(alias) => s"$tableName AS $alias"
        case None        => tableName
      }

    def appendComparisonClause(
        builder: SqlQuerySegmentBuilder,
        predicates: List[com.jacoby6000.smithplates.sql.service.SqlSelectPredicate],
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

    def appendPredicateOperand(
        builder: SqlQuerySegmentBuilder,
        operand: com.jacoby6000.smithplates.sql.service.SqlPredicateOperand
    ): Unit =
      operand match {
        case com.jacoby6000.smithplates.sql.service.SqlPredicateOperand.InputMember(_)         =>
          builder.appendParameter()
        case com.jacoby6000.smithplates.sql.service.SqlPredicateOperand.TableColumn(column)    =>
          builder.appendText(s"${column.tableAlias}.${column.columnName}")
        case com.jacoby6000.smithplates.sql.service.SqlPredicateOperand.Projection(projection) =>
          builder.appendText(projection.renderExpression)
      }

    def renderOptionalClause[A](
        items: List[A],
        builder: SqlQuerySegmentBuilder,
        prefix: String,
        separator: String
    )(renderItem: A => String): Unit =
      NonEmptyList.fromList(items).foreach { itemList =>
        builder.appendText(s"$prefix${itemList.toList.map(renderItem).mkString(separator)}")
      }

    def appendPlaceholderEqualities[A](
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

    def renderJoinClause(join: com.jacoby6000.smithplates.sql.service.SqlSelectJoin): String = {
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
}
