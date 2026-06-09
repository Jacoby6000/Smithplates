package com.jacoby6000.smithy.stache.sql.service

import cats.syntax.all.*
import com.jacoby6000.smithy.stache.sql.*
import com.jacoby6000.smithy.stache.sql.service.traits.SqlSelectJoinValue
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.StructureShape

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

private[service] object SqlSelectTableContext {
  final case class TableContext(
      tableRef: String,
      shapeId: ShapeId,
      table: SqlTable,
      structure: StructureShape,
      membersByName: Map[String, SqlTableMemberCatalog.TableMemberInfo],
      referenceAlias: String
  )

  def primaryContext(
      tableRef: String,
      table: SqlTable,
      structure: StructureShape,
      queryAlias: Option[String]
  ): TableContext =
    TableContext(
      tableRef = tableRef,
      shapeId = structure.getId,
      table = table,
      structure = structure,
      membersByName = SqlTableMemberCatalog.membersFor(structure).map(info => info.memberName -> info).toMap,
      referenceAlias = queryAlias.getOrElse(table.name)
    )

  def resolveTable(
      model: Model,
      schema: SqlSchema,
      queryShape: ShapeId,
      tableRef: String,
      queryKind: InvalidQueryTableReference.Kind
  ): SqlValidated[(SqlTable, StructureShape)] =
    SqlTableMemberCatalog
      .parseShapeId(tableRef)
      .flatMap(SqlTableMemberCatalog.lookupSqlTableStructure(model, _))
      .flatMap { tableStructure =>
        schema.tables.find(_.shapeId == tableStructure.getId).map(table => (table, tableStructure))
      }
      .map(SqlValidated.valid)
      .getOrElse(SqlValidated.invalid(InvalidQueryTableReference(queryShape, tableRef, queryKind)))

  def resolveJoinContexts(
      model: Model,
      schema: SqlSchema,
      queryShape: ShapeId,
      joins: java.util.List[SqlSelectJoinValue],
      primaryContext: TableContext,
      queryKind: InvalidQueryTableReference.Kind
  ): SqlValidated[List[TableContext]] =
    joins.asScala.toList.traverse { join =>
      if (join.getTable == primaryContext.tableRef) {
        SqlValidated.invalid(duplicateJoinError(queryShape, join.getTable, primaryContext.tableRef, queryKind))
      } else {
        resolveTable(model, schema, queryShape, join.getTable, queryKind).map { case (table, structure) =>
          TableContext(
            tableRef = join.getTable,
            shapeId = structure.getId,
            table = table,
            structure = structure,
            membersByName = SqlTableMemberCatalog.membersFor(structure).map(info => info.memberName -> info).toMap,
            referenceAlias = join.getTableAlias.toScala.getOrElse(table.name)
          )
        }
      }
    }

  def resolveJoinModels(
      model: Model,
      queryShape: ShapeId,
      primaryContext: TableContext,
      joinContexts: List[TableContext],
      joinSpecs: java.util.List[SqlSelectJoinValue],
      queryKind: InvalidQueryTableReference.Kind = InvalidQueryTableReference.Kind.DeriveSelect
  ): SqlValidated[List[SqlSelectJoin]] = {
    val deriveTrait = deriveTraitName(queryKind)
    joinContexts.zip(joinSpecs.asScala.toList).zipWithIndex.traverse { case ((joinContext, joinSpec), index) =>
      SqlJoinType.fromString(joinSpec.getType) match {
        case None           =>
          SqlValidated.invalid(InvalidJoinType(queryShape, joinSpec.getType))
        case Some(joinType) =>
          val leftContexts =
            (joinContexts.take(index).reverse :+ primaryContext).map { leftContext =>
              (leftContext.table, leftContext.structure, leftContext.referenceAlias)
            }
          SqlSelectJoinResolver
            .resolveTransitiveJoinCondition(
              queryShape,
              leftContexts,
              joinContext.table,
              joinContext.structure,
              joinType,
              joinContext.referenceAlias,
              deriveTrait
            )
            .map(on =>
              SqlSelectJoin(
                joinType,
                joinContext.table,
                joinSpec.getTableAlias.toScala,
                on
              ))
      }
    }
  }

  private def deriveTraitName(queryKind: InvalidQueryTableReference.Kind): String =
    queryKind match {
      case InvalidQueryTableReference.Kind.DeriveSelectOne => "sqlDeriveSelectOne"
      case InvalidQueryTableReference.Kind.DeriveSelect    => "sqlDeriveSelect"
      case _                                               => "sqlDeriveSelect"
    }

  def validateUniqueAliases(
      queryShape: ShapeId,
      primaryContext: TableContext,
      joinContexts: List[TableContext],
      queryKind: InvalidQueryTableReference.Kind = InvalidQueryTableReference.Kind.DeriveSelect
  ): SqlValidated[Unit] = {
    val aliases = (primaryContext.referenceAlias :: joinContexts.map(_.referenceAlias)).sorted
    aliases
      .groupBy(identity)
      .collect { case (alias, occurrences) if occurrences.size > 1 => alias }
      .toList match {
      case Nil            => ().validNel
      case duplicate :: _ =>
        duplicateAliasError(queryShape, duplicate, queryKind).invalidNel
    }
  }

  private def duplicateAliasError(
      queryShape: ShapeId,
      alias: String,
      queryKind: InvalidQueryTableReference.Kind
  ): SqlSchemaError =
    queryKind match {
      case InvalidQueryTableReference.Kind.DeriveSelectOne =>
        InvalidDeriveSelectOne(queryShape, s"duplicate table alias '$alias'")
      case InvalidQueryTableReference.Kind.DeriveSelect    =>
        InvalidDeriveSelect(queryShape, s"duplicate table alias '$alias'")
      case other                                           =>
        InvalidQueryTableReference(queryShape, alias, other)
    }

  private def duplicateJoinError(
      queryShape: ShapeId,
      joinTable: String,
      primaryTable: String,
      queryKind: InvalidQueryTableReference.Kind
  ): SqlSchemaError =
    queryKind match {
      case InvalidQueryTableReference.Kind.DeriveSelectOne =>
        InvalidDeriveSelectOne(queryShape, s"join table '$joinTable' duplicates primary table '$primaryTable'")
      case InvalidQueryTableReference.Kind.DeriveSelect    =>
        InvalidDeriveSelect(queryShape, s"join table '$joinTable' duplicates primary table '$primaryTable'")
      case other                                           =>
        InvalidQueryTableReference(queryShape, joinTable, other)
    }
}
