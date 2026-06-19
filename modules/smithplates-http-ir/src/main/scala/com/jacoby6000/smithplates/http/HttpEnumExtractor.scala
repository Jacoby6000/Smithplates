package com.jacoby6000.smithplates.http

import com.jacoby6000.smithplates.http.model.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ShapeId

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

private[http] object HttpEnumExtractor {
  def extractReferenced(
      model: Model,
      serviceShape: ShapeId,
      structures: List[HttpStructure],
      unions: List[HttpUnion]
  ): (List[HttpStringEnum], List[HttpIntEnum]) = {
    val namespace = serviceShape.getNamespace
    val typeNames =
      (structures.flatMap(_.members.map(_.typeName)) ++ unions.flatMap(_.members.map(_.typeName)))
        .flatMap(internal.componentTypeNames)
        .distinct
    val extracted = typeNames.flatMap(typeName => internal.extractEnum(model, namespace, typeName))
    (
      extracted.collect { case Left(value) => value },
      extracted.collect { case Right(value) => value }
    )
  }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def componentTypeNames(typeName: String): List[String] =
      if (typeName.startsWith("List[")) {
        componentTypeNames(typeName.substring(5, typeName.length - 1))
      } else if (typeName.startsWith("Map[String, ")) {
        componentTypeNames(typeName.substring(12, typeName.length - 1))
      } else if (HttpSmithyTypeResolver.primitiveTypeNames.contains(typeName)) {
        Nil
      } else {
        List(typeName)
      }

    def extractEnum(
        model: Model,
        namespace: String,
        typeName: String
    ): Option[Either[HttpStringEnum, HttpIntEnum]] =
      model.getShape(ShapeId.fromParts(namespace, typeName)).toScala.flatMap {
        case shape if shape.isEnumShape    =>
          val enumShape = shape.asEnumShape.get()
          Some(
            Left(
              HttpStringEnum(
                shapeId = enumShape.getId,
                name = typeName,
                members = enumShape.getEnumValues.asScala.toList.sortBy(_._1).map { case (name, value) =>
                  HttpStringEnumMember(name = name, value = value)
                }
              )
            )
          )
        case shape if shape.isIntEnumShape =>
          val intEnumShape = shape.asIntEnumShape.get()
          Some(
            Right(
              HttpIntEnum(
                shapeId = intEnumShape.getId,
                name = typeName,
                members = intEnumShape.getEnumValues.asScala.toList.sortBy(_._1).map { case (name, value) =>
                  HttpIntEnumMember(name = name, value = value.intValue())
                }
              )
            )
          )
        case _                             =>
          None
      }
  }
}
