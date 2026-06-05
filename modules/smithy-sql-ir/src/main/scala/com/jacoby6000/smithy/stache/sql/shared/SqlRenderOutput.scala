package com.jacoby6000.smithy.stache.sql.shared

import software.amazon.smithy.model.shapes.ShapeId

/** Formats structured render units into the SQL file text consumed by the build plugin. */
object SqlRenderOutput {
  private val StatementSeparator: String = "\n\n"

  def format(units: List[SqlRenderUnit], placeholderStyle: SqlBindPlaceholder): String = {
    val (ddlUnits, queryUnits) = units.partition {
      case _: SqlRenderUnit.Ddl   => true
      case _: SqlRenderUnit.Query => false
    }
    val ddlText                =
      ddlUnits.collect { case ddl: SqlRenderUnit.Ddl => ddl.formatted }.filter(_.nonEmpty).mkString(StatementSeparator)
    val queryText              =
      if (queryUnits.isEmpty) {
        ""
      } else {
        val statements =
          queryUnits
            .collect { case query: SqlRenderUnit.Query => query.formatted(placeholderStyle) }
            .mkString(
              StatementSeparator
            )
        s"-- Queries$StatementSeparator$statements"
      }
    SqlShared.appendSection(ddlText, queryText)
  }

  def ddlUnits(units: List[SqlRenderUnit]): List[SqlRenderUnit.Ddl] =
    units.collect { case ddl: SqlRenderUnit.Ddl => ddl }

  def queryUnits(units: List[SqlRenderUnit]): List[SqlRenderUnit.Query] =
    units.collect { case query: SqlRenderUnit.Query => query }

  def queryUnit(units: List[SqlRenderUnit], shapeId: ShapeId): Option[SqlRenderUnit.Query] =
    queryUnits(units).find(_.shapeId == shapeId)

  def ddlUnit(units: List[SqlRenderUnit], shapeId: ShapeId): Option[SqlRenderUnit.Ddl] =
    ddlUnits(units).find(_.shapeId == shapeId)

  def ddlStatements(units: List[SqlRenderUnit]): List[DDLStatement] =
    ddlUnits(units).map(_.ddl)
}
