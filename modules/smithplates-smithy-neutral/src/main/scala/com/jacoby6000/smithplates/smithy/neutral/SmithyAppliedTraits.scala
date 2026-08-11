package com.jacoby6000.smithplates.smithy.neutral

import com.jacoby6000.smithplates.codegen.core.AppliedTrait
import com.jacoby6000.smithplates.codegen.core.SmithyNodeValue
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.shapes.Shape

import scala.jdk.CollectionConverters.*

object SmithyAppliedTraits {
  def fromShape(shape: Shape): List[AppliedTrait] =
    shape.getAllTraits.asScala.toList
      .map { case (id, traitValue) =>
        AppliedTrait(
          id = ModelIds.fromShapeId(id),
          value = fromNode(traitValue.toNode),
          synthetic = traitValue.isSynthetic
        )
      }
      .sortBy(traitValue => (traitValue.id.namespace, traitValue.id.name))

  def fromNode(node: Node): SmithyNodeValue =
    if (node.isNullNode) {
      SmithyNodeValue.NullValue
    } else if (node.isBooleanNode) {
      SmithyNodeValue.BooleanValue(node.expectBooleanNode().getValue)
    } else if (node.isStringNode) {
      SmithyNodeValue.StringValue(node.expectStringNode().getValue)
    } else if (node.isNumberNode) {
      SmithyNodeValue.NumberValue(node.expectNumberNode().getValue.toString)
    } else if (node.isArrayNode) {
      SmithyNodeValue.ArrayValue(node.expectArrayNode().getElements.asScala.toList.map(fromNode))
    } else if (node.isObjectNode) {
      SmithyNodeValue.ObjectValue(
        node
          .expectObjectNode()
          .getMembers
          .asScala
          .toList
          .map { case (key, value) => key.getValue -> fromNode(value) }
          .sortBy(_._1)
      )
    } else {
      throw new IllegalArgumentException(s"unsupported Smithy node type: ${node.getType}")
    }
}
