package com.jacoby6000.smithy.stache.sql.codegen

import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.ShapeId

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

object SqlCodegenShapeGraph {
  val UnitShapeId: ShapeId = ShapeId.from("smithy.api#Unit")

  private val PreludeShapeIds: Set[ShapeId] =
    Set(
      UnitShapeId,
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
    PreludeShapeIds.contains(shapeId)

  def isUserDefinedStructure(model: Model, shapeId: ShapeId): Boolean =
    if (isPreludeShape(shapeId)) {
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

  private def memberTargets(model: Model, member: MemberShape): List[ShapeId] = {
    val targetShape = model.expectShape(member.getTarget)
    targetShape match {
      case shape if shape.isStructureShape =>
        List(shape.toShapeId)
      case shape if shape.isListShape      =>
        List(shape.asListShape.get().getMember.getTarget)
      case shape if shape.isMapShape       =>
        List(shape.asMapShape.get().getValue.getTarget)
      case shape if shape.isUnionShape     =>
        shape.asUnionShape.get().getAllMembers.asScala.values.map(_.getTarget).toList
      case _                               =>
        Nil
    }
  }
}
