package com.jacoby6000.smithplates.sql.service.query.renderer

import com.jacoby6000.smithplates.sql.ddl.renderer.common.SqlShared
import com.jacoby6000.smithplates.sql.model.DDLStatement

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

}
