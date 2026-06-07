package com.jacoby6000.smithy.stache.sql.codegen

import com.jacoby6000.smithy.stache.sql.*
import software.amazon.smithy.model.shapes.MemberShape

object SqlCodegenMemberOptional {
  def isOptional(member: MemberShape, role: SqlCodegenMemberRole): Boolean =
    role match {
      case SqlCodegenMemberRole.General     =>
        !member.isRequired
      case SqlCodegenMemberRole.SqlTableRow =>
        !member.isRequired &&
        !member.sqlPrimaryKey &&
        member.autoGeneration.isEmpty
    }
}
