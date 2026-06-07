package com.jacoby6000.smithy.stache.sql.codegen.language

import com.jacoby6000.smithy.stache.sql.codegen.SqlCodegenMemberRole
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeId

trait LanguageTypeMapper {
  def resolveLanguageTypeName(model: Model, shape: Shape): String

  def resolvePrimitiveLanguageTypeName(shapeId: ShapeId): String

  def resolveOutputLanguageTypeName(shapeId: ShapeId): String

  def isOptionalMember(member: MemberShape, role: SqlCodegenMemberRole): Boolean
}
