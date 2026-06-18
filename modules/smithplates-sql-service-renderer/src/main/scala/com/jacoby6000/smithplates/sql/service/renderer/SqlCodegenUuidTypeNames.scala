package com.jacoby6000.smithplates.sql.service.renderer

import com.jacoby6000.smithplates.sql.model.*

object SqlCodegenUuidTypeNames {
  def fromSchema(schema: SqlSchema, shapeIr: SqlShapeIr): Set[String] =
    schema.tables.flatMap { table =>
      shapeIr.structure(table.shapeId).toList.flatMap { structure =>
        structure.members.flatMap { member =>
          table.columns.find(_.name == member.name).collect {
            case column if column.columnType == SqlColumnType.Uuid =>
              member.typeName
          }
        }
      }
    }.toSet
}
