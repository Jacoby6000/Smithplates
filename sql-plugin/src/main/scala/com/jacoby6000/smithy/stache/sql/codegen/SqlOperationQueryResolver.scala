package com.jacoby6000.smithy.stache.sql.codegen

import com.jacoby6000.smithy.stache.sql.SqlDeleteQuery
import com.jacoby6000.smithy.stache.sql.SqlInsertQuery
import com.jacoby6000.smithy.stache.sql.SqlQueries
import com.jacoby6000.smithy.stache.sql.SqlSelectOneQuery
import com.jacoby6000.smithy.stache.sql.SqlSelectQuery
import com.jacoby6000.smithy.stache.sql.SqlUpdateQuery
import software.amazon.smithy.model.shapes.ShapeId

sealed trait ResolvedSqlOperationQuery {
  def shapeId: ShapeId
}

object ResolvedSqlOperationQuery {
  final case class Insert(query: SqlInsertQuery) extends ResolvedSqlOperationQuery {
    override val shapeId: ShapeId = query.shapeId
  }

  final case class Update(query: SqlUpdateQuery) extends ResolvedSqlOperationQuery {
    override val shapeId: ShapeId = query.shapeId
  }

  final case class Delete(query: SqlDeleteQuery) extends ResolvedSqlOperationQuery {
    override val shapeId: ShapeId = query.shapeId
  }

  final case class SelectOne(query: SqlSelectOneQuery) extends ResolvedSqlOperationQuery {
    override val shapeId: ShapeId = query.shapeId
  }

  final case class Select(query: SqlSelectQuery) extends ResolvedSqlOperationQuery {
    override val shapeId: ShapeId = query.shapeId
  }
}

object SqlOperationQueryResolver {
  def resolve(queries: SqlQueries, operationShapeId: ShapeId): Option[ResolvedSqlOperationQuery] =
    queries.inserts
      .find(_.shapeId == operationShapeId)
      .map(ResolvedSqlOperationQuery.Insert(_))
      .orElse(
        queries.updates
          .find(_.shapeId == operationShapeId)
          .map(ResolvedSqlOperationQuery.Update(_))
      )
      .orElse(
        queries.deletes
          .find(_.shapeId == operationShapeId)
          .map(ResolvedSqlOperationQuery.Delete(_))
      )
      .orElse(
        queries.selectOnes
          .find(_.shapeId == operationShapeId)
          .map(ResolvedSqlOperationQuery.SelectOne(_))
      )
      .orElse(
        queries.selects
          .find(_.shapeId == operationShapeId)
          .map(ResolvedSqlOperationQuery.Select(_))
      )
}
