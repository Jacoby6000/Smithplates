package com.jacoby6000.smithy.stache.sql.codegen.language

import com.jacoby6000.smithy.stache.sql.SqlIrTypeNameResolver
import com.jacoby6000.smithy.stache.sql.codegen.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.MemberShape

object LanguageTypeResolver {
  def resolveMember(
      model: Model,
      memberName: String,
      member: MemberShape,
      role: SqlCodegenMemberRole = SqlCodegenMemberRole.General
  ): SqlCodegenMember = {
    val memberType = SqlIrTypeNameResolver.resolveMember(model, memberName, member)
    SqlCodegenMember(
      name = memberName,
      typeName = memberType.typeName,
      optional = SqlCodegenMemberOptional.isOptional(member, role),
      isStructure = memberType.isStructure,
      structureShapeId = memberType.structureShapeId,
      isUnion = memberType.isUnion
    )
  }
}
