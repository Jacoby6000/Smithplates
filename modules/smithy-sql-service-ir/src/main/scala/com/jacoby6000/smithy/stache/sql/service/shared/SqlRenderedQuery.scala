package com.jacoby6000.smithy.stache.sql.service.shared

import com.jacoby6000.smithy.stache.sql.shared.SqlBindPlaceholder
import com.jacoby6000.smithy.stache.sql.shared.SqlParameterizedStatement
import software.amazon.smithy.model.shapes.ShapeId

/** One rendered DML query keyed by the Smithy operation shape id. */
final case class SqlRenderedQuery(shapeId: ShapeId, statement: SqlParameterizedStatement) {
  def formatted(placeholderStyle: SqlBindPlaceholder): String =
    s"-- ${shapeId.toString}\n${SqlBindPlaceholder.format(statement.segments, placeholderStyle)}"
}
