package com.jacoby6000.smithplates.sql.service.query.renderer
import com.jacoby6000.smithplates.sql.SqlBindPlaceholder
import com.jacoby6000.smithplates.sql.SqlParameterizedStatement
import software.amazon.smithy.model.shapes.ShapeId

/** One rendered DML query keyed by the Smithy operation shape id. */
final case class SqlRenderedQuery(shapeId: ShapeId, statement: SqlParameterizedStatement) {
  def formatted(placeholderStyle: SqlBindPlaceholder): String =
    s"-- ${shapeId.toString}\n${SqlBindPlaceholder.format(statement.segments, placeholderStyle)}"
}
