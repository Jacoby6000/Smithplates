package com.jacoby6000.smithy.stache.sql.codegen.python

import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.shapes.ShapeVisitor.Default

final private class PythonLanguageTypeNameVisitor(model: Model) extends Default[String] {
  def apply(shape: Shape): String =
    shape.accept(this)

  override protected def getDefault(shape: Shape): String =
    PythonTypeMapper.resolvePrimitiveLanguageTypeName(shape.toShapeId)

  override def structureShape(shape: StructureShape): String =
    shape.getId.getName

  override def enumShape(shape: EnumShape): String =
    shape.getId.getName

  override def intEnumShape(shape: IntEnumShape): String =
    shape.getId.getName

  override def unionShape(shape: UnionShape): String =
    shape.getId.getName

  override def listShape(shape: ListShape): String = {
    val memberTarget = shape.getMember.getTarget
    s"list[${apply(model.expectShape(memberTarget))}]"
  }

  override def mapShape(shape: MapShape): String = {
    val valueTarget = shape.getValue.getTarget
    s"dict[str, ${apply(model.expectShape(valueTarget))}]"
  }
}
