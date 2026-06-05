package com.jacoby6000.smithy.stache.sql.shared

import software.amazon.smithy.model.shapes.ShapeId

/** One emitted SQL artifact from schema rendering (DDL statement or DML query). */
sealed trait SqlRenderUnit

object SqlRenderUnit {
  final case class Ddl(ddl: DDLStatement) extends SqlRenderUnit {
    def shapeId: ShapeId  = ddl.shapeId
    def statement: String = ddl.statement
    def formatted: String = s"-- ${shapeId.toString}\n$statement"
  }

  final case class Query(shapeId: ShapeId, statement: SqlParameterizedStatement) extends SqlRenderUnit {
    def formatted(placeholderStyle: SqlBindPlaceholder): String =
      s"-- ${shapeId.toString}\n${SqlBindPlaceholder.format(statement.segments, placeholderStyle)}"
  }
}
