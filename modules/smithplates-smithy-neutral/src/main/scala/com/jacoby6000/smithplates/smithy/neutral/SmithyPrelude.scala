package com.jacoby6000.smithplates.smithy.neutral

import com.jacoby6000.smithplates.codegen.core.NeutralType
import com.jacoby6000.smithplates.codegen.core.NeutralType.*
import com.jacoby6000.smithplates.codegen.core.TimestampFormat
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeId

object SmithyPrelude {
  val UnitShapeId: ShapeId = ShapeId.from("smithy.api#Unit")

  val PrimitiveShapeIds: Set[ShapeId] =
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
    shapeId.getNamespace == "smithy.api"

  def isPrimitiveShapeId(shapeId: ShapeId): Boolean =
    PrimitiveShapeIds.contains(shapeId)

  def isUserDefinedAliasShape(shape: Shape): Boolean =
    userDefinedAliasUnderlying(shape).isDefined

  def userDefinedAliasUnderlying(shape: Shape): Option[NeutralType] = {
    val shapeId = shape.getId
    if (isPreludeShape(shapeId) || shape.isEnumShape || shape.isIntEnumShape) {
      None
    } else if (shape.isStringShape) {
      Some(StringT)
    } else if (shape.isIntegerShape) {
      Some(IntegerT)
    } else if (shape.isLongShape) {
      Some(LongT)
    } else if (shape.isFloatShape) {
      Some(FloatT)
    } else if (shape.isDoubleShape) {
      Some(DoubleT)
    } else if (shape.isBigDecimalShape) {
      Some(BigDecimalT)
    } else if (shape.isBigIntegerShape) {
      Some(BigIntegerT)
    } else if (shape.isBooleanShape) {
      Some(BooleanT)
    } else if (shape.isBlobShape) {
      Some(BytesT)
    } else if (shape.isTimestampShape) {
      Some(TimestampT(TimestampFormat.DateTime))
    } else {
      None
    }
  }
}
