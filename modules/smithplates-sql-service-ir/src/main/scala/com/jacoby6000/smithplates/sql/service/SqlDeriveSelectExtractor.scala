package com.jacoby6000.smithplates.sql.service

import cats.syntax.all.*
import com.jacoby6000.smithplates.sql.*
import com.jacoby6000.smithplates.sql.model.*
import com.jacoby6000.smithplates.sql.service.SqlDeriveSelectReferenceResolver.ResolvedReference
import com.jacoby6000.smithplates.sql.service.SqlDeriveSelectReferenceResolver.SelectScope
import com.jacoby6000.smithplates.sql.service.SqlSelectTableContext.TableContext
import com.jacoby6000.smithplates.sql.service.traits.SqlDeriveSelectConditionValue
import com.jacoby6000.smithplates.sql.service.traits.SqlDeriveSelectOrderByValue
import com.jacoby6000.smithplates.sql.service.traits.SqlDeriveSelectProjectionValue
import com.jacoby6000.smithplates.sql.service.traits.SqlDeriveSelectProjectionsValue
import com.jacoby6000.smithplates.sql.service.traits.SqlDeriveSelectTrait
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.StructureShape

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

private[service] object SqlDeriveSelectExtractor {
  def extractDeriveSelects(model: Model, schema: SqlSchema): SqlValidated[List[SqlSelectQuery]] =
    model.getOperationShapes.asScala.toList
      .flatMap { operation =>
        operation.sqlDeriveSelect.map(selectTrait => (operation, selectTrait))
      }
      .traverse { case (operation, selectTrait) =>
        internal.extractDeriveSelect(model, schema, operation, selectTrait)
      }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    enum FilterClause {
      case Where
      case Having
    }

    def extractDeriveSelect(
        model: Model,
        schema: SqlSchema,
        operation: OperationShape,
        selectTrait: SqlDeriveSelectTrait
    ): SqlValidated[SqlSelectQuery] = {
      val queryKind      = InvalidQueryTableReference.Kind.DeriveSelect
      val operationShape = operation.getId
      val fromSpec       = selectTrait.getFrom

      requireInputStructure(model, operation).andThen { inputStructure =>
        requireDerivedStructOutput(operation).andThen { _ =>
          SqlSelectTableContext
            .resolveTable(model, schema, operationShape, fromSpec.getTable, queryKind)
            .andThen { case (primaryTable, primaryStructure) =>
              val primaryContext =
                SqlSelectTableContext.primaryContext(
                  fromSpec.getTable,
                  primaryTable,
                  primaryStructure,
                  fromSpec.getAlias.toScala
                )

              SqlSelectTableContext
                .resolveJoinContexts(
                  model,
                  schema,
                  operationShape,
                  selectTrait.getJoins,
                  primaryContext,
                  queryKind
                )
                .andThen { joinContexts =>
                  SqlSelectTableContext
                    .validateUniqueAliases(operationShape, primaryContext, joinContexts)
                    .andThen { _ =>
                      val inputMembers = inputStructure.getAllMembers.asScala.keySet.toSet

                      SqlSelectTableContext
                        .resolveJoinModels(
                          model,
                          operationShape,
                          primaryContext,
                          joinContexts,
                          selectTrait.getJoins
                        )
                        .andThen { joins =>
                          validateStarProjectionsCompatibility(
                            operationShape,
                            selectTrait.getProjections,
                            selectTrait.getGroupBy.asScala.toList,
                            selectTrait.getHaving.asScala.toList,
                            selectTrait.getOrderBy.asScala.toList
                          ).andThen { _ =>
                            extractProjections(
                              operationShape,
                              selectTrait.getProjections,
                              primaryContext,
                              joinContexts
                            ).andThen { selectColumns =>
                              val scope =
                                SelectScope(
                                  primary = primaryContext,
                                  joins = joinContexts,
                                  inputMembers = inputMembers,
                                  projections = selectColumns.map(p => p.resultAlias -> p).toMap
                                )

                              (
                                extractConditions(
                                  operationShape,
                                  selectTrait.getWhere.asScala.toList,
                                  scope,
                                  FilterClause.Where
                                ),
                                extractGroupBy(operationShape, selectTrait.getGroupBy.asScala.toList, scope),
                                extractConditions(
                                  operationShape,
                                  selectTrait.getHaving.asScala.toList,
                                  scope,
                                  FilterClause.Having
                                ),
                                extractOrderBy(operationShape, selectTrait.getOrderBy.asScala.toList, selectColumns),
                                validateOptionalInputMember(
                                  operationShape,
                                  selectTrait.getLimitInputMember.toScala,
                                  inputMembers,
                                  "limitInputMember"
                                ),
                                validateOptionalInputMember(
                                  operationShape,
                                  selectTrait.getOffsetInputMember.toScala,
                                  inputMembers,
                                  "offsetInputMember"
                                )
                              ).mapN {
                                (
                                    wherePredicates,
                                    groupByColumns,
                                    havingPredicates,
                                    orderBy,
                                    limitInputMember,
                                    offsetInputMember
                                ) =>
                                  (
                                    validateGroupByCoversSelectColumns(
                                      operationShape,
                                      selectColumns,
                                      groupByColumns
                                    ),
                                    validateAtLeastOneProjection(operationShape, selectColumns)
                                  ).mapN { (_, _) =>
                                    SqlSelectQuery(
                                      shapeId = operationShape,
                                      primaryTable = primaryTable,
                                      primaryTableAlias = fromSpec.getAlias.toScala,
                                      joins = joins,
                                      selectColumns = selectColumns,
                                      wherePredicates = wherePredicates,
                                      groupByColumns = groupByColumns,
                                      havingPredicates = havingPredicates,
                                      orderBy = orderBy,
                                      limitInputMember = limitInputMember,
                                      offsetInputMember = offsetInputMember
                                    )
                                  }
                              }.andThen(identity)
                            }
                          }
                        }
                    }
                }
            }
        }
      }
    }

    def requireInputStructure(
        model: Model,
        operation: OperationShape
    ): SqlValidated[StructureShape] = {
      val operationShape = operation.getId
      val unitShapeId    = ShapeId.from("smithy.api#Unit")
      val inputShapeId   = Option(operation.getInputShape).getOrElse(unitShapeId)

      if (inputShapeId == unitShapeId) {
        InvalidDeriveSelect(operationShape, "input must be a structure").invalidNel
      } else if (inputShapeId == SqlQueryExtractor.DerivedStructShapeId) {
        InvalidDeriveSelect(
          operationShape,
          s"input must be a structure with bind-parameter members; not ${SqlQueryExtractor.DerivedStructShapeId.toString}"
        ).invalidNel
      } else {
        model.getShape(inputShapeId).toScala.flatMap(_.asStructureShape.toScala) match {
          case Some(structure) => structure.validNel
          case None            =>
            InvalidDeriveSelect(
              operationShape,
              s"input must be a structure; got '${inputShapeId.toString}'"
            ).invalidNel
        }
      }
    }

    def requireDerivedStructOutput(operation: OperationShape): SqlValidated[Unit] = {
      val operationShape = operation.getId
      val unitShapeId    = ShapeId.from("smithy.api#Unit")
      val outputShapeId  = Option(operation.getOutputShape).getOrElse(unitShapeId)

      if (outputShapeId == SqlQueryExtractor.DerivedStructShapeId) {
        ().validNel
      } else {
        InvalidDeriveSelect(
          operationShape,
          s"output must be ${SqlQueryExtractor.DerivedStructShapeId.toString}; got '${outputShapeId.toString}'"
        ).invalidNel
      }
    }

    def validateOptionalInputMember(
        operationShape: ShapeId,
        memberName: Option[String],
        inputMembers: Set[String],
        traitField: String
    ): SqlValidated[Option[String]] =
      memberName match {
        case None                                      => None.validNel
        case Some(name) if inputMembers.contains(name) =>
          Some(name).validNel
        case Some(name)                                =>
          InvalidDeriveSelect(
            operationShape,
            s"$traitField '$name' is not a member of the operation input structure"
          ).invalidNel
      }

    def validateStarProjectionsCompatibility(
        operationShape: ShapeId,
        projections: SqlDeriveSelectProjectionsValue,
        groupBy: List[String],
        having: List[SqlDeriveSelectConditionValue],
        orderBy: List[SqlDeriveSelectOrderByValue]
    ): SqlValidated[Unit] =
      if (!projections.isAllColumns) {
        ().validNel
      } else {
        val incompatibleFields =
          List(
            Option.when(groupBy.nonEmpty)("groupBy"),
            Option.when(having.nonEmpty)("having"),
            Option.when(orderBy.nonEmpty)("orderBy")
          ).flatten

        incompatibleFields match {
          case Nil    => ().validNel
          case fields =>
            InvalidDeriveSelect(
              operationShape,
              s"projections \"*\" selects all table columns and cannot be used with ${fields.mkString(", ")}; provide an explicit projection list"
            ).invalidNel
        }
      }

    def extractProjections(
        operationShape: ShapeId,
        projections: SqlDeriveSelectProjectionsValue,
        primary: TableContext,
        joins: List[TableContext]
    ): SqlValidated[List[SqlSelectProjection]] =
      if (projections.isAllColumns) {
        expandAllProjections(operationShape, primary, joins)
      } else {
        val emptyScope =
          SelectScope(
            primary = primary,
            joins = joins,
            inputMembers = Set.empty,
            projections = Map.empty
          )

        projections.getExplicit.asScala.toList.traverse(projection =>
          extractProjection(operationShape, projection, emptyScope))
      }

    def expandAllProjections(
        operationShape: ShapeId,
        primary: TableContext,
        joins: List[TableContext]
    ): SqlValidated[List[SqlSelectProjection]] = {
      val tables   = primary :: joins
      val expanded =
        tables.flatMap { context =>
          SqlTableMemberCatalog.membersFor(context.structure).map { member =>
            val resultAlias = s"${context.referenceAlias}_${member.memberName}"
            val column      = SqlQualifiedColumn(context.referenceAlias, member.columnName)
            SqlSelectColumnProjection(resultAlias, column)
          }
        }

      val duplicateAliases =
        expanded
          .groupBy(_.resultAlias)
          .collect { case (alias, occurrences) if occurrences.size > 1 => alias }
          .toList

      duplicateAliases match {
        case Nil if expanded.nonEmpty => expanded.validNel
        case Nil                      =>
          InvalidDeriveSelect(operationShape, "projections \"*\" found no table columns to select").invalidNel
        case alias :: _               =>
          InvalidDeriveSelect(
            operationShape,
            s"projections \"*\" produced duplicate result alias '$alias'"
          ).invalidNel
      }
    }

    def extractProjection(
        operationShape: ShapeId,
        projection: SqlDeriveSelectProjectionValue,
        scope: SelectScope
    ): SqlValidated[SqlSelectProjection] = {
      val alias = projection.getAlias
      projection.getAggregate.toScala match {
        case None               =>
          SqlDeriveSelectReferenceResolver
            .resolveTableColumn(operationShape, projection.getSource, scope)
            .map(column => SqlSelectColumnProjection(alias, column))
        case Some(functionName) =>
          SqlAggregateFunction.fromString(functionName) match {
            case None           =>
              InvalidDeriveSelect(
                operationShape,
                s"projection '$alias' has unsupported aggregate function '$functionName'"
              ).invalidNel
            case Some(function) =>
              SqlDeriveSelectReferenceResolver
                .resolveTableColumn(operationShape, projection.getSource, scope)
                .match {
                  case cats.data.Validated.Valid(columnValue) =>
                    SqlSelectAggregateProjection(alias, function, Some(columnValue)).validNel
                  case cats.data.Validated.Invalid(errors)    =>
                    if (function == SqlAggregateFunction.Count && projection.getSource.trim == "*") {
                      SqlSelectAggregateProjection(alias, function, None).validNel
                    } else {
                      errors.map(error => InvalidDeriveSelect(operationShape, error.message)).invalid
                    }
                }
          }
      }
    }

    def extractGroupBy(
        operationShape: ShapeId,
        groupByEntries: List[String],
        scope: SelectScope
    ): SqlValidated[List[SqlSelectGroupBy]] =
      groupByEntries.traverse { reference =>
        SqlDeriveSelectReferenceResolver
          .resolveTableColumn(operationShape, reference, scope)
          .map(column => SqlSelectGroupBy(column))
          .leftMap(_.map {
            case error: InvalidDeriveSelect =>
              InvalidGroupByColumn(operationShape, reference, scope.primary.tableRef, reference)
            case other                      =>
              other
          })
      }

    def extractConditions(
        operationShape: ShapeId,
        conditions: List[SqlDeriveSelectConditionValue],
        scope: SelectScope,
        clause: FilterClause
    ): SqlValidated[List[SqlSelectPredicate]] =
      conditions.traverse { condition =>
        SqlComparisonOperator.fromString(condition.getOperator) match {
          case None           =>
            InvalidDeriveSelect(
              operationShape,
              s"${clauseName(clause)} condition has unsupported operator '${condition.getOperator}'"
            ).invalidNel
          case Some(operator) =>
            val allowProjection = clause == FilterClause.Having
            (
              resolveConditionOperand(
                operationShape,
                condition.getLeft,
                scope,
                clause,
                allowProjection = allowProjection
              ),
              resolveConditionOperand(
                operationShape,
                condition.getRight,
                scope,
                clause,
                allowProjection = allowProjection
              )
            ).mapN { (left, right) =>
              validateConditionOperands(operationShape, left, right, clause).map(_ =>
                SqlSelectPredicate(left = left, operator = operator, right = right))
            }.andThen(identity)
        }
      }

    def resolveConditionOperand(
        operationShape: ShapeId,
        reference: String,
        scope: SelectScope,
        clause: FilterClause,
        allowProjection: Boolean
    ): SqlValidated[SqlPredicateOperand] =
      SqlDeriveSelectReferenceResolver
        .resolveConditionSide(operationShape, reference, scope, allowProjection)
        .andThen {
          case ResolvedReference.InputMember(name)                                      =>
            SqlPredicateOperand.InputMember(name).validNel
          case ResolvedReference.TableColumn(column)                                    =>
            SqlPredicateOperand.TableColumn(column).validNel
          case ResolvedReference.Projection(projection) if clause == FilterClause.Where =>
            InvalidWhereColumn(
              operationShape,
              reference,
              s"must not reference projection '${projection.resultAlias}'"
            ).invalidNel
          case ResolvedReference.Projection(projection)                                 =>
            SqlPredicateOperand.Projection(projection).validNel
        }

    def validateConditionOperands(
        operationShape: ShapeId,
        left: SqlPredicateOperand,
        right: SqlPredicateOperand,
        clause: FilterClause
    ): SqlValidated[Unit] =
      (left, right, clause) match {
        case (SqlPredicateOperand.Projection(projection), _, FilterClause.Where)                            =>
          invalidCondition(
            operationShape,
            projection.resultAlias,
            clause,
            s"must not reference projection '${projection.resultAlias}'"
          ).map(_ => ())
        case (SqlPredicateOperand.Projection(projection), _, FilterClause.Having) if projection.isAggregate =>
          ().validNel
        case (_, SqlPredicateOperand.Projection(_), FilterClause.Where)                                     =>
          invalidCondition(
            operationShape,
            "right",
            clause,
            "must not reference projections"
          ).map(_ => ())
        case _                                                                                              =>
          ().validNel
      }

    def extractOrderBy(
        operationShape: ShapeId,
        orderByEntries: List[SqlDeriveSelectOrderByValue],
        selectColumns: List[SqlSelectProjection]
    ): SqlValidated[List[SqlSelectOrderBy]] = {
      val projectionByAlias = selectColumns.map(projection => projection.resultAlias -> projection).toMap

      orderByEntries.traverse { entry =>
        SqlSortDirection.fromString(entry.getDirection) match {
          case None            =>
            InvalidDeriveSelect(
              operationShape,
              s"orderBy projection '${entry.getProjection}' has unsupported direction '${entry.getDirection}'"
            ).invalidNel
          case Some(direction) =>
            projectionByAlias.get(entry.getProjection) match {
              case None    =>
                InvalidDeriveSelect(
                  operationShape,
                  s"orderBy references unknown projection '${entry.getProjection}'"
                ).invalidNel
              case Some(_) =>
                SqlSelectOrderBy(entry.getProjection, direction).validNel
            }
        }
      }
    }

    def validateGroupByCoversSelectColumns(
        operationShape: ShapeId,
        selectColumns: List[SqlSelectProjection],
        groupByColumns: List[SqlSelectGroupBy]
    ): SqlValidated[Unit] =
      if (groupByColumns.isEmpty) {
        ().validNel
      } else {
        val groupByKeys = groupByColumns.map(groupBy => (groupBy.column.tableAlias, groupBy.column.columnName)).toSet
        selectColumns
          .collect { case projection: SqlSelectColumnProjection => projection }
          .traverse { projection =>
            val key = (projection.column.tableAlias, projection.column.columnName)
            if (groupByKeys.contains(key)) {
              ().validNel
            } else {
              SelectGroupByMissingColumn(operationShape, projection.resultAlias).invalidNel
            }
          }
          .map(_ => ())
      }

    def validateAtLeastOneProjection(
        operationShape: ShapeId,
        selectColumns: List[SqlSelectProjection]
    ): SqlValidated[Unit] =
      if (selectColumns.isEmpty) {
        InvalidDeriveSelect(operationShape, "projections must contain at least one entry").invalidNel
      } else {
        ().validNel
      }

    def clauseName(clause: FilterClause): String =
      clause match {
        case FilterClause.Where  => "where"
        case FilterClause.Having => "having"
      }

    def invalidCondition(
        operationShape: ShapeId,
        reference: String,
        clause: FilterClause,
        reason: String
    ): SqlValidated[Unit] =
      clause match {
        case FilterClause.Where  => InvalidWhereColumn(operationShape, reference, reason).invalidNel
        case FilterClause.Having => InvalidHavingColumn(operationShape, reference, reason).invalidNel
      }
  }
}
