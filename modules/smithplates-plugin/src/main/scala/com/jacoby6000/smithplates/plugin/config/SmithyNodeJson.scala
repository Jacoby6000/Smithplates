package com.jacoby6000.smithplates.plugin.config

import io.circe.Json
import software.amazon.smithy.model.node.Node

import scala.jdk.CollectionConverters.*

/** Converts Smithy AST nodes into Circe JSON for unified config decoding. */
object SmithyNodeJson {
  def toJson(node: Node): Json =
    if (node.isNullNode) {
      Json.Null
    } else if (node.isBooleanNode) {
      Json.fromBoolean(node.expectBooleanNode().getValue)
    } else if (node.isStringNode) {
      Json.fromString(node.expectStringNode().getValue)
    } else if (node.isNumberNode) {
      Json.fromBigDecimal(BigDecimal(node.expectNumberNode().getValue.toString))
    } else if (node.isArrayNode) {
      Json.fromValues(node.expectArrayNode().getElements.asScala.map(toJson).toVector)
    } else if (node.isObjectNode) {
      Json.fromFields(
        node
          .expectObjectNode()
          .getMembers
          .asScala
          .map { case (keyNode, valueNode) =>
            keyNode.expectStringNode().getValue -> toJson(valueNode)
          }
          .toSeq
      )
    } else {
      Json.Null
    }
}
