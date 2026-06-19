package com.jacoby6000.smithplates.http

import cats.syntax.all.*
import com.jacoby6000.smithplates.http.SmithyHttpTraitAccess.*
import com.jacoby6000.smithplates.http.model.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ShapeId

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

private[http] object HttpStructureExtractor {
  final case class ExtractedShapes(
      structures: List[HttpStructure],
      unions: List[HttpUnion]
  )

  def extractForService(
      model: Model,
      serviceShape: ShapeId,
      operations: List[HttpOperation],
      serviceErrors: List[HttpServiceError]
  ): HttpValidated[ExtractedShapes] = {
    val rootShapeIds             =
      (operations.flatMap(_.responseBinding.allVariants.map(_.modelShapeId)) ++
        serviceErrors.map(_.shapeId) ++
        internal.documentInputShapeIds(operations) ++
        internal.memberPayloadStructureShapeIds(model, operations))
        .filter(_ != internal.UnitShapeId)
        .distinct
    val (structureIds, unionIds) = HttpShapeGraph.referencedShapes(model, rootShapeIds)
    (
      structureIds.traverse(shapeId => internal.extractStructure(model, serviceShape, shapeId)),
      unionIds.traverse(shapeId => internal.extractUnion(model, serviceShape, shapeId))
    ).mapN(ExtractedShapes.apply)
  }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    val UnitShapeId: ShapeId = ShapeId.from("smithy.api#Unit")

    def documentInputShapeIds(operations: List[HttpOperation]): List[ShapeId] =
      operations.flatMap { operation =>
        operation.bodyBinding match {
          case HttpOperationBodyBinding.Document(inputShape) => List(inputShape)
          case _                                             => Nil
        }
      }

    def memberPayloadStructureShapeIds(model: Model, operations: List[HttpOperation]): List[ShapeId] =
      operations.flatMap { operation =>
        operation.bodyBinding match {
          case HttpOperationBodyBinding.Members(members) =>
            members
              .map(_.targetShape)
              .filter(shapeId => HttpShapeGraph.isUserDefinedStructure(model, shapeId))
          case _                                         =>
            Nil
        }
      }

    def extractStructure(
        model: Model,
        serviceShape: ShapeId,
        shapeId: ShapeId
    ): HttpValidated[HttpStructure] =
      model.getShape(shapeId).toScala.flatMap(_.asStructureShape.toScala) match {
        case None                                                            =>
          InvalidHttpService(
            serviceShape,
            s"structure shape '${shapeId.toString}' is not defined in the model"
          ).invalidNel
        case Some(structure) if structure.getId.getNamespace == "smithy.api" =>
          InvalidHttpService(
            serviceShape,
            s"structure shape '${shapeId.toString}' cannot be a smithy prelude shape"
          ).invalidNel
        case Some(structure)                                                 =>
          structure.getAllMembers.asScala.toList
            .traverse { case (memberName, member) =>
              HttpSmithyTypeResolver
                .resolveMemberType(model, serviceShape, structure.getId.getName, memberName, member)
                .map { memberType =>
                  HttpStructureMember(
                    name = memberName,
                    typeName = memberType.typeName,
                    required = member.requiredMember,
                    timestampFormat = memberType.timestampFormat
                  )
                }
            }
            .map { members =>
              HttpStructure(
                shapeId = structure.getId,
                name = structure.getId.getName,
                members = members
              )
            }
      }

    def extractUnion(
        model: Model,
        serviceShape: ShapeId,
        shapeId: ShapeId
    ): HttpValidated[HttpUnion] =
      model.getShape(shapeId).toScala.flatMap(_.asUnionShape.toScala) match {
        case None                                                    =>
          InvalidHttpService(
            serviceShape,
            s"union shape '${shapeId.toString}' is not defined in the model"
          ).invalidNel
        case Some(union) if union.getId.getNamespace == "smithy.api" =>
          InvalidHttpService(
            serviceShape,
            s"union shape '${shapeId.toString}' cannot be a smithy prelude shape"
          ).invalidNel
        case Some(union)                                             =>
          union.getAllMembers.asScala.toList
            .traverse { case (memberName, member) =>
              HttpSmithyTypeResolver
                .resolveMemberType(model, serviceShape, union.getId.getName, memberName, member)
                .map { memberType =>
                  HttpUnionMember(
                    name = memberName,
                    typeName = memberType.typeName,
                    timestampFormat = memberType.timestampFormat
                  )
                }
            }
            .map { members =>
              HttpUnion(
                shapeId = union.getId,
                name = union.getId.getName,
                members = members
              )
            }
      }
  }
}
