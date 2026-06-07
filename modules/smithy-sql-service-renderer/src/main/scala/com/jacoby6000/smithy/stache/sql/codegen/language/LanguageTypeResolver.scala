package com.jacoby6000.smithy.stache.sql.codegen.language

import com.jacoby6000.smithy.stache.sql.codegen.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeId

object LanguageTypeResolver {
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

  def resolveMember(
      model: Model,
      memberName: String,
      member: MemberShape,
      mapper: LanguageTypeMapper,
      role: SqlCodegenMemberRole = SqlCodegenMemberRole.General
  ): SqlCodegenMember = {
    val targetShape      = model.expectShape(member.getTarget)
    val structureShapeId =
      if (targetShape.isStructureShape && !SqlCodegenShapeGraph.isPreludeShape(targetShape.toShapeId)) {
        Some(targetShape.toShapeId)
      } else {
        None
      }

    SqlCodegenMember(
      name = memberName,
      typeName = resolveTypeName(model, targetShape),
      languageTypeName = mapper.resolveLanguageTypeName(model, targetShape),
      optional = mapper.isOptionalMember(member, role),
      isStructure = structureShapeId.isDefined,
      structureShapeId = structureShapeId,
      isUnion = targetShape.isUnionShape
    )
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
}
