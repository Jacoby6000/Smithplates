package com.jacoby6000.smithplates.smithy.neutral

import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeId

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

/** Discovers user-defined alias shapes reachable from extraction roots through structure/union/list/map edges. */
object SmithyAliasClosure {
  def aliasShapeIds(model: Model, rootShapeIds: List[ShapeId]): List[ShapeId] =
    transitiveMemberTargets(model, rootShapeIds).filter { shapeId =>
      SmithyPrelude.isUserDefinedAliasShape(model.expectShape(shapeId))
    }.distinct

  def transitiveMemberTargets(model: Model, rootShapeIds: List[ShapeId]): List[ShapeId] = {
    val visited = scala.collection.mutable.Set.empty[ShapeId]
    val queue   = scala.collection.mutable.Queue.from(rootShapeIds.distinct)
    val targets = scala.collection.mutable.ListBuffer.empty[ShapeId]

    while (queue.nonEmpty) {
      val current = queue.dequeue()
      if (!visited.contains(current)) {
        visited += current
        directMemberTargets(model, current).foreach { target =>
          targets += target
          queue.enqueue(target)
        }
      }
    }

    targets.toList.distinct
  }

  def directMemberTargets(model: Model, shapeId: ShapeId): List[ShapeId] =
    model.getShape(shapeId).toScala.toList.flatMap(shape => directMemberTargets(model, shape))

  def directMemberTargets(model: Model, shape: Shape): List[ShapeId] =
    if (shape.isStructureShape) {
      shape.asStructureShape.get().getAllMembers.asScala.values.toList.map(_.getTarget)
    } else if (shape.isUnionShape) {
      shape.asUnionShape.get().getAllMembers.asScala.values.toList.map(_.getTarget)
    } else if (shape.isListShape) {
      List(shape.asListShape.get().getMember.getTarget)
    } else if (shape.isMapShape) {
      val mapShape = shape.asMapShape.get()
      List(mapShape.getKey.getTarget, mapShape.getValue.getTarget)
    } else {
      Nil
    }
}
