package com.jacoby6000.smithplates.sql

import cats.syntax.all.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.StructureShape

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

object SqlShapeIrExtractor {
  def extract(model: Model, rootShapeIds: Iterable[ShapeId]): SqlValidated[SqlShapeIr] = {
    val structureIds =
      rootShapeIds.toList.flatMap(SqlShapeGraph.referencedStructureIds(model, _)).distinct
    val unionIds     =
      SqlShapeGraph.referencedUnionIds(model, rootShapeIds).distinct

    (
      structureIds.traverse(extractStructure(model, _)),
      unionIds.traverse(extractUnion(model, _))
    ).mapN(SqlShapeIr(_, _))
  }

  private def extractStructure(model: Model, shapeId: ShapeId): SqlValidated[SqlStructure] =
    model.getShape(shapeId).toScala.flatMap(_.asStructureShape.toScala) match {
      case None            =>
        InvalidCodegenShape(shapeId, "expected a structure shape").invalidNel
      case Some(structure) =>
        extractStructureMembers(model, structure).map { members =>
          SqlStructure(
            shapeId = shapeId,
            name = shapeId.getName,
            namespace = shapeId.getNamespace,
            members = members
          )
        }
    }

  private def extractUnion(model: Model, shapeId: ShapeId): SqlValidated[SqlUnion] =
    model.getShape(shapeId).toScala.flatMap(_.asUnionShape.toScala) match {
      case None             =>
        InvalidCodegenShape(shapeId, "expected a union shape").invalidNel
      case Some(unionShape) =>
        unionShape.getAllMembers.asScala.toList
          .map { case (memberName, member) =>
            val targetShape = model.expectShape(member.getTarget)
            SqlUnionMember(
              name = memberName,
              typeName = SqlIrTypeNameResolver.resolveTypeName(model, targetShape)
            )
          }
          .validNel
          .map { members =>
            SqlUnion(
              shapeId = shapeId,
              name = shapeId.getName,
              namespace = shapeId.getNamespace,
              members = members
            )
          }
    }

  private def extractStructureMembers(
      model: Model,
      structure: StructureShape
  ): SqlValidated[List[SqlStructureMember]] = {
    val isSqlTable         = structure.sqlTable.isDefined
    val orderedMemberNames =
      if (isSqlTable) {
        com.jacoby6000.smithplates.sql.shared.SqlTableMemberOrdering
          .orderedMembers(structure)
          .map { case (memberName, _) => memberName }
      } else {
        structure.getAllMembers.asScala.keys.toList
      }

    orderedMemberNames
      .traverse { memberName =>
        structure.getMember(memberName).toScala match {
          case None         =>
            InvalidCodegenShape(structure.getId, s"missing structure member '$memberName'").invalidNel
          case Some(member) =>
            toStructureMember(model, memberName, member, isSqlTable).validNel
        }
      }
  }

  private def toStructureMember(
      model: Model,
      memberName: String,
      member: MemberShape,
      isSqlTable: Boolean
  ): SqlStructureMember = {
    val memberType = SqlIrTypeNameResolver.resolveMember(model, memberName, member)
    SqlStructureMember(
      name = memberName,
      typeName = memberType.typeName,
      optional = memberOptional(member, isSqlTable),
      isStructure = memberType.isStructure,
      structureShapeId = memberType.structureShapeId,
      isUnion = memberType.isUnion
    )
  }

  private def memberOptional(member: MemberShape, isSqlTable: Boolean): Boolean =
    if (isSqlTable) {
      !member.isRequired && !member.sqlPrimaryKey && member.autoGeneration.isEmpty
    } else {
      !member.isRequired
    }
}
