package com.jacoby6000.smithy.stache.sql.shared

import com.jacoby6000.smithy.stache.sql.{DuplicateSqlColumnIndex, SmithySqlTraitAccess, SqlValidated}
import scala.jdk.CollectionConverters.*
import software.amazon.smithy.model.shapes.{MemberShape, StructureShape}

/** Stable @sqlTable member ordering for DDL, queries, and codegen. */
object SqlTableMemberOrdering {
  private val CreatedTimestampDefaultIndex: Int = Int.MaxValue - 1
  private val UpdatedTimestampDefaultIndex: Int = Int.MaxValue

  def orderedMembers(structure: StructureShape): List[(String, MemberShape)] = {
    val members = structure.getAllMembers.asScala.toList
    members.sortBy { case (memberName, member) =>
      sortWeight(memberName, member, members)
    }
  }

  def validate(structure: StructureShape): SqlValidated[Unit] = {
    val members = structure.getAllMembers.asScala.toList
    val weightedMembers =
      members.map { case (memberName, member) =>
        memberName -> sortWeight(memberName, member, members)
      }

    weightedMembers
      .groupBy(_._2)
      .collectFirst { case (_, grouped) if grouped.size > 1 =>
        DuplicateSqlColumnIndex(
          structure.getId,
          grouped.map(_._1).sorted,
          grouped.head._2._2
        )
      }
      .map(SqlValidated.invalid)
      .getOrElse(SqlValidated.valid(()))
  }

  private def sortWeight(
      memberName: String,
      member: MemberShape,
      members: List[(String, MemberShape)]
    ): (Int, Int) = {
    val definitionOrder = members.indexWhere(_._1 == memberName)
    explicitSortIndex(member) match {
      case Some(index) => (1, index)
      case None        => (0, definitionOrder)
    }
  }

  private def explicitSortIndex(member: MemberShape): Option[Int] =
    SmithySqlTraitAccess
      .sqlColumnIndex(member)
      .orElse {
        if (SmithySqlTraitAccess.sqlUpdatedTimestamp(member)) {
          Some(UpdatedTimestampDefaultIndex)
        } else if (SmithySqlTraitAccess.sqlCreatedTimestamp(member)) {
          Some(CreatedTimestampDefaultIndex)
        } else {
          None
        }
      }
}
