package com.jacoby6000.smithplates.http

import cats.syntax.all.*
import com.jacoby6000.smithplates.http.SmithyHttpTraitAccess.*
import com.jacoby6000.smithplates.http.model.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ShapeId

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

private[http] object HttpStructureExtractor {
  private val UnitShapeId: ShapeId = ShapeId.from("smithy.api#Unit")

  def extractForService(
      model: Model,
      serviceShape: ShapeId,
      operations: List[HttpOperation],
      serviceErrors: List[HttpServiceError]
  ): HttpValidated[List[HttpStructure]] = {
    val shapeIds =
      (operations.flatMap(_.outputShape) ++ serviceErrors.map(_.shapeId))
        .filter(_ != UnitShapeId)
        .distinct
    shapeIds.traverse(shapeId => extractStructure(model, serviceShape, shapeId))
  }

  private def extractStructure(
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
}
