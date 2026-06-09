package com.jacoby6000.smithplates.sql

import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ListShape
import software.amazon.smithy.model.shapes.MapShape
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.ShapeVisitor
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.shapes.UnionShape

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

object SqlShapeGraph {
  val UnitShapeId: ShapeId = ShapeId.from("smithy.api#Unit")

  def isUserDefinedStructure(model: Model, shapeId: ShapeId): Boolean =
    if (SqlIrTypeNameResolver.isPreludeShape(shapeId)) {
      false
    } else {
      model.getShape(shapeId).toScala.exists(_.isStructureShape)
    }

  def referencedStructureIds(model: Model, rootShapeId: ShapeId): List[ShapeId] = {
    val visited = scala.collection.mutable.Set.empty[ShapeId]
    val pending = scala.collection.mutable.Queue.empty[ShapeId]
    if (isUserDefinedStructure(model, rootShapeId)) {
      pending.enqueue(rootShapeId)
    }

    val collected = scala.collection.mutable.ListBuffer.empty[ShapeId]
    while (pending.nonEmpty) {
      val shapeId = pending.dequeue()
      if (!visited.contains(shapeId)) {
        visited += shapeId
        collected += shapeId
        model.getShape(shapeId).toScala.foreach { shape =>
          shape
            .members()
            .asScala
            .foreach { member =>
              memberTargets(model, member).filter(isUserDefinedStructure(model, _)).foreach { referenced =>
                if (!visited.contains(referenced)) {
                  pending.enqueue(referenced)
                }
              }
            }
        }
      }
    }

    collected.toList
  }

  def referencedUnionIds(model: Model, rootShapeIds: Iterable[ShapeId]): List[ShapeId] = {
    val visitedStructures = scala.collection.mutable.Set.empty[ShapeId]
    val visitedUnions     = scala.collection.mutable.Set.empty[ShapeId]
    val pendingStructures = scala.collection.mutable.Queue.empty[ShapeId]
    val collectedUnions   = scala.collection.mutable.ListBuffer.empty[ShapeId]

    rootShapeIds.foreach { shapeId =>
      if (isUserDefinedStructure(model, shapeId)) {
        pendingStructures.enqueue(shapeId)
      }
    }

    while (pendingStructures.nonEmpty) {
      val shapeId = pendingStructures.dequeue()
      if (!visitedStructures.contains(shapeId)) {
        visitedStructures += shapeId
        model.getShape(shapeId).toScala.foreach { shape =>
          shape
            .members()
            .asScala
            .foreach { member =>
              val targetShape = model.expectShape(member.getTarget)
              if (targetShape.isUnionShape) {
                val unionShapeId = targetShape.toShapeId
                if (!visitedUnions.contains(unionShapeId)) {
                  visitedUnions += unionShapeId
                  collectedUnions += unionShapeId
                }
              } else {
                memberTargets(model, member).filter(isUserDefinedStructure(model, _)).foreach { referenced =>
                  if (!visitedStructures.contains(referenced)) {
                    pendingStructures.enqueue(referenced)
                  }
                }
              }
            }
        }
      }
    }

    collectedUnions.toList
  }

  private def memberTargets(model: Model, member: MemberShape): List[ShapeId] =
    model.expectShape(member.getTarget).accept(MemberTargetShapeIds)

  private object MemberTargetShapeIds extends ShapeVisitor.Default[List[ShapeId]] {
    override protected def getDefault(shape: Shape): List[ShapeId] =
      Nil

    override def structureShape(shape: StructureShape): List[ShapeId] =
      List(shape.getId)

    override def listShape(shape: ListShape): List[ShapeId] =
      List(shape.getMember.getTarget)

    override def mapShape(shape: MapShape): List[ShapeId] =
      List(shape.getValue.getTarget)

    override def unionShape(shape: UnionShape): List[ShapeId] =
      shape.getAllMembers.asScala.values.map(_.getTarget).toList
  }
}
