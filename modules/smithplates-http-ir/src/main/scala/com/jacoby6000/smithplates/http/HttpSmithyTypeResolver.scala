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
            smithyTypeName = "Timestamp",
            pythonTypeName = pythonTypeForTimestamp(timestampFormat),
            timestampFormat = Some(timestampFormat)
          )
        }
    } else if (shape.isListShape) {
      val elementTarget = shape.asListShape.get().getMember.getTarget
      val elementShape  = model.expectShape(elementTarget)
      resolveShapeType(model, serviceShape, operationName, memberName, member, elementShape).map { elementType =>
        HttpMemberType(
          smithyTypeName = s"List[${elementType.smithyTypeName}]",
          pythonTypeName = s"list[${elementType.pythonTypeName}]",
          timestampFormat = None
        )
      }
    } else if (shape.isMapShape) {
      val valueTarget = shape.asMapShape.get().getValue.getTarget
      val valueShape  = model.expectShape(valueTarget)
      resolveShapeType(model, serviceShape, operationName, memberName, member, valueShape).map { valueType =>
        HttpMemberType(
          smithyTypeName = s"Map[String, ${valueType.smithyTypeName}]",
          pythonTypeName = s"dict[str, ${valueType.pythonTypeName}]",
          timestampFormat = None
        )
      }
    } else if (shape.isStructureShape && !isPreludeShape(shape.getId)) {
      HttpMemberType(
        smithyTypeName = shape.getId.getName,
        pythonTypeName = shape.getId.getName,
        timestampFormat = None
      ).validNel
    } else if (shape.isEnumShape || shape.isIntEnumShape || shape.isUnionShape) {
      HttpMemberType(
        smithyTypeName = shape.getId.getName,
        pythonTypeName = shape.getId.getName,
        timestampFormat = None
      ).validNel
    } else {
      HttpMemberType(
        smithyTypeName = primitiveSmithyTypeName(shape.getId),
        pythonTypeName = primitivePythonTypeName(shape.getId),
        timestampFormat = None
      ).validNel
    }

  private def isPreludeShape(shapeId: ShapeId): Boolean =
    shapeId.getNamespace == "smithy.api"

  private def primitiveSmithyTypeName(shapeId: ShapeId): String =
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

  private def primitivePythonTypeName(shapeId: ShapeId): String =
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

  private def pythonTypeForTimestamp(format: HttpTimestampFormat): String =
    format match {
      case HttpTimestampFormat.DateTime     => "datetime"
      case HttpTimestampFormat.EpochSeconds => "float"
      case HttpTimestampFormat.HttpDate     => "str"
    }
}

final case class HttpMemberType(
    smithyTypeName: String,
    pythonTypeName: String,
    timestampFormat: Option[HttpTimestampFormat]
)
