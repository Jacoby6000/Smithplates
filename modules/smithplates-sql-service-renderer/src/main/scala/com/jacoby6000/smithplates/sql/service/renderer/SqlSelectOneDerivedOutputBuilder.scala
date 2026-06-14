package com.jacoby6000.smithplates.sql.service.renderer

import com.jacoby6000.smithplates.sql.*
import com.jacoby6000.smithplates.sql.model.*
import com.jacoby6000.smithplates.sql.service.*
import software.amazon.smithy.model.shapes.ShapeId

object SqlSelectOneDerivedOutputBuilder {
  val DerivedNamespace: String = "generated.selectOne"

  final case class DerivedOutput(
      typeName: String,
      structure: SqlStructure,
      binding: SqlCodegenSelectOneOutputBinding
  )

  def build(
      operationName: String,
      query: SqlSelectOneQuery
  ): DerivedOutput = {
    val typeName       = s"${operationName}Result"
    val primaryMembers =
      query.selectColumns.map { column =>
        SqlStructureMember(
          name = column.memberName,
          typeName = column.typeName,
          optional = false,
          isStructure = column.isStructure,
          structureShapeId = column.structureShapeId
        )
      }
    val nestedMembers  =
      query.nestedResults.map { nested =>
        val memberTypeName =
          nested.cardinality match {
            case SqlSelectOneNestedCardinality.Singular   =>
              nested.shapeId.getName
            case SqlSelectOneNestedCardinality.Collection =>
              s"List[${nested.shapeId.getName}]"
          }
        SqlStructureMember(
          name = nested.memberName,
          typeName = memberTypeName,
          optional = nested.optional,
          isStructure = nested.cardinality == SqlSelectOneNestedCardinality.Singular,
          structureShapeId = if (nested.cardinality == SqlSelectOneNestedCardinality.Singular) {
            Some(nested.shapeId)
          } else {
            None
          }
        )
      }
    val structure      =
      SqlStructure(
        shapeId = ShapeId.from(s"generated.selectOne#$typeName"),
        name = typeName,
        namespace = DerivedNamespace,
        members = primaryMembers ++ nestedMembers
      )
    val primaryFields  =
      query.effectiveProjectedColumns.take(primaryMembers.size).zipWithIndex.map { case (projected, index) =>
        SqlCodegenResultField(
          fieldName = projected.column.memberName,
          columnName = projected.resultAlias.getOrElse(projected.column.columnName),
          columnIndex = index,
          typeName = projected.column.typeName,
          readTypeName = rowReadTypeName(query.table, projected.column.columnName, projected.column.typeName),
          isJson = isJsonColumn(query.table, projected.column.columnName),
          timestampFormat = timestampFormat(query.table, projected.column.columnName)
        )
      }
    val nestedBindings =
      query.nestedResults.map { nested =>
        val startIndex =
          query.effectiveProjectedColumns.indexWhere { projected =>
            projected.tableAlias == nested.tableAlias &&
            projected.column.memberName == nested.columns.headOption.map(_.memberName).getOrElse("")
          }
        val fields     =
          nested.columns.zipWithIndex.map { case (column, offset) =>
            val projected = query.effectiveProjectedColumns(startIndex + offset)
            SqlCodegenResultField(
              fieldName = column.memberName,
              columnName = projected.resultAlias.getOrElse(projected.column.columnName),
              columnIndex = startIndex + offset,
              typeName = column.typeName,
              readTypeName = rowReadTypeName(nested.table, column.columnName, column.typeName),
              isJson = isJsonColumn(nested.table, column.columnName),
              timestampFormat = timestampFormat(nested.table, column.columnName)
            )
          }
        SqlCodegenSelectOneNestedBinding(
          memberName = nested.memberName,
          shapeName = nested.shapeId.getName,
          cardinality = nested.cardinality,
          optional = nested.optional,
          fields = fields
        )
      }
    DerivedOutput(
      typeName = typeName,
      structure = structure,
      binding = SqlCodegenSelectOneOutputBinding(
        primaryFields = primaryFields,
        nestedBindings = nestedBindings,
        hasCollectionJoin = query.nestedResults.exists(_.cardinality == SqlSelectOneNestedCardinality.Collection)
      )
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
