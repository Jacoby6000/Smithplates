package com.jacoby6000.smithy.stache.sql

import software.amazon.smithy.model.shapes.ShapeId

final case class SqlServiceIr(
    queries: SqlQueries = SqlQueries(),
    services: List[SqlService] = Nil
)

final case class SqlService(
    shapeId: ShapeId,
    version: String,
    operations: List[SqlOperation]
)

final case class SqlOperation(
    shapeId: ShapeId,
    name: String,
    inputShape: ShapeId,
    outputShape: Option[ShapeId],
    errorShapes: List[ShapeId]
)

final case class SqlQueries(
    inserts: List[SqlInsertQuery] = Nil,
    updates: List[SqlUpdateQuery] = Nil,
    deletes: List[SqlDeleteQuery] = Nil,
    selectOnes: List[SqlSelectOneQuery] = Nil,
    selects: List[SqlSelectQuery] = Nil
)

final case class SqlInsertQuery(
    shapeId: ShapeId,
    table: SqlTable,
    columns: List[SqlQueryColumn],
    returningColumns: List[String]
)

final case class SqlUpdateQuery(
    shapeId: ShapeId,
    table: SqlTable,
    setColumns: List[SqlQueryColumn],
    whereColumns: List[SqlQueryColumn],
    returningColumns: List[String]
)

final case class SqlDeleteQuery(
    shapeId: ShapeId,
    table: SqlTable,
    whereColumns: List[SqlQueryColumn],
    returningColumns: List[String]
)

final case class SqlSelectOneQuery(
    shapeId: ShapeId,
    table: SqlTable,
    selectColumns: List[SqlQueryColumn],
    whereColumns: List[SqlQueryColumn]
)

final case class SqlQueryColumn(
    memberName: String,
    columnName: String
)

sealed trait SqlJoinType

object SqlJoinType {
  case object Inner extends SqlJoinType
  case object Left  extends SqlJoinType
  case object Right extends SqlJoinType
  case object Full  extends SqlJoinType
  case object Cross extends SqlJoinType

  def fromString(value: String): Option[SqlJoinType] =
    value.toLowerCase match {
      case "inner" => Some(Inner)
      case "left"  => Some(Left)
      case "right" => Some(Right)
      case "full"  => Some(Full)
      case "cross" => Some(Cross)
      case _       => None
    }
}

final case class SqlQualifiedColumn(
    tableAlias: String,
    columnName: String
)

final case class SqlJoinCondition(
    left: SqlQualifiedColumn,
    right: SqlQualifiedColumn
)

final case class SqlSelectJoin(
    joinType: SqlJoinType,
    table: SqlTable,
    tableAlias: Option[String],
    on: Option[SqlJoinCondition]
)

sealed trait SqlAggregateFunction {
  def sqlName: String
}

object SqlAggregateFunction {
  case object Sum   extends SqlAggregateFunction {
    override val sqlName: String = "SUM"
  }
  case object Count extends SqlAggregateFunction {
    override val sqlName: String = "COUNT"
  }
  case object Max   extends SqlAggregateFunction {
    override val sqlName: String = "MAX"
  }
  case object Min   extends SqlAggregateFunction {
    override val sqlName: String = "MIN"
  }
  case object Avg   extends SqlAggregateFunction {
    override val sqlName: String = "AVG"
  }

  def fromString(value: String): Option[SqlAggregateFunction] =
    value.toLowerCase match {
      case "sum"   => Some(Sum)
      case "count" => Some(Count)
      case "max"   => Some(Max)
      case "min"   => Some(Min)
      case "avg"   => Some(Avg)
      case _       => None
    }
}

sealed trait SqlSelectProjection {
  def resultAlias: String
  def isAggregate: Boolean
  def renderExpression: String
}

final case class SqlSelectColumnProjection(
    resultAlias: String,
    column: SqlQualifiedColumn
) extends SqlSelectProjection {
  override val isAggregate: Boolean = false

  override def renderExpression: String =
    s"${column.tableAlias}.${column.columnName}"
}

final case class SqlSelectAggregateProjection(
    resultAlias: String,
    function: SqlAggregateFunction,
    column: Option[SqlQualifiedColumn]
) extends SqlSelectProjection {
  override val isAggregate: Boolean = true

  override def renderExpression: String =
    (function, column) match {
      case (SqlAggregateFunction.Count, None) => "COUNT(*)"
      case (_, Some(qualifiedColumn))         =>
        s"${function.sqlName}(${qualifiedColumn.tableAlias}.${qualifiedColumn.columnName})"
      case (_, None)                          =>
        throw new IllegalStateException(s"aggregate ${function.sqlName} requires a column")
    }
}

sealed trait SqlComparisonOperator {
  def sqlSymbol: String
}

object SqlComparisonOperator {
  case object Eq extends SqlComparisonOperator {
    override val sqlSymbol: String = "="
  }

  def fromString(value: String): Option[SqlComparisonOperator] =
    value match {
      case "=" | "eq" => Some(Eq)
      case _          => None
    }
}

sealed trait SqlPredicateOperand

object SqlPredicateOperand {
  final case class InputMember(name: String)                   extends SqlPredicateOperand
  final case class TableColumn(column: SqlQualifiedColumn)     extends SqlPredicateOperand
  final case class Projection(projection: SqlSelectProjection) extends SqlPredicateOperand
}

final case class SqlSelectPredicate(
    left: SqlPredicateOperand,
    operator: SqlComparisonOperator,
    right: SqlPredicateOperand
)

final case class SqlSelectGroupBy(
    column: SqlQualifiedColumn
)

sealed trait SqlSortDirection

object SqlSortDirection {
  case object Asc  extends SqlSortDirection
  case object Desc extends SqlSortDirection

  def fromString(value: String): Option[SqlSortDirection] =
    value.toLowerCase match {
      case "asc"  => Some(Asc)
      case "desc" => Some(Desc)
      case _      => None
    }

  def sqlKeyword(direction: SqlSortDirection): String =
    direction match {
      case Asc  => "ASC"
      case Desc => "DESC"
    }
}

final case class SqlSelectOrderBy(
    projectionAlias: String,
    direction: SqlSortDirection
)

final case class SqlSelectQuery(
    shapeId: ShapeId,
    primaryTable: SqlTable,
    primaryTableAlias: Option[String],
    joins: List[SqlSelectJoin],
    selectColumns: List[SqlSelectProjection],
    wherePredicates: List[SqlSelectPredicate],
    groupByColumns: List[SqlSelectGroupBy],
    havingPredicates: List[SqlSelectPredicate],
    orderBy: List[SqlSelectOrderBy] = Nil,
    limitInputMember: Option[String] = None,
    offsetInputMember: Option[String] = None
)
