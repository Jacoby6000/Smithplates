package com.jacoby6000.smithy.stache.sql.codegen.python

import com.jacoby6000.smithy.stache.sql.codegen.SqlCodegenMember
import com.jacoby6000.smithy.stache.sql.codegen.SqlCodegenMemberRole
import com.jacoby6000.smithy.stache.sql.codegen.SqlCodegenShapeGraph
import com.jacoby6000.smithy.stache.sql.codegen.language.LanguageTypeResolver
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeId

/** Python-facing facade over neutral shape graph and language type resolution. */
object SqlCodegenTypeResolver {
  val UnitShapeId: ShapeId = SqlCodegenShapeGraph.UnitShapeId

  def isPreludeShape(shapeId: ShapeId): Boolean =
    SqlCodegenShapeGraph.isPreludeShape(shapeId)

  def isUserDefinedStructure(model: Model, shapeId: ShapeId): Boolean =
    SqlCodegenShapeGraph.isUserDefinedStructure(model, shapeId)

  def resolveTypeName(model: Model, shape: Shape): String =
    LanguageTypeResolver.resolveTypeName(model, shape)

  def resolvePythonTypeName(model: Model, shape: Shape): String =
    PythonTypeMapper.resolveLanguageTypeName(model, shape)

  def resolveOutputPythonTypeName(shapeId: ShapeId): String =
    PythonTypeMapper.resolveOutputLanguageTypeName(shapeId)

  def resolveMember(
      model: Model,
      memberName: String,
      member: MemberShape,
      role: SqlCodegenMemberRole = SqlCodegenMemberRole.General
  ): SqlCodegenMember =
    LanguageTypeResolver.resolveMember(model, memberName, member, PythonTypeMapper, role)

  def referencedStructureIds(model: Model, rootShapeId: ShapeId): List[ShapeId] =
    SqlCodegenShapeGraph.referencedStructureIds(model, rootShapeId)

  def referencedUnionIds(model: Model, rootShapeIds: Iterable[ShapeId]): List[ShapeId] =
    SqlCodegenShapeGraph.referencedUnionIds(model, rootShapeIds)

  def resolvePrimitivePythonTypeName(shapeId: ShapeId): String =
    PythonTypeMapper.resolvePrimitiveLanguageTypeName(shapeId)
}
