package com.jacoby6000.smithplates.sql.service

import cats.syntax.all.*
import com.jacoby6000.smithplates.sql.*
import com.jacoby6000.smithplates.sql.model.*
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.StructureShape

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

/** Resolves JOIN ON clauses from @sqlForeignKey relationships between two tables. */
private[service] object SqlSelectJoinResolver {
  final case class ResolvedForeignKey(
      sourceTable: SqlTable,
      sourceColumn: String,
      targetTable: SqlTable,
      targetColumn: String
  )

  def resolveTransitiveJoinCondition(
      queryShape: ShapeId,
      leftContexts: List[(SqlTable, StructureShape, String)],
      joinTable: SqlTable,
      joinStructure: StructureShape,
      joinType: SqlJoinType,
      joinReferenceAlias: String,
      deriveTrait: String
  ): SqlValidated[Option[SqlJoinCondition]] =
    if (joinType == SqlJoinType.Cross) {
      None.validNel
    } else {
      leftContexts.foldLeft[Option[SqlValidated[Option[SqlJoinCondition]]]](None) {
        case (resolved @ Some(_), _)                                =>
          resolved
        case (None, (leftTable, leftStructure, leftReferenceAlias)) =>
          findForeignKeys(leftTable, leftStructure, joinTable, joinStructure) match {
            case Nil           => None
            case single :: Nil =>
              Some(
                internal
                  .buildJoinCondition(single, leftTable, leftReferenceAlias, joinReferenceAlias)
                  .some
                  .validNel
              )
            case _             =>
              Some(
                SqlValidated.invalid(
                  AmbiguousJoinForeignKey(queryShape, deriveTrait, leftTable.name, joinTable.name)
                )
              )
          }
      } match {
        case Some(result) => result
        case None         =>
          val primaryTableName =
            leftContexts.lastOption.map(_._1.name).getOrElse(joinTable.name)
          SqlValidated.invalid(
            MissingJoinForeignKey(queryShape, deriveTrait, primaryTableName, joinTable.name)
          )
      }
    }

  def findForeignKeys(
      primaryTable: SqlTable,
      primaryStructure: StructureShape,
      joinTable: SqlTable,
      joinStructure: StructureShape
  ): List[ResolvedForeignKey] = {
    val joinToPrimary =
      internal.foreignKeysReferencing(joinStructure, joinTable, primaryStructure, primaryTable)
    val primaryToJoin =
      internal.foreignKeysReferencing(primaryStructure, primaryTable, joinStructure, joinTable)
    joinToPrimary ++ primaryToJoin
  }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def buildJoinCondition(
        foreignKey: ResolvedForeignKey,
        leftTable: SqlTable,
        leftReferenceAlias: String,
        joinReferenceAlias: String
    ): SqlJoinCondition = {
      val (leftTableAlias, leftColumn, rightTableAlias, rightColumn) =
        if (foreignKey.sourceTable.name == leftTable.name) {
          (leftReferenceAlias, foreignKey.sourceColumn, joinReferenceAlias, foreignKey.targetColumn)
        } else {
          (leftReferenceAlias, foreignKey.targetColumn, joinReferenceAlias, foreignKey.sourceColumn)
        }
      SqlJoinCondition(
        left = SqlQualifiedColumn(leftTableAlias, leftColumn),
        right = SqlQualifiedColumn(rightTableAlias, rightColumn)
      )
    }

    def foreignKeysReferencing(
        sourceStructure: StructureShape,
        sourceTable: SqlTable,
        targetStructure: StructureShape,
        targetTable: SqlTable
    ): List[ResolvedForeignKey] =
      sourceStructure.getAllMembers.asScala.toList.flatMap { case (memberName, member) =>
        member.sqlForeignKey.flatMap { foreignKeyTrait =>
          SqlTableMemberCatalog
            .parseShapeId(foreignKeyTrait.getReferences)
            .filter(_ == targetStructure.getId)
            .flatMap { _ =>
              resolveReferencedColumn(targetStructure, foreignKeyTrait.getColumn.toScala).map { referencedColumn =>
                ResolvedForeignKey(
                  sourceTable = sourceTable,
                  sourceColumn = member.sqlColumnName(memberName),
                  targetTable = targetTable,
                  targetColumn = referencedColumn
                )
              }
            }
        }
      }

    def resolveReferencedColumn(
        targetStructure: StructureShape,
        explicitColumn: Option[String]
    ): Option[String] =
      SqlText.trimmedNonEmpty(explicitColumn) match {
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
}
