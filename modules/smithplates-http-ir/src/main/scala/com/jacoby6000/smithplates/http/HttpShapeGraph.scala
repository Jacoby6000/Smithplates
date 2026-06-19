package com.jacoby6000.smithplates.http

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

private[http] object HttpShapeGraph {
  def isUserDefinedStructure(model: Model, shapeId: ShapeId): Boolean =
    if (HttpSmithyTypeResolver.isPreludeShape(shapeId)) {
      false
    } else {
      model.getShape(shapeId).toScala.exists(_.isStructureShape)
    }

  def referencedShapes(model: Model, rootShapeIds: List[ShapeId]): (List[ShapeId], List[ShapeId]) = {
    val structures        = scala.collection.mutable.Set.empty[ShapeId]
    val unions            = scala.collection.mutable.Set.empty[ShapeId]
    val pendingStructures = scala.collection.mutable.Queue.empty[ShapeId]

    rootShapeIds.filter(isUserDefinedStructure(model, _)).foreach(pendingStructures.enqueue)

    while (pendingStructures.nonEmpty) {
      val shapeId = pendingStructures.dequeue()
      if (!structures.contains(shapeId)) {
        structures += shapeId
        model.getShape(shapeId).toScala.foreach { shape =>
          shape
            .members()
            .asScala
            .foreach { member =>
              internal.enqueueMemberTargets(model, member, pendingStructures, structures, unions)
            }
        }
      }
    }

    (structures.toList, unions.toList)
  }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def enqueueMemberTargets(
        model: Model,
        member: MemberShape,
        pendingStructures: scala.collection.mutable.Queue[ShapeId],
        structures: scala.collection.mutable.Set[ShapeId],
        unions: scala.collection.mutable.Set[ShapeId]
    ): Unit = {
      val targetShape = model.expectShape(member.getTarget)
      if (targetShape.isUnionShape) {
        val unionShapeId = targetShape.toShapeId
        if (!unions.contains(unionShapeId)) {
          unions += unionShapeId
          targetShape
            .asUnionShape()
            .get()
            .getAllMembers
            .asScala
            .values
            .foreach { unionMember =>
              memberTargets(model, unionMember).filter(HttpShapeGraph.isUserDefinedStructure(model, _)).foreach {
                referenced =>
                  if (!structures.contains(referenced)) {
                    pendingStructures.enqueue(referenced)
                  }
              }
            }
        }
      } else {
        memberTargets(model, member).filter(HttpShapeGraph.isUserDefinedStructure(model, _)).foreach { referenced =>
          if (!structures.contains(referenced)) {
            pendingStructures.enqueue(referenced)
          }
        }
      }
    }

    def memberTargets(model: Model, member: MemberShape): List[ShapeId] =
      model.expectShape(member.getTarget).accept(MemberTargetShapeIds)

    object MemberTargetShapeIds extends ShapeVisitor.Default[List[ShapeId]] {
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
}
