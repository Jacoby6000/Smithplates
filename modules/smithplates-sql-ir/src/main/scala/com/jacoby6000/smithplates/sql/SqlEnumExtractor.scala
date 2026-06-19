package com.jacoby6000.smithplates.sql

import com.jacoby6000.smithplates.sql.model.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ShapeId

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

object SqlEnumExtractor {
  def extractReferenced(
      model: Model,
      namespace: String,
      structures: List[SqlStructure],
      unions: List[SqlUnion],
      extraTypeNames: List[String] = Nil
  ): (List[SqlStringEnum], List[SqlIntEnum]) = {
    val typeNames =
      (structures.flatMap(_.members.map(_.typeName)) ++
        unions.flatMap(_.members.map(_.typeName)) ++
        extraTypeNames)
        .flatMap(componentTypeNames)
        .distinct
    val extracted = typeNames.flatMap(typeName => extractEnum(model, namespace, typeName))
    (
      extracted.collect { case Left(value) => value }.sortBy(_.name),
      extracted.collect { case Right(value) => value }.sortBy(_.name)
    )
  }

  private def componentTypeNames(typeName: String): List[String] =
    if (typeName.startsWith("List[")) {
      componentTypeNames(typeName.substring(5, typeName.length - 1))
    } else if (typeName.startsWith("Map[String, ")) {
      componentTypeNames(typeName.substring(12, typeName.length - 1))
    } else if (SqlIrTypeNameResolver.PreludePrimitiveTypeNames.contains(typeName)) {
      Nil
    } else {
      List(typeName)
    }

  private def extractEnum(
      model: Model,
      namespace: String,
      typeName: String
  ): Option[Either[SqlStringEnum, SqlIntEnum]] =
    model.getShape(ShapeId.fromParts(namespace, typeName)).toScala.flatMap {
      case shape if shape.isEnumShape    =>
        val enumShape = shape.asEnumShape.get()
        Some(
          Left(
            SqlStringEnum(
              shapeId = enumShape.getId,
              name = typeName,
              members = enumShape.getEnumValues.asScala.toList.sortBy(_._1).map { case (name, value) =>
                SqlStringEnumMember(name = name, value = value)
              }
            )
          )
        )
      case shape if shape.isIntEnumShape =>
        val intEnumShape = shape.asIntEnumShape.get()
        Some(
          Right(
            SqlIntEnum(
              shapeId = intEnumShape.getId,
              name = typeName,
              members = intEnumShape.getEnumValues.asScala.toList.sortBy(_._1).map { case (name, value) =>
                SqlIntEnumMember(name = name, value = value.intValue())
              }
            )
          )
        )
      case _                             =>
        None
    }
}
