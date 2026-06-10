package com.jacoby6000.smithplates.sql

import software.amazon.smithy.model.shapes.ShapeId

/** String and naming helpers shared by schema extraction and DDL rendering. */
object SqlText {
  def trimmedNonEmpty(text: String): Option[String] =
    Option(text).filter(_.trim.nonEmpty)

  def trimmedNonEmpty(opt: Option[String]): Option[String] =
    opt.filter(_.trim.nonEmpty)

  def enumTypeName(shapeId: ShapeId): String = {
    val namespace = shapeId.getNamespace.replace('.', '_').replace('-', '_')
    s"${namespace}_${shapeId.getName}".toLowerCase
  }
}
