package com.jacoby6000.smithplates.sql.service.renderer

import com.jacoby6000.smithplates.sql.model.*

object SqlCodegenUuidTypeNames {
  def fromSchema(schema: SqlSchema, shapeIr: SqlShapeIr): Set[String] =
    schema.tables.flatMap { table =>
      shapeIr.structure(table.shapeId).toList.flatMap { structure =>
        structure.members.flatMap { member =>
          table.columns
            .find(_.name == member.name)
            .collect {
              case column if column.columnType == SqlColumnType.Uuid =>
                member.typeName
            }
            .filterNot(internal.builtinTypeNames.contains)
        }
      }
    }.toSet

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {

    /** Smithy prelude shape names that already map onto Python builtins via the template `pythonTypeName` helper. These
      * must never receive a generated `<name> = str` alias, otherwise we would emit nonsense such as `String = str` for
      * `@sqlAutoUuid` columns typed directly as `String`.
      */
    val builtinTypeNames: Set[String] =
      Set(
        "String",
        "Integer",
        "Long",
        "BigInteger",
        "Float",
        "Double",
        "BigDecimal",
        "Boolean",
        "Blob",
        "Timestamp",
        "Document",
        "Unit"
      )
  }
}
