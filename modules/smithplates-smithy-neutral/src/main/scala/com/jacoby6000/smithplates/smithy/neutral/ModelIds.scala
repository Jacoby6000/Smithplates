package com.jacoby6000.smithplates.smithy.neutral

import com.jacoby6000.smithplates.codegen.core.ModelId
import software.amazon.smithy.model.shapes.ShapeId

object ModelIds {
  def fromShapeId(shapeId: ShapeId): ModelId =
    ModelId(namespace = shapeId.getNamespace, name = shapeId.getName)

  def toShapeId(modelId: ModelId): ShapeId =
    ShapeId.fromParts(modelId.namespace, modelId.name)
}
