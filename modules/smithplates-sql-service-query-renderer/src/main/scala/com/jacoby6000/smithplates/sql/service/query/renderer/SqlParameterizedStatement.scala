package com.jacoby6000.smithplates.sql.service.query.renderer

/** SQL with bind parameters implied between consecutive segments. */
final case class SqlParameterizedStatement(segments: List[String]) {
  require(segments.nonEmpty, "parameterized statement requires at least one segment")
}

/** Placeholder pattern used when formatting parameterized SQL for a target driver or export. */
final case class SqlBindPlaceholder(pattern: String) {
  def placeholder(index: Int): String =
    if (pattern.contains(SqlBindPlaceholder.NumberToken)) {
      pattern.replace(SqlBindPlaceholder.NumberToken, index.toString)
    } else {
      pattern
    }
}

object SqlBindPlaceholder {

  /** Substituted with the 1-based bind index (for example `$?{n}` → `$1`). */
  val NumberToken: String = "?{n}"

  def format(segments: List[String], placeholder: SqlBindPlaceholder): String =
    segments.zipWithIndex.map { case (segment, index) =>
      if (index == segments.length - 1) {
        segment
      } else {
        s"$segment${placeholder.placeholder(index + 1)}"
      }
    }.mkString
}

final class SqlQuerySegmentBuilder(var segments: List[String]) {
  def appendText(text: String): Unit =
    if (text.nonEmpty) {
      segments = segments.updated(segments.length - 1, segments.last + text)
    }

  def appendParameter(): Unit =
    segments = segments :+ ""

  def build: SqlParameterizedStatement = SqlParameterizedStatement(segments)
}

object SqlQuerySegmentBuilder {
  def empty: SqlQuerySegmentBuilder = internal.empty

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def empty: SqlQuerySegmentBuilder = new SqlQuerySegmentBuilder(List(""))
  }
}
