package com.jacoby6000.smithy.stache.sql

import cats.syntax.all.*
import com.jacoby6000.smithy.stache.sql.SqlSelectTableContext.TableContext
import software.amazon.smithy.model.shapes.ShapeId

/** Resolves `input.member`, `alias.column`, projection aliases, and bare column names. */
private[sql] object SqlDeriveSelectReferenceResolver {
  private val InputPrefix = "input"

  final case class SelectScope(
      primary: TableContext,
      joins: List[TableContext],
      inputMembers: Set[String],
      projections: Map[String, SqlSelectProjection]
  ) {
    lazy val tables: List[TableContext] = primary :: joins

    lazy val aliasToContext: Map[String, TableContext] =
      tables.map(context => context.referenceAlias -> context).toMap
  }

  sealed trait ResolvedReference

  object ResolvedReference {
    final case class InputMember(name: String) extends ResolvedReference
    final case class TableColumn(column: SqlQualifiedColumn) extends ResolvedReference
    final case class Projection(projection: SqlSelectProjection) extends ResolvedReference
  }

  def resolveTableColumn(
      queryShape: ShapeId,
      reference: String,
      scope: SelectScope
  ): SqlValidated[SqlQualifiedColumn] =
    resolveReference(queryShape, reference, scope, allowProjection = false).andThen {
      case ResolvedReference.TableColumn(column) => column.validNel
      case ResolvedReference.InputMember(name) =>
        InvalidDeriveSelect(queryShape, s"reference '$reference' is an input member, not a table column")
          .invalidNel
      case ResolvedReference.Projection(projection) =>
        InvalidDeriveSelect(
          queryShape,
          s"reference '$reference' is projection '${projection.resultAlias}', not a table column"
        ).invalidNel
    }

  def resolveConditionSide(
      queryShape: ShapeId,
      reference: String,
      scope: SelectScope,
      allowProjection: Boolean
  ): SqlValidated[ResolvedReference] =
    resolveReference(queryShape, reference, scope, allowProjection)

  private def resolveReference(
      queryShape: ShapeId,
      reference: String,
      scope: SelectScope,
      allowProjection: Boolean
  ): SqlValidated[ResolvedReference] = {
    val trimmed = reference.trim
    if (trimmed.isEmpty) {
      InvalidDeriveSelect(queryShape, "reference must not be empty").invalidNel
    } else if (trimmed.startsWith(s"$InputPrefix.")) {
      resolveInputMember(queryShape, trimmed, scope.inputMembers)
    } else if (allowProjection && scope.projections.contains(trimmed)) {
      ResolvedReference.Projection(scope.projections(trimmed)).validNel
    } else {
      resolveTableColumnReference(queryShape, trimmed, scope)
        .map(ResolvedReference.TableColumn(_))
    }
  }

  private def resolveInputMember(
      queryShape: ShapeId,
      reference: String,
      inputMembers: Set[String]
  ): SqlValidated[ResolvedReference] = {
    val memberName = reference.stripPrefix(s"$InputPrefix.")
    if (memberName.isEmpty || memberName.contains('.')) {
      InvalidDeriveSelect(
        queryShape,
        s"reference '$reference' must be '$InputPrefix.<memberName>'"
      ).invalidNel
    } else if (inputMembers.contains(memberName)) {
      ResolvedReference.InputMember(memberName).validNel
    } else {
      InvalidDeriveSelect(
        queryShape,
        s"input member '$memberName' is not declared on the operation input structure"
      ).invalidNel
    }
  }

  private def resolveTableColumnReference(
      queryShape: ShapeId,
      reference: String,
      scope: SelectScope
  ): SqlValidated[SqlQualifiedColumn] =
    reference.indexOf('.') match {
      case -1 =>
        resolveBareColumnReference(queryShape, reference, scope)
      case dotIndex =>
        val alias = reference.substring(0, dotIndex)
        val columnName = reference.substring(dotIndex + 1)
        if (alias == InputPrefix) {
          InvalidDeriveSelect(
            queryShape,
            s"reference '$reference' uses reserved prefix '$InputPrefix'; use '$InputPrefix.$columnName'"
          ).invalidNel
        } else if (columnName.isEmpty) {
          InvalidDeriveSelect(queryShape, s"reference '$reference' is missing a column name").invalidNel
        } else {
          resolveAliasedColumn(queryShape, alias, columnName, scope)
        }
    }

  private def resolveAliasedColumn(
      queryShape: ShapeId,
      alias: String,
      columnName: String,
      scope: SelectScope
  ): SqlValidated[SqlQualifiedColumn] =
    scope.aliasToContext.get(alias) match {
      case None =>
        InvalidDeriveSelect(queryShape, s"unknown table alias '$alias' in reference '$alias.$columnName'")
          .invalidNel
      case Some(context) =>
        lookupColumnInContext(queryShape, s"$alias.$columnName", context, columnName)
    }

  private def resolveBareColumnReference(
      queryShape: ShapeId,
      columnName: String,
      scope: SelectScope
  ): SqlValidated[SqlQualifiedColumn] = {
    val matches =
      scope.tables.flatMap(context => lookupColumnInContext(queryShape, columnName, context, columnName).toOption)

    matches match {
      case single :: Nil => single.validNel
      case _ :: _ =>
        InvalidDeriveSelect(
          queryShape,
          s"reference '$columnName' is ambiguous across tables; qualify it as alias.$columnName"
        ).invalidNel
      case Nil =>
        InvalidDeriveSelect(
          queryShape,
          s"reference '$columnName' does not match any table column on from/joins"
        ).invalidNel
    }
  }

  private def lookupColumnInContext(
      queryShape: ShapeId,
      reference: String,
      context: TableContext,
      columnName: String
  ): SqlValidated[SqlQualifiedColumn] = {
    val memberByName = context.membersByName.get(columnName)
    val memberByPhysicalName =
      context.membersByName.values.find(_.columnName == columnName)

    (memberByName, memberByPhysicalName) match {
      case (Some(member), _) =>
        SqlQualifiedColumn(context.referenceAlias, member.columnName).validNel
      case (None, Some(member)) =>
        SqlQualifiedColumn(context.referenceAlias, member.columnName).validNel
      case (None, None) =>
        InvalidDeriveSelect(
          queryShape,
          s"reference '$reference' does not match any column on table alias '${context.referenceAlias}'"
        ).invalidNel
    }
  }
}
