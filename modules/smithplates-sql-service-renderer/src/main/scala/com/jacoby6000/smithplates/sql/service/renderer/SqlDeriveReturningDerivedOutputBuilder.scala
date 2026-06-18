package com.jacoby6000.smithplates.sql.service.renderer

import com.jacoby6000.smithplates.sql.model.*
import com.jacoby6000.smithplates.sql.service.SqlQueryColumn
import software.amazon.smithy.model.shapes.ShapeId

object SqlDeriveReturningDerivedOutputBuilder {
  val DerivedNamespace: String = "generated.dmlReturning"

  final case class DerivedOutput(
      typeName: String,
      structure: SqlStructure,
      resultFields: List[SqlCodegenResultField]
  )

  def build(
      operationName: String,
      table: SqlTable,
      returningColumns: List[SqlQueryColumn]
  ): DerivedOutput = {
    val typeName  = s"${operationName}Result"
    val members   =
      returningColumns.map { column =>
        SqlStructureMember(
          name = column.memberName,
          typeName = column.typeName,
          optional = column.nullable,
          isStructure = column.isStructure,
          structureShapeId = column.structureShapeId
        )
      }
    val structure =
      SqlStructure(
        shapeId = ShapeId.from(s"generated.dmlReturning#$typeName"),
        name = typeName,
        namespace = DerivedNamespace,
        members = members
      )
    val fields    =
      returningColumns.zipWithIndex.map { case (column, index) =>
        SqlCodegenResultField(
          fieldName = column.memberName,
          columnName = column.columnName,
          columnIndex = index,
          typeName = column.typeName,
          readTypeName = rowReadTypeName(table, column.columnName, column.typeName),
          isJson = isJsonColumn(table, column.columnName),
          timestampFormat = timestampFormat(table, column.columnName)
        )
      }
    DerivedOutput(
      typeName = typeName,
      structure = structure,
      resultFields = fields
    )
  }

  private def isJsonColumn(table: SqlTable, columnName: String): Boolean =
    table.columns.find(_.name == columnName).exists(_.columnType == SqlColumnType.Json)

  private def timestampFormat(table: SqlTable, columnName: String): Option[SqlTimestampFormat] =
    table.columns
      .find(_.name == columnName)
      .collect {
        case column if column.columnType.isInstanceOf[SqlColumnType.Timestamp] =>
          column.columnType.asInstanceOf[SqlColumnType.Timestamp].format
      }

  private def rowReadTypeName(table: SqlTable, columnName: String, typeName: String): String =
    table.columns.find(_.name == columnName).map(_.columnType) match {
      case Some(_: SqlColumnType.StringEnum) => "String"
      case Some(_: SqlColumnType.IntEnum)    => "Integer"
      case _                                 => typeName
    }
}
