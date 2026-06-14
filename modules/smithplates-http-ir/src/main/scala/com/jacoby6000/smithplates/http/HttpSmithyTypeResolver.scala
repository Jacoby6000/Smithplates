package com.jacoby6000.smithplates.http

import cats.syntax.all.*
import com.jacoby6000.smithplates.http.model.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeId

import scala.jdk.OptionConverters.*

object HttpSmithyTypeResolver {
  def resolveMemberType(
      model: Model,
      serviceShape: ShapeId,
      operationName: String,
      memberName: String,
      member: MemberShape
  ): HttpValidated[HttpMemberType] =
    model.getShape(member.getTarget).toScala match {
      case None              =>
        InvalidHttpOperation(
          serviceShape,
          operationName,
          s"input member '$memberName' targets undefined shape '${member.getTarget.toString}'"
        ).invalidNel
      case Some(targetShape) =>
        resolveShapeType(model, serviceShape, operationName, memberName, member, targetShape)
    }

  private def resolveShapeType(
      model: Model,
      serviceShape: ShapeId,
      operationName: String,
      memberName: String,
      member: MemberShape,
      shape: Shape
  ): HttpValidated[HttpMemberType] =
    if (shape.getId == SmithyHttpTimestampFormatResolver.TimestampShapeId) {
      SmithyHttpTimestampFormatResolver
        .resolve(model, serviceShape, operationName, memberName, member)
        .map { timestampFormat =>
          HttpMemberType(
            typeName = "Timestamp",
            timestampFormat = Some(timestampFormat)
          )
        }
    } else if (shape.isListShape) {
      val elementTarget = shape.asListShape.get().getMember.getTarget
      val elementShape  = model.expectShape(elementTarget)
      resolveShapeType(model, serviceShape, operationName, memberName, member, elementShape).map { elementType =>
        HttpMemberType(
          typeName = s"List[${elementType.typeName}]",
          timestampFormat = None
        )
      }
    } else if (shape.isMapShape) {
      val valueTarget = shape.asMapShape.get().getValue.getTarget
      val valueShape  = model.expectShape(valueTarget)
      resolveShapeType(model, serviceShape, operationName, memberName, member, valueShape).map { valueType =>
        HttpMemberType(
          typeName = s"Map[String, ${valueType.typeName}]",
          timestampFormat = None
        )
      }
    } else if (shape.isStructureShape && !isPreludeShape(shape.getId)) {
      HttpMemberType(
        typeName = shape.getId.getName,
        timestampFormat = None
      ).validNel
    } else if (shape.isEnumShape || shape.isIntEnumShape || shape.isUnionShape) {
      HttpMemberType(
        typeName = shape.getId.getName,
        timestampFormat = None
      ).validNel
    } else {
      HttpMemberType(
        typeName = primitiveTypeName(shape.getId),
        timestampFormat = None
      ).validNel
    }

  def isPreludeShape(shapeId: ShapeId): Boolean =
    shapeId.getNamespace == "smithy.api"

  private def primitiveTypeName(shapeId: ShapeId): String =
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

  val primitiveTypeNames: Set[String] =
    Set(
      "String",
      "Integer",
      "Long",
      "Float",
      "Double",
      "BigDecimal",
      "BigInteger",
      "Boolean",
      "Blob",
      "Timestamp",
      "Document",
      "Unit"
    )

  def isStructureTypeName(typeName: String): Boolean =
    !primitiveTypeNames.contains(typeName) &&
      !typeName.startsWith("List[") &&
      !typeName.startsWith("Map[String, ")
}

final case class HttpMemberType(
    typeName: String,
    timestampFormat: Option[HttpTimestampFormat]
)
