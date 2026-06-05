package com.jacoby6000.smithy.stache.sql.service.shared

import com.jacoby6000.smithy.stache.sql.shared.DDLStatement
import com.jacoby6000.smithy.stache.sql.shared.SqlBindPlaceholder
import com.jacoby6000.smithy.stache.sql.shared.SqlShared
import software.amazon.smithy.model.shapes.ShapeId

/** Formats rendered service queries into migration file text. */
object SqlQueryRenderOutput {
  private val StatementSeparator: String = "\n\n"

  def format(queries: List[SqlRenderedQuery], placeholderStyle: SqlBindPlaceholder): String =
    if (queries.isEmpty) {
      ""
    } else {
      val statements = queries.map(_.formatted(placeholderStyle)).mkString(StatementSeparator)
      s"-- Queries$StatementSeparator$statements"
    }

  def formatWithDdl(
      ddlStatements: List[DDLStatement],
      queries: List[SqlRenderedQuery],
      placeholderStyle: SqlBindPlaceholder
  ): String =
    SqlShared.appendSection(
      SqlShared.formatDdlStatements(ddlStatements),
      format(queries, placeholderStyle)
    )

  def query(queries: List[SqlRenderedQuery], shapeId: ShapeId): Option[SqlRenderedQuery] =
    queries.find(_.shapeId == shapeId)
}
