package com.jacoby6000.smithplates.smithy.neutral

import munit.FunSuite
import software.amazon.smithy.model.shapes.ShapeId

class ModelIdsSpec extends FunSuite {
  test("fromShapeId and toShapeId round-trip") {
    val shapeId = ShapeId.from("example.widgets#WidgetId")
    val modelId = ModelIds.fromShapeId(shapeId)
    assertEquals(modelId.namespace, "example.widgets")
    assertEquals(modelId.name, "WidgetId")
    assertEquals(ModelIds.toShapeId(modelId), shapeId)
  }
}
