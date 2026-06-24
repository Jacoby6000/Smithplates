package com.jacoby6000.smithplates.smithy.neutral

import com.jacoby6000.smithplates.codegen.core.*
import com.jacoby6000.smithplates.codegen.core.CodegenValidated.invalidNel
import com.jacoby6000.smithplates.codegen.core.NeutralType.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.DefaultTrait
import software.amazon.smithy.model.traits.RequiredTrait
import software.amazon.smithy.model.traits.SparseTrait
import software.amazon.smithy.model.traits.TimestampFormatTrait

import scala.jdk.OptionConverters.*

/** Maps Smithy shapes and members to [[NeutralType]], including optionality and sparse collections. */
object SmithyNeutralTypeResolver {
  final case class MemberContext(
      shapeId: ShapeId,
      memberName: String,
      role: String
  )

  def resolveMemberType(
      model: Model,
      member: MemberShape,
      optionalWhen: MemberShape => Boolean,
      timestampFormat: (Model, MemberShape) => CodegenValidated[TimestampFormat],
      context: MemberContext
  ): CodegenValidated[NeutralType] =
    model.getShape(member.getTarget).toScala match {
      case None              =>
        InvalidSmithyShape(
          ModelIds.fromShapeId(context.shapeId),
          s"${context.role} member '${context.memberName}' targets undefined shape '${member.getTarget}'"
        ).invalidNel
      case Some(targetShape) =>
        resolveShapeType(model, targetShape, Some(member), timestampFormat, context).map { resolved =>
          applyMemberOptionality(member, resolved, optionalWhen)
        }
    }

  def resolveShapeType(
      model: Model,
      shape: Shape,
      member: Option[MemberShape] = None,
      timestampFormat: (Model, MemberShape) => CodegenValidated[TimestampFormat] = SmithyTimestampFormats.defaultFormat,
      context: MemberContext = MemberContext(ShapeId.from("unknown#Unknown"), "unknown", "shape")
  ): CodegenValidated[NeutralType] = {
    val shapeId = shape.getId
    if (shapeId == SmithyPrelude.UnitShapeId) {
      CodegenValidated.valid(StringT)
    } else if (shape.isTimestampShape) {
      member match {
        case Some(memberShape) => timestampFormat(model, memberShape).map(format => TimestampT(format))
        case None              => CodegenValidated.valid(TimestampT(TimestampFormat.DateTime))
      }
    } else if (shape.isListShape) {
      val listShape     = shape.asListShape.get()
      val elementTarget = listShape.getMember.getTarget
      val elementShape  = model.expectShape(elementTarget)
      resolveShapeType(model, elementShape, None, timestampFormat, context).map { elementType =>
        val sparse =
          listShape.hasTrait(classOf[SparseTrait]) ||
            member.exists(_.hasTrait(classOf[SparseTrait])) ||
            listShape.getMember.hasTrait(classOf[SparseTrait])
        if (sparse) {
          ListT(NeutralType.optional(elementType))
        } else {
          ListT(elementType)
        }
      }
    } else if (shape.isMapShape) {
      val valueTarget = shape.asMapShape.get().getValue.getTarget
      val valueShape  = model.expectShape(valueTarget)
      resolveShapeType(model, valueShape, None, timestampFormat, context).map { valueType =>
        MapT(StringT, valueType)
      }
    } else if (shape.isStringShape && !SmithyPrelude.isPreludeShape(shapeId)) {
      CodegenValidated.valid(ModelRef(ModelIds.fromShapeId(shapeId)))
    } else if ((shape.isStructureShape || shape.isEnumShape || shape.isIntEnumShape || shape.isUnionShape) &&
      !SmithyPrelude.isPreludeShape(shapeId)) {
      CodegenValidated.valid(ModelRef(ModelIds.fromShapeId(shapeId)))
    } else {
      CodegenValidated.valid(primitiveType(shapeId))
    }
  }

  def applyMemberOptionality(
      member: MemberShape,
      resolved: NeutralType,
      optionalWhen: MemberShape => Boolean
  ): NeutralType =
    if (optionalWhen(member)) {
      NeutralType.optional(resolved)
    } else {
      resolved
    }

  def memberOptionalByRequiredTrait(member: MemberShape): Boolean =
    !member.hasTrait(classOf[RequiredTrait]) || member.hasTrait(classOf[DefaultTrait])

  def aliasUnderlying(model: Model, shapeId: ShapeId): CodegenValidated[NeutralType] = {
    val shape = model.expectShape(shapeId)
    if (shape.isStringShape) {
      CodegenValidated.valid(StringT)
    } else if (SmithyPrelude.isPrimitiveShapeId(shapeId)) {
      CodegenValidated.valid(primitiveType(shapeId))
    } else {
      InvalidSmithyShape(
        ModelIds.fromShapeId(shapeId),
        "expected a string or primitive alias target"
      ).invalidNel
    }
  }

  private def primitiveType(shapeId: ShapeId): NeutralType =
    shapeId.getName match {
      case "String"     => StringT
      case "Integer"    => IntegerT
      case "Long"       => LongT
      case "Float"      => FloatT
      case "Double"     => DoubleT
      case "BigDecimal" => BigDecimalT
      case "BigInteger" => BigIntegerT
      case "Boolean"    => BooleanT
      case "Blob"       => BytesT
      case "Timestamp"  => TimestampT(TimestampFormat.DateTime)
      case "Document"   => DocumentT
      case "Unit"       => StringT
      case _            => StringT
    }
}

object SmithyTimestampFormats {
  def defaultFormat(model: Model, member: MemberShape): CodegenValidated[TimestampFormat] = {
    val memberFormat = member.getTrait(classOf[TimestampFormatTrait]).toScala.map(_.getFormat)
    val targetFormat =
      model.getShape(member.getTarget).toScala.flatMap { target =>
        target.getTrait(classOf[TimestampFormatTrait]).toScala.map(_.getFormat)
      }
    val format       = memberFormat.orElse(targetFormat)
    CodegenValidated.valid(
      format match {
        case None                                            => TimestampFormat.DateTime
        case Some(TimestampFormatTrait.Format.DATE_TIME)     => TimestampFormat.DateTime
        case Some(TimestampFormatTrait.Format.EPOCH_SECONDS) => TimestampFormat.EpochSeconds
        case Some(TimestampFormatTrait.Format.HTTP_DATE)     => TimestampFormat.DateTime
        case Some(TimestampFormatTrait.Format.UNKNOWN)       => TimestampFormat.DateTime
      }
    )
  }
}
