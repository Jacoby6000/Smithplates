package com.jacoby6000.smithplates.sql.service

import com.jacoby6000.smithplates.sql.*
import com.jacoby6000.smithplates.sql.model.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.StructureShape

private[service] object SqlQueryColumnBuilder {
  def queryColumn(
      model: Model,
      tableStructure: StructureShape,
      table: SqlTable,
      tableMember: SqlTableMemberCatalog.TableMemberInfo
  ): SqlQueryColumn = {
    val member       = tableStructure.getMember(tableMember.memberName).get()
    val memberType   = SqlIrTypeNameResolver.resolveMember(model, tableMember.memberName, member)
    val tableColumn  = table.columns.find(_.name == tableMember.columnName)
    val jsonTypeName =
      table.columns
        .find(_.name == tableMember.columnName)
        .collect { case column if column.columnType == SqlColumnType.Json => memberType.typeName }
    SqlQueryColumn(
      memberName = tableMember.memberName,
      columnName = tableMember.columnName,
      typeName = memberType.typeName,
      nullable = tableColumn.exists(_.nullable),
      jsonTypeName = jsonTypeName,
      isStructure = memberType.isStructure,
      structureShapeId = memberType.structureShapeId
    )
  }
}
