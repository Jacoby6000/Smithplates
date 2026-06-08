package com.jacoby6000.smithy.stache.sql.codegen

import com.jacoby6000.smithy.stache.sql.*

object SqlShapeCodegenMapper {
  def fromIr(shapeIr: SqlShapeIr): (List[SqlCodegenStructure], List[SqlCodegenUnion]) =
    (
      shapeIr.structures.map(toCodegenStructure),
      shapeIr.unions.map(toCodegenUnion)
    )

  private def toCodegenStructure(structure: SqlStructure): SqlCodegenStructure =
    SqlCodegenStructure(
      shapeId = structure.shapeId,
      name = structure.name,
      namespace = structure.namespace,
      members = structure.members.map(toCodegenMember)
    )

  private def toCodegenMember(member: SqlStructureMember): SqlCodegenMember =
    SqlCodegenMember(
      name = member.name,
      typeName = member.typeName,
      optional = member.optional,
      isStructure = member.isStructure,
      structureShapeId = member.structureShapeId,
      isUnion = member.isUnion
    )

  private def toCodegenUnion(union: SqlUnion): SqlCodegenUnion =
    SqlCodegenUnion(
      shapeId = union.shapeId,
      name = union.name,
      namespace = union.namespace,
      members = union.members.map { member =>
        SqlCodegenUnionMember(
          name = member.name,
          variantClassName = SqlCodegenNaming.unionVariantClassName(union.name, member.name),
          typeName = member.typeName
        )
      }
    )
}
