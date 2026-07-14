package com.jacoby6000.smithplates.sql.service.query.renderer
import com.jacoby6000.smithplates.sql.SqlBindPlaceholder
import com.jacoby6000.smithplates.sql.ddl.renderer.common.SqlShared
import com.jacoby6000.smithplates.sql.model.DDLStatement

/** Formats rendered service queries into migration file text. */
object SqlQueryRenderOutput {
  def format(queries: List[SqlRenderedQuery], placeholderStyle: SqlBindPlaceholder): String =
    if (queries.isEmpty) {
      ""
    } else {
      val statements = queries.map(_.formatted(placeholderStyle)).mkString(internal.StatementSeparator)
      s"-- Queries${internal.StatementSeparator}$statements"
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

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    val StatementSeparator: String = "\n\n"
  }
}
