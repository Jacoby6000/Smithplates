package com.jacoby6000.smithplates.sql

import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeId

object SqlIrTypeNameResolver {
  final case class MemberType(
      typeName: String,
      isStructure: Boolean,
      structureShapeId: Option[ShapeId],
      isUnion: Boolean
  )

  private val PreludeShapeIds: Set[ShapeId] =
    Set(
      ShapeId.from("smithy.api#Unit"),
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

  def resolveShapeTypeName(model: Model, shapeId: ShapeId): String =
    if (isPreludeShape(shapeId)) {
      resolvePrimitiveTypeName(shapeId)
    } else {
      resolveTypeName(model, model.expectShape(shapeId))
    }

  def resolveMember(model: Model, memberName: String, member: MemberShape): MemberType = {
    val targetShape      = model.expectShape(member.getTarget)
    val structureShapeId =
      if (targetShape.isStructureShape && !isPreludeShape(targetShape.toShapeId)) {
        Some(targetShape.toShapeId)
      } else {
        None
      }

    MemberType(
      typeName = resolveTypeName(model, targetShape),
      isStructure = structureShapeId.isDefined,
      structureShapeId = structureShapeId,
      isUnion = targetShape.isUnionShape
    )
  }

  def resolvePrimitiveTypeName(shapeId: ShapeId): String =
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
}
