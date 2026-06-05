package com.jacoby6000.smithy.stache.sql.codegen

import com.jacoby6000.smithy.stache.sql.SmithySqlTraitAccess
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeId

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

private[codegen] object SqlCodegenTypeResolver {
  val UnitShapeId: ShapeId = ShapeId.from("smithy.api#Unit")

  private val PreludeShapeIds: Set[ShapeId] =
    Set(
      UnitShapeId,
      ShapeId.from("smithy.api#String"),
      ShapeId.from("smithy.api#Integer"),
      ShapeId.from("smithy.api#Long"),
      ShapeId.from("smithy.api#Float"),
      ShapeId.from("smithy.api#Double"),
      ShapeId.from("smithy.api#BigDecimal"),
      ShapeId.from("smithy.api#BigInteger"),
      ShapeId.from("smithy.api#Boolean"),
      ShapeId.from("smithy.api#Blob"),
      ShapeId.from("smithy.api#Timestamp"),
      ShapeId.from("smithy.api#Document")
    )

  def isPreludeShape(shapeId: ShapeId): Boolean =
    PreludeShapeIds.contains(shapeId)

  def isUserDefinedStructure(model: Model, shapeId: ShapeId): Boolean =
    if (isPreludeShape(shapeId)) {
      false
    } else {
      model.getShape(shapeId).toScala.exists(_.isStructureShape)
    }

  def resolveTypeName(model: Model, shape: Shape): String =
    shape match {
      case structure if structure.isStructureShape =>
        structure.asStructureShape.get().getId.getName
      case enumShape if enumShape.isEnumShape      =>
        enumShape.asEnumShape.get().getId.getName
      case intEnum if intEnum.isIntEnumShape       =>
        intEnum.asIntEnumShape.get().getId.getName
      case union if union.isUnionShape             =>
        union.asUnionShape.get().getId.getName
      case listShape if listShape.isListShape      =>
        val memberTarget = listShape.asListShape.get().getMember.getTarget
        s"List[${resolveTypeName(model, model.expectShape(memberTarget))}]"
      case mapShape if mapShape.isMapShape         =>
        val valueTarget = mapShape.asMapShape.get().getValue.getTarget
        s"Map[String, ${resolveTypeName(model, model.expectShape(valueTarget))}]"
      case _                                       =>
        resolvePrimitiveTypeName(shape.toShapeId)
    }

  def resolvePythonTypeName(model: Model, shape: Shape): String =
    shape match {
      case structure if structure.isStructureShape =>
        structure.asStructureShape.get().getId.getName
      case enumShape if enumShape.isEnumShape      =>
        enumShape.asEnumShape.get().getId.getName
      case intEnum if intEnum.isIntEnumShape       =>
        intEnum.asIntEnumShape.get().getId.getName
      case union if union.isUnionShape             =>
        union.asUnionShape.get().getId.getName
      case listShape if listShape.isListShape      =>
        val memberTarget = listShape.asListShape.get().getMember.getTarget
        s"list[${resolvePythonTypeName(model, model.expectShape(memberTarget))}]"
      case mapShape if mapShape.isMapShape         =>
        val valueTarget = mapShape.asMapShape.get().getValue.getTarget
        s"dict[str, ${resolvePythonTypeName(model, model.expectShape(valueTarget))}]"
      case _                                       =>
        resolvePrimitivePythonTypeName(shape.toShapeId)
    }

  def resolveOutputPythonTypeName(shapeId: ShapeId): String =
    if (isPreludeShape(shapeId)) {
      resolvePrimitivePythonTypeName(shapeId)
    } else {
      shapeId.getName
    }

  def resolveMember(
      model: Model,
      memberName: String,
      member: MemberShape,
      role: SqlCodegenMemberRole = SqlCodegenMemberRole.General
  ): SqlCodegenMember = {
    val targetShape      = model.expectShape(member.getTarget)
    val structureShapeId =
      if (targetShape.isStructureShape && !isPreludeShape(targetShape.toShapeId)) {
        Some(targetShape.toShapeId)
      } else {
        None
      }

    SqlCodegenMember(
      name = memberName,
      typeName = resolveTypeName(model, targetShape),
      pythonTypeName = resolvePythonTypeName(model, targetShape),
      optional = isOptionalPythonMember(member, role),
      isStructure = structureShapeId.isDefined,
      structureShapeId = structureShapeId,
      isUnion = targetShape.isUnionShape
    )
  }

  def isOptionalPythonMember(member: MemberShape, role: SqlCodegenMemberRole): Boolean =
    role match {
      case SqlCodegenMemberRole.General     =>
        !member.isRequired
      case SqlCodegenMemberRole.SqlTableRow =>
        !member.isRequired &&
        !SmithySqlTraitAccess.sqlPrimaryKey(member) &&
        SmithySqlTraitAccess.autoGeneration(member).isEmpty
    }

  def referencedStructureIds(model: Model, rootShapeId: ShapeId): List[ShapeId] = {
    val visited = scala.collection.mutable.Set.empty[ShapeId]
    val pending = scala.collection.mutable.Queue.empty[ShapeId]
    if (isUserDefinedStructure(model, rootShapeId)) {
      pending.enqueue(rootShapeId)
    }

    val collected = scala.collection.mutable.ListBuffer.empty[ShapeId]
    while (pending.nonEmpty) {
      val shapeId = pending.dequeue()
      if (!visited.contains(shapeId)) {
        visited += shapeId
        collected += shapeId
        model.getShape(shapeId).toScala.foreach { shape =>
          shape
            .members()
            .asScala
            .foreach { member =>
              memberTargets(model, member).filter(isUserDefinedStructure(model, _)).foreach { referenced =>
                if (!visited.contains(referenced)) {
                  pending.enqueue(referenced)
                }
              }
            }
        }
      }
    }

    collected.toList
  }

  def referencedUnionIds(model: Model, rootShapeIds: Iterable[ShapeId]): List[ShapeId] = {
    val visitedStructures = scala.collection.mutable.Set.empty[ShapeId]
    val visitedUnions     = scala.collection.mutable.Set.empty[ShapeId]
    val pendingStructures = scala.collection.mutable.Queue.empty[ShapeId]
    val collectedUnions   = scala.collection.mutable.ListBuffer.empty[ShapeId]

    rootShapeIds.foreach { shapeId =>
      if (isUserDefinedStructure(model, shapeId)) {
        pendingStructures.enqueue(shapeId)
      }
    }

    while (pendingStructures.nonEmpty) {
      val shapeId = pendingStructures.dequeue()
      if (!visitedStructures.contains(shapeId)) {
        visitedStructures += shapeId
        model.getShape(shapeId).toScala.foreach { shape =>
          shape
            .members()
            .asScala
            .foreach { member =>
              val targetShape = model.expectShape(member.getTarget)
              if (targetShape.isUnionShape) {
                val unionShapeId = targetShape.toShapeId
                if (!visitedUnions.contains(unionShapeId)) {
                  visitedUnions += unionShapeId
                  collectedUnions += unionShapeId
                }
              } else {
                memberTargets(model, member).filter(isUserDefinedStructure(model, _)).foreach { referenced =>
                  if (!visitedStructures.contains(referenced)) {
                    pendingStructures.enqueue(referenced)
                  }
                }
              }
            }
        }
      }
    }

    collectedUnions.toList
  }

  private def memberTargets(model: Model, member: MemberShape): List[ShapeId] = {
    val targetShape = model.expectShape(member.getTarget)
    targetShape match {
      case shape if shape.isStructureShape =>
        List(shape.toShapeId)
      case shape if shape.isListShape      =>
        List(shape.asListShape.get().getMember.getTarget)
      case shape if shape.isMapShape       =>
        List(shape.asMapShape.get().getValue.getTarget)
      case shape if shape.isUnionShape     =>
        shape.asUnionShape.get().getAllMembers.asScala.values.map(_.getTarget).toList
      case _                               =>
        Nil
    }
  }

  private def resolvePrimitiveTypeName(shapeId: ShapeId): String =
    shapeId.getName match {
      case "String"     => "String"
      case "Integer"    => "Integer"
      case "Long"       => "Long"
      case "Float"      => "Float"
      case "Double"     => "Double"
      case "BigDecimal" => "BigDecimal"
      case "BigInteger" => "BigInteger"
      case "Boolean"    => "Boolean"
      case "Blob"       => "Blob"
      case "Timestamp"  => "Timestamp"
      case "Document"   => "Document"
      case "Unit"       => "Unit"
      case other        => other
    }

  def resolvePrimitivePythonTypeName(shapeId: ShapeId): String =
    shapeId.getName match {
      case "String"     => "str"
      case "Integer"    => "int"
      case "Long"       => "int"
      case "Float"      => "float"
      case "Double"     => "float"
      case "BigDecimal" => "Decimal"
      case "BigInteger" => "int"
      case "Boolean"    => "bool"
      case "Blob"       => "bytes"
      case "Timestamp"  => "datetime"
      case "Document"   => "Any"
      case "Unit"       => "None"
      case other        => other
    }
}
