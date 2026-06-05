package com.jacoby6000.smithy.stache.sql.codegen

import cats.syntax.all.*
import com.jacoby6000.smithy.stache.sql.*
import com.jacoby6000.smithy.stache.sql.codegen.python.SqlCodegenTypeResolver
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.StructureShape

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

object SqlShapeCodegenExtractor {
  def extractStructure(model: Model, shapeId: ShapeId): SqlValidated[SqlCodegenStructure] =
    model.getShape(shapeId).toScala.flatMap(_.asStructureShape.toScala) match {
      case None            =>
        InvalidCodegenShape(shapeId, "expected a structure shape").invalidNel
      case Some(structure) =>
        extractStructureMembers(model, structure).map { members =>
          SqlCodegenStructure(
            shapeId = shapeId,
            name = shapeId.getName,
            namespace = shapeId.getNamespace,
            members = members
          )
        }
    }

  def collectStructures(model: Model, rootShapeIds: Iterable[ShapeId]): SqlValidated[List[SqlCodegenStructure]] = {
    val orderedShapeIds =
      rootShapeIds.toList.flatMap(SqlCodegenTypeResolver.referencedStructureIds(model, _)).distinct
    orderedShapeIds.traverse(extractStructure(model, _))
  }

  def collectUnions(model: Model, rootShapeIds: Iterable[ShapeId]): SqlValidated[List[SqlCodegenUnion]] = {
    val orderedShapeIds =
      SqlCodegenTypeResolver.referencedUnionIds(model, rootShapeIds).distinct
    orderedShapeIds.traverse(extractUnion(model, _))
  }

  def extractUnion(model: Model, shapeId: ShapeId): SqlValidated[SqlCodegenUnion] =
    model.getShape(shapeId).toScala.flatMap(_.asUnionShape.toScala) match {
      case None             =>
        InvalidCodegenShape(shapeId, "expected a union shape").invalidNel
      case Some(unionShape) =>
        unionShape.getAllMembers.asScala.toList
          .map { case (memberName, member) =>
            val targetShape = model.expectShape(member.getTarget)
            SqlCodegenUnionMember(
              name = memberName,
              variantClassName = SqlCodegenNaming.unionVariantClassName(shapeId.getName, memberName),
              pythonTypeName = SqlCodegenTypeResolver.resolvePythonTypeName(model, targetShape)
            )
          }
          .validNel
          .map { members =>
            SqlCodegenUnion(
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
  ): SqlValidated[List[SqlCodegenMember]] = {
    val memberRole =
      if (structure.sqlTable.isDefined) {
        SqlCodegenMemberRole.SqlTableRow
      } else {
        SqlCodegenMemberRole.General
      }

    structure.getAllMembers.asScala.toList
      .map { case (memberName, member) =>
        SqlCodegenTypeResolver.resolveMember(model, memberName, member, memberRole)
      }
      .validNel
      .map { members =>
        val orderedMembers =
          if (memberRole == SqlCodegenMemberRole.SqlTableRow) {
            com.jacoby6000.smithy.stache.sql.shared.SqlTableMemberOrdering
              .orderedMembers(structure)
              .map { case (memberName, _) => memberName }
          } else {
            Nil
          }

        val membersByName = members.map(member => member.name -> member).toMap
        val sortedMembers =
          if (orderedMembers.nonEmpty) {
            orderedMembers.flatMap(membersByName.get)
          } else {
            members
          }

        sortedMembers
      }
  }
}
