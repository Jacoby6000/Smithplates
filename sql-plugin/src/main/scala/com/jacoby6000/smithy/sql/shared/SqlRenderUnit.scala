package com.jacoby6000.smithy.sql.shared

import software.amazon.smithy.model.shapes.ShapeId

/** One emitted SQL artifact from schema rendering (DDL statement or DML query). */
sealed trait SqlRenderUnit

object SqlRenderUnit {
  final case class Ddl(shapeId: ShapeId, statement: String) extends SqlRenderUnit {
    def formatted: String = s"-- ${shapeId.toString}\n$statement"
  }

  final case class Query(shapeId: ShapeId, statement: SqlParameterizedStatement) extends SqlRenderUnit {
    def formatted(placeholderStyle: SqlBindPlaceholder): String =
      s"-- ${shapeId.toString}\n${SqlBindPlaceholder.format(statement.segments, placeholderStyle)}"
  }
}
