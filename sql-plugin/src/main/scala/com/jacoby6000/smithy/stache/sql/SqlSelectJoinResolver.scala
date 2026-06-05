package com.jacoby6000.smithy.stache.sql

import cats.syntax.all.*
import com.jacoby6000.smithy.stache.sql.shared.SqlShared
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.StructureShape

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

/** Resolves JOIN ON clauses from @sqlForeignKey relationships between two tables. */
private[sql] object SqlSelectJoinResolver {
  final case class ResolvedForeignKey(
      sourceTable: SqlTable,
      sourceColumn: String,
      targetTable: SqlTable,
      targetColumn: String
  )

  def resolveJoinCondition(
      model: Model,
      queryShape: ShapeId,
      primaryTable: SqlTable,
      primaryStructure: StructureShape,
      joinTable: SqlTable,
      joinStructure: StructureShape,
      joinType: SqlJoinType,
      primaryReferenceAlias: String,
      joinReferenceAlias: String
  ): SqlValidated[Option[SqlJoinCondition]] =
    if (joinType == SqlJoinType.Cross) {
      None.validNel
    } else {
      findForeignKeys(primaryTable, primaryStructure, joinTable, joinStructure) match {
        case Nil           =>
          SqlValidated.invalid(
            MissingJoinForeignKey(queryShape, primaryTable.name, joinTable.name)
          )
        case single :: Nil =>
          val (leftTableAlias, leftColumn, rightTableAlias, rightColumn) =
            if (single.sourceTable.name == primaryTable.name) {
              (primaryReferenceAlias, single.sourceColumn, joinReferenceAlias, single.targetColumn)
            } else {
              (primaryReferenceAlias, single.targetColumn, joinReferenceAlias, single.sourceColumn)
            }
          Some(
            SqlJoinCondition(
              left = SqlQualifiedColumn(leftTableAlias, leftColumn),
              right = SqlQualifiedColumn(rightTableAlias, rightColumn)
            )
          ).validNel
        case _             =>
          SqlValidated.invalid(
            AmbiguousJoinForeignKey(queryShape, primaryTable.name, joinTable.name)
          )
      }
    }

  private def findForeignKeys(
      primaryTable: SqlTable,
      primaryStructure: StructureShape,
      joinTable: SqlTable,
      joinStructure: StructureShape
  ): List[ResolvedForeignKey] = {
    val joinToPrimary =
      foreignKeysReferencing(joinStructure, joinTable, primaryStructure, primaryTable)
    val primaryToJoin =
      foreignKeysReferencing(primaryStructure, primaryTable, joinStructure, joinTable)
    joinToPrimary ++ primaryToJoin
  }

  private def foreignKeysReferencing(
      sourceStructure: StructureShape,
      sourceTable: SqlTable,
      targetStructure: StructureShape,
      targetTable: SqlTable
  ): List[ResolvedForeignKey] =
    sourceStructure.getAllMembers.asScala.toList.flatMap { case (memberName, member) =>
      SmithySqlTraitAccess.sqlForeignKey(member).flatMap { foreignKeyTrait =>
        SqlTableMemberCatalog
          .parseShapeId(foreignKeyTrait.getReferences)
          .filter(_ == targetStructure.getId)
          .flatMap { _ =>
            resolveReferencedColumn(targetStructure, foreignKeyTrait.getColumn.toScala).map { referencedColumn =>
              ResolvedForeignKey(
                sourceTable = sourceTable,
                sourceColumn = SmithySqlTraitAccess.columnName(memberName, member),
                targetTable = targetTable,
                targetColumn = referencedColumn
              )
            }
          }
      }
    }

  private def resolveReferencedColumn(
      targetStructure: StructureShape,
      explicitColumn: Option[String]
  ): Option[String] =
    SqlShared.trimmedNonEmpty(explicitColumn) match {
      case Some(column) => Some(column)
      case None         =>
        SqlTableMemberCatalog
          .membersFor(targetStructure)
          .filter(_.isPrimaryKey)
          .map(_.columnName) match {
          case single :: Nil => Some(single)
          case _             => None
        }
    }
}
