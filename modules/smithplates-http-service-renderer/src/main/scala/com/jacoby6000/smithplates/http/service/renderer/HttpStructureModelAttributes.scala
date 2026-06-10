package com.jacoby6000.smithplates.http.service.renderer

import cats.syntax.all.*
import com.jacoby6000.smithplates.http.HttpSmithyTypeResolver
import com.jacoby6000.smithplates.http.HttpValidated
import com.jacoby6000.smithplates.http.model.HttpSchemaError
import com.jacoby6000.smithplates.http.model.HttpServiceIr
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ShapeId

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

object HttpStructureModelAttributes {
  final case class StructureMemberView(
      name: String,
      pythonTypeName: String,
      required: Boolean
  ) {
    def fieldDefinition: String = {
      val typeAnnotation =
        if (required) {
          pythonTypeName
        } else {
          s"$pythonTypeName | None"
        }
      val fieldDefault   =
        if (required) {
          "..."
        } else {
          "default=None"
        }
      s"$name: $typeAnnotation = Field($fieldDefault)"
    }
  }

  final case class StructureModelView(
      shapeId: ShapeId,
      className: String,
      moduleName: String,
      members: List[StructureMemberView]
  )

  def outputModels(model: Model, serviceIr: HttpServiceIr): Either[HttpSchemaError, List[StructureModelView]] = {
    val outputShapeIds =
      serviceIr.services.flatMap(_.routeGroups).flatMap(_.operations).flatMap(_.outputShape).distinct
    outputShapeIds
      .traverse(shapeId => structureModel(model, shapeId))
      .map(_.flatten)
      .toEither
      .left
      .map(_.head)
  }

  private def structureModel(model: Model, shapeId: ShapeId): HttpValidated[Option[StructureModelView]] =
    model.getShape(shapeId).toScala.flatMap(_.asStructureShape.toScala) match {
      case None                                                            =>
        None.validNel
      case Some(structure) if structure.getId.getNamespace == "smithy.api" =>
        None.validNel
      case Some(structure)                                                 =>
        val serviceShape = structure.getId
        structure.getAllMembers.asScala.toList
          .traverse { case (memberName, member) =>
            HttpSmithyTypeResolver
              .resolveMemberType(model, serviceShape, structure.getId.getName, memberName, member)
              .map { memberType =>
                StructureMemberView(
                  name = memberName,
                  pythonTypeName = memberType.pythonTypeName,
                  required = Option(
                    member.getTrait(classOf[software.amazon.smithy.model.traits.RequiredTrait]).orElse(null)).isDefined
                )
              }
          }
          .map { members =>
            Some(
              StructureModelView(
                shapeId = structure.getId,
                className = structure.getId.getName,
                moduleName = HttpCodegenTemplateAttributes.toSnakeCase(structure.getId.getName),
                members = members
              )
            )
          }
    }
}
