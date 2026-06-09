package com.jacoby6000.smithplates.sql.service

import cats.syntax.all.*
import com.jacoby6000.smithplates.sql.*
import com.jacoby6000.smithplates.sql.service.SqlSelectJoinResolver.ResolvedForeignKey
import com.jacoby6000.smithplates.sql.service.SqlSelectTableContext.TableContext
import com.jacoby6000.smithplates.sql.service.traits.SqlDeriveSelectOneTrait
import com.jacoby6000.smithplates.sql.service.traits.SqlSelectJoinValue
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.StructureShape

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

private[service] object SqlDeriveSelectOneExtractor {
  private val DerivedStructShapeId: ShapeId = ShapeId.from("smithplates.codegen.sql#DerivedStruct")

  def extract(
      model: Model,
      schema: SqlSchema,
      operation: OperationShape,
      selectTrait: SqlDeriveSelectOneTrait
  ): SqlValidated[SqlSelectOneQuery] = {
    val queryKind      = InvalidQueryTableReference.Kind.DeriveSelectOne
    val operationShape = operation.getId
    val targetTable    = selectTrait.getTargetTable
    val joinSpecs      = selectTrait.getJoins.asScala.toList

    SqlSelectTableContext
      .resolveTable(model, schema, operationShape, targetTable, queryKind)
      .andThen { case (table, tableStructure) =>
        val primaryContext =
          SqlSelectTableContext.primaryContext(
            targetTable,
            table,
            tableStructure,
            None
          )

        if (joinSpecs.isEmpty) {
          extractWithoutJoins(model, operation, table, tableStructure, operationShape)
        } else {
          extractWithJoins(
            model,
            schema,
            operation,
            table,
            tableStructure,
            operationShape,
            queryKind,
            primaryContext,
            joinSpecs
          )
        }
      }
  }

  private def extractWithoutJoins(
      model: Model,
      operation: OperationShape,
      table: SqlTable,
      tableStructure: StructureShape,
      operationShape: ShapeId
  ): SqlValidated[SqlSelectOneQuery] =
    (
      requireDerivedStructInput(operation),
      requireTableStructureOutput(operation, tableStructure.getId),
      SqlQueryExtractor.deriveSelectOnePrimaryKeyColumns(
        operationShape,
        table.name,
        model,
        tableStructure,
        table
      )
    ).mapN { (_, _, whereColumns) =>
      val primaryColumns =
        SqlTableMemberCatalog.membersFor(tableStructure).map { tableMember =>
          queryColumn(model, tableStructure, table, tableMember)
        }
      SqlSelectOneQuery(
        shapeId = operationShape,
        table = table,
        selectColumns = primaryColumns,
        whereColumns = whereColumns
      )
    }

  private def extractWithJoins(
      model: Model,
      schema: SqlSchema,
      operation: OperationShape,
      table: SqlTable,
      tableStructure: StructureShape,
      operationShape: ShapeId,
      queryKind: InvalidQueryTableReference.Kind,
      primaryContext: TableContext,
      joinSpecs: List[SqlSelectJoinValue]
  ): SqlValidated[SqlSelectOneQuery] =
    (
      requireDerivedStructInput(operation),
      requireDerivedStructOutput(operation),
      SqlQueryExtractor.deriveSelectOnePrimaryKeyColumns(
        operationShape,
        table.name,
        model,
        tableStructure,
        table
      )
    ).mapN { (_, _, whereColumns) =>
      ((), whereColumns)
    }.andThen { case (_, whereColumns) =>
      SqlSelectTableContext
        .resolveJoinContexts(
          model,
          schema,
          operationShape,
          joinSpecs.asJava,
          primaryContext,
          queryKind
        )
        .andThen { joinContexts =>
          SqlSelectTableContext
            .validateUniqueAliases(operationShape, primaryContext, joinContexts, queryKind)
            .andThen { _ =>
              SqlSelectTableContext
                .resolveJoinModels(
                  model,
                  operationShape,
                  primaryContext,
                  joinContexts,
                  joinSpecs.asJava,
                  queryKind
                )
                .andThen { joins =>
                  joinContexts
                    .zip(joinSpecs)
                    .zip(joins)
                    .zipWithIndex
                    .traverse { case (((joinContext, _), _), index) =>
                      val leftContexts =
                        joinContexts.take(index).reverse :+ primaryContext
                      resolveNestedResult(
                        model,
                        schema,
                        operationShape,
                        leftContexts,
                        joinContext
                      )
                    }
                    .map { nestedResults =>
                      val primaryColumns =
                        SqlTableMemberCatalog.membersFor(tableStructure).map { tableMember =>
                          queryColumn(model, tableStructure, table, tableMember)
                        }
                      val selectColumns  =
                        buildSelectColumns(primaryContext, primaryColumns, nestedResults)
                      val adjustedJoins  = adjustJoinTypesForNestedResults(joins, nestedResults)
                      SqlSelectOneQuery(
                        shapeId = operationShape,
                        table = table,
                        tableAlias = Some(primaryContext.referenceAlias),
                        joins = adjustedJoins,
                        selectColumns = primaryColumns,
                        projectedColumns = selectColumns,
                        whereColumns = whereColumns,
                        nestedResults = nestedResults
                      )
                    }
                }
            }
        }
    }

  private def resolveNestedResult(
      model: Model,
      schema: SqlSchema,
      operationShape: ShapeId,
      leftContexts: List[TableContext],
      joinContext: TableContext
  ): SqlValidated[SqlSelectOneNestedResult] =
    leftContexts.foldLeft[Option[SqlValidated[SqlSelectOneNestedResult]]](None) {
      case (resolved @ Some(_), _) =>
        resolved
      case (None, leftContext)     =>
        findForeignKeys(
          leftContext.table,
          leftContext.structure,
          joinContext.table,
          joinContext.structure
        ) match {
          case Nil           => None
          case single :: Nil =>
            Some(
              resolveNestedResultFromForeignKey(
                model,
                schema,
                operationShape,
                leftContext,
                joinContext,
                single
              )
            )
          case _             =>
            Some(
              SqlValidated.invalid(
                AmbiguousJoinForeignKey(
                  operationShape,
                  "sqlDeriveSelectOne",
                  leftContext.table.name,
                  joinContext.table.name
                )
              )
            )
        }
    } match {
      case Some(result) => result
      case None         =>
        val primaryTableName =
          leftContexts.lastOption.map(_.table.name).getOrElse(joinContext.table.name)
        SqlValidated.invalid(
          MissingJoinForeignKey(
            operationShape,
            "sqlDeriveSelectOne",
            primaryTableName,
            joinContext.table.name
          )
        )
    }

  private def resolveNestedResultFromForeignKey(
      model: Model,
      schema: SqlSchema,
      operationShape: ShapeId,
      sourceContext: TableContext,
      joinContext: TableContext,
      foreignKey: ResolvedForeignKey
  ): SqlValidated[SqlSelectOneNestedResult] = {
    val joinColumns =
      SqlTableMemberCatalog.membersFor(joinContext.structure).map { tableMember =>
        queryColumn(model, joinContext.structure, joinContext.table, tableMember)
      }

    if (foreignKey.sourceTable.name == joinContext.table.name &&
      foreignKey.targetTable.name == sourceContext.table.name) {
      val optional = false
      SqlValidated.valid(
        SqlSelectOneNestedResult(
          memberName = pluralMemberName(joinContext.shapeId.getName),
          shapeId = joinContext.shapeId,
          cardinality = SqlSelectOneNestedCardinality.Collection,
          optional = optional,
          table = joinContext.table,
          tableAlias = joinContext.referenceAlias,
          columns = joinColumns
        )
      )
    } else if (foreignKey.sourceTable.name == sourceContext.table.name &&
      foreignKey.targetTable.name == joinContext.table.name) {
      sourceContext.structure.getAllMembers.asScala
        .collectFirst {
          case (memberName, member) if member.sqlColumnName(memberName) == foreignKey.sourceColumn =>
            (memberName, member)
        } match {
        case None           =>
          SqlValidated.invalid(
            InvalidDeriveSelectOne(
              operationShape,
              s"join '${joinContext.tableRef}' is missing foreign key column '${foreignKey.sourceColumn}' on '${sourceContext.tableRef}'"
            )
          )
        case Some(fkMember) =>
          val relationship      =
            schema.relationships.find { relationship =>
              relationship.sourceTable == sourceContext.shapeId &&
              relationship.targetTable == joinContext.shapeId &&
              relationship.sourceColumn == foreignKey.sourceColumn
            }
          val cardinality       =
            relationship
              .map(_.cardinality)
              .getOrElse(SqlRelationshipCardinality.ManyToOne)
          val nestedCardinality =
            cardinality match {
              case SqlRelationshipCardinality.OneToOne  => SqlSelectOneNestedCardinality.Singular
              case SqlRelationshipCardinality.ManyToOne => SqlSelectOneNestedCardinality.Singular
            }
          val fkRequired        = fkMember._2.isRequired
          val optional          = !fkRequired
          SqlValidated.valid(
            SqlSelectOneNestedResult(
              memberName = singularMemberName(joinContext.shapeId.getName),
              shapeId = joinContext.shapeId,
              cardinality = nestedCardinality,
              optional = optional,
              table = joinContext.table,
              tableAlias = joinContext.referenceAlias,
              columns = joinColumns
            )
          )
      }
    } else {
      SqlValidated.invalid(
        InvalidDeriveSelectOne(
          operationShape,
          s"join '${joinContext.tableRef}' has an unsupported foreign key orientation relative to '${sourceContext.tableRef}'"
        )
      )
    }
  }

  private def adjustJoinTypesForNestedResults(
      joins: List[SqlSelectJoin],
      nestedResults: List[SqlSelectOneNestedResult]
  ): List[SqlSelectJoin] =
    joins.zip(nestedResults).map { case (join, nestedResult) =>
      nestedResult.cardinality match {
        case SqlSelectOneNestedCardinality.Collection if join.joinType == SqlJoinType.Inner =>
          join.copy(joinType = SqlJoinType.Left)
        case _                                                                              => join
      }
    }

  private def buildSelectColumns(
      primaryContext: TableContext,
      primaryColumns: List[SqlQueryColumn],
      nestedResults: List[SqlSelectOneNestedResult]
  ): List[SqlSelectOneSelectColumn] = {
    val primarySelect =
      primaryColumns.map { column =>
        SqlSelectOneSelectColumn(
          tableAlias = primaryContext.referenceAlias,
          column = column,
          resultAlias = None
        )
      }
    val nestedSelect  =
      nestedResults.flatMap { nested =>
        nested.columns.map { column =>
          SqlSelectOneSelectColumn(
            tableAlias = nested.tableAlias,
            column = column,
            resultAlias = Some(s"${nested.tableAlias}_${column.columnName}")
          )
        }
      }
    primarySelect ++ nestedSelect
  }

  private def findForeignKeys(
      primaryTable: SqlTable,
      primaryStructure: StructureShape,
      joinTable: SqlTable,
      joinStructure: StructureShape
  ): List[ResolvedForeignKey] =
    SqlSelectJoinResolver.findForeignKeys(primaryTable, primaryStructure, joinTable, joinStructure)

  private def singularMemberName(shapeName: String): String =
    toSnakeCase(shapeName)

  private def pluralMemberName(shapeName: String): String = {
    val singular = toSnakeCase(shapeName)
    if (singular.endsWith("s")) {
      s"${singular}es"
    } else if (singular.endsWith("y") && singular.length > 1 && !"aeiou".contains(singular(singular.length - 2))) {
      s"${singular.dropRight(1)}ies"
    } else {
      s"${singular}s"
    }
  }

  private def toSnakeCase(value: String): String =
    value
      .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
      .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
      .toLowerCase

  private def queryColumn(
      model: Model,
      tableStructure: StructureShape,
      table: SqlTable,
      tableMember: SqlTableMemberCatalog.TableMemberInfo
  ): SqlQueryColumn = {
    val member       = tableStructure.getMember(tableMember.memberName).get()
    val memberType   = SqlIrTypeNameResolver.resolveMember(model, tableMember.memberName, member)
    val jsonTypeName =
      table.columns
        .find(_.name == tableMember.columnName)
        .collect { case column if column.columnType == SqlColumnType.Json => memberType.typeName }
    SqlQueryColumn(
      memberName = tableMember.memberName,
      columnName = tableMember.columnName,
      typeName = memberType.typeName,
      jsonTypeName = jsonTypeName,
      isStructure = memberType.isStructure,
      structureShapeId = memberType.structureShapeId
    )
  }

  private def requireDerivedStructInput(operation: OperationShape): SqlValidated[Unit] = {
    val inputShapeId = Option(operation.getInputShape).getOrElse(ShapeId.from("smithy.api#Unit"))
    if (inputShapeId == DerivedStructShapeId) {
      ().validNel
    } else {
      InvalidDeriveSelectOne(
        operation.getId,
        s"input must be ${DerivedStructShapeId.toString}; codegen expands whereClause from the @sqlDeriveSelectOne targetTable"
      ).invalidNel
    }
  }

  private def requireDerivedStructOutput(operation: OperationShape): SqlValidated[Unit] = {
    val outputShapeId = operation.getOutput.toScala.getOrElse(operation.getOutputShape)
    if (outputShapeId == DerivedStructShapeId) {
      ().validNel
    } else {
      InvalidDeriveSelectOne(
        operation.getId,
        s"output must be ${DerivedStructShapeId.toString} when @sqlDeriveSelectOne declares joins; codegen expands nested join structures"
      ).invalidNel
    }
  }

  private def requireTableStructureOutput(operation: OperationShape, tableShapeId: ShapeId): SqlValidated[Unit] = {
    val outputShapeId = operation.getOutput.toScala.getOrElse(operation.getOutputShape)
    if (outputShapeId == tableShapeId) {
      ().validNel
    } else {
      InvalidDeriveSelectOne(
        operation.getId,
        s"output must be the target @sqlTable structure '${tableShapeId.toString}'; got '${outputShapeId.toString}'"
      ).invalidNel
    }
  }
}
