package com.jacoby6000.smithplates.smithy.neutral

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
}
