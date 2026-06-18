package com.jacoby6000.smithplates.sql.service.renderer

import cats.syntax.all.*
import com.jacoby6000.smithplates.sql.*
import com.jacoby6000.smithplates.sql.model.*
import com.jacoby6000.smithplates.sql.service.SqlOperation
import com.jacoby6000.smithplates.sql.service.SqlQueries
import com.jacoby6000.smithplates.sql.service.SqlQueryColumn
import com.jacoby6000.smithplates.sql.service.codegen.ResolvedSqlOperationQuery
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlParameterizedStatement
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlQueryRenderer
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ShapeId

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

object SqlOperationBindingBuilder {
  private val BooleanShapeId: ShapeId = ShapeId.from("smithy.api#Boolean")

  def build(
      model: Model,
      operation: SqlOperation,
      query: ResolvedSqlOperationQuery,
      queryRenderer: SqlQueryRenderer
  ): SqlValidated[SqlCodegenSqlBinding] =
    buildBinding(model, operation, query, queryRenderer)

  def parametersFromQuery(
      operation: SqlOperation,
      query: ResolvedSqlOperationQuery
  ): SqlValidated[List[SqlCodegenParameter]] =
    query match {
      case ResolvedSqlOperationQuery.Insert(insertQuery)       =>
        parametersForColumns(insertQuery.columns)
      case ResolvedSqlOperationQuery.Update(updateQuery)       =>
        parametersForColumns(updateQuery.setColumns ++ updateQuery.whereColumns)
      case ResolvedSqlOperationQuery.Delete(deleteQuery)       =>
        parametersForColumns(deleteQuery.whereColumns)
      case ResolvedSqlOperationQuery.SelectOne(selectOneQuery) =>
        parametersForColumns(selectOneQuery.whereColumns)
      case ResolvedSqlOperationQuery.Select(_)                 =>
        InvalidPluginConfig(
          s"@sqlDeriveSelect on ${operation.shapeId.toString} is not supported for aiosqlite implementation generation"
        ).invalidNel
    }

  private def buildBinding(
      model: Model,
      operation: SqlOperation,
      query: ResolvedSqlOperationQuery,
      queryRenderer: SqlQueryRenderer
  ): SqlValidated[SqlCodegenSqlBinding] =
    query match {
      case ResolvedSqlOperationQuery.Select(_) =>
        InvalidPluginConfig(
          s"sql-service-codegen does not yet generate aiosqlite implementations for @sqlDeriveSelect on ${operation.shapeId.toString}"
        ).invalidNel
      case other                               =>
        val statement: SqlParameterizedStatement =
          queryRenderer
            .renderQueryUnits(SqlQueriesAdapter.fromResolved(other))
            .headOption
            .map(_.statement)
            .getOrElse(SqlParameterizedStatement(List("")))

        other match {
          case ResolvedSqlOperationQuery.Insert(insertQuery)       =>
            buildInsertBinding(insertQuery, statement).validNel
          case ResolvedSqlOperationQuery.Update(updateQuery)       =>
            buildUpdateBinding(model, operation, updateQuery, statement).validNel
          case ResolvedSqlOperationQuery.Delete(deleteQuery)       =>
            buildDeleteBinding(model, operation, deleteQuery, statement).validNel
          case ResolvedSqlOperationQuery.SelectOne(selectOneQuery) =>
            buildSelectOneBinding(selectOneQuery, statement, operation.name).validNel
          case ResolvedSqlOperationQuery.Select(_)                 =>
            InvalidPluginConfig("unreachable select branch").invalidNel
        }
    }

  private def buildInsertBinding(
      query: com.jacoby6000.smithplates.sql.service.SqlInsertQuery,
      statement: SqlParameterizedStatement
  ): SqlCodegenSqlBinding = {
    val bindParameters = query.columns.map(column => columnToBindParameter(query.table, column))
    val resultFields   =
      query.returningColumns.zipWithIndex.map { case (column, index) =>
        columnToResultField(query.table, column, index)
      }
    SqlCodegenSqlBinding(
      queryKind = "insert",
      sqlStatement = statement,
      tableName = query.table.name,
      bindParameters = bindParameters,
      executionMode = "fetchone",
      outputKind = "structure",
      returningColumnIndex = None,
      resultFields = resultFields
    )
  }

  private def buildUpdateBinding(
      model: Model,
      operation: SqlOperation,
      query: com.jacoby6000.smithplates.sql.service.SqlUpdateQuery,
      statement: SqlParameterizedStatement
  ): SqlCodegenSqlBinding = {
    val resultFields =
      query.returningColumns.zipWithIndex.map { case (column, index) =>
        columnToResultField(query.table, column, index)
      }
    val hasReturning = query.returningColumns.nonEmpty
    SqlCodegenSqlBinding(
      queryKind = "update",
      sqlStatement = statement,
      tableName = query.table.name,
      bindParameters =
        (query.setColumns ++ query.whereColumns).map(column => columnToBindParameter(query.table, column)),
      executionMode = if (hasReturning) "fetchone" else "rowcount",
      outputKind = "structure",
      returningColumnIndex = None,
      resultFields = resultFields,
      mutationResultMemberName = if (hasReturning) None else mutationResultMemberName(model, operation)
    )
  }

  private def buildDeleteBinding(
      model: Model,
      operation: SqlOperation,
      query: com.jacoby6000.smithplates.sql.service.SqlDeleteQuery,
      statement: SqlParameterizedStatement
  ): SqlCodegenSqlBinding =
    SqlCodegenSqlBinding(
      queryKind = "delete",
      sqlStatement = statement,
      tableName = query.table.name,
      bindParameters = query.whereColumns.map(column => columnToBindParameter(query.table, column)),
      executionMode = "fetchone",
      outputKind = "structure",
      returningColumnIndex = None,
      resultFields = Nil,
      mutationResultMemberName = mutationResultMemberName(model, operation)
    )

  private def mutationResultMemberName(model: Model, operation: SqlOperation): Option[String] =
    operation.outputShape.flatMap { outputShapeId =>
      model
        .getShape(outputShapeId)
        .toScala
        .collect {
          case shape if shape.isStructureShape =>
            shape.asStructureShape.get().getAllMembers.asScala.toList.collectFirst {
              case (memberName, member) if member.isRequired && member.getTarget == BooleanShapeId =>
                memberName
            }
        }
        .flatten
    }

  private def buildSelectOneBinding(
      query: com.jacoby6000.smithplates.sql.service.SqlSelectOneQuery,
      statement: SqlParameterizedStatement,
      operationName: String
  ): SqlCodegenSqlBinding =
    if (query.hasNestedResults) {
      val derived = SqlSelectOneDerivedOutputBuilder.build(operationName, query)
      SqlCodegenSqlBinding(
        queryKind = "selectOne",
        sqlStatement = statement,
        tableName = query.table.name,
        bindParameters = query.whereColumns.map(column => columnToBindParameter(query.table, column)),
        executionMode = if (derived.binding.hasCollectionJoin) "fetchall" else "fetchone",
        outputKind = "structure",
        returningColumnIndex = None,
        resultFields = derived.binding.primaryFields,
        selectOneOutput = Some(derived.binding)
      )
    } else {
      val resultFields =
        query.selectColumns.zipWithIndex.map { case (column, index) =>
          columnToResultField(query.table, column, index)
        }
      SqlCodegenSqlBinding(
        queryKind = "selectOne",
        sqlStatement = statement,
        tableName = query.table.name,
        bindParameters = query.whereColumns.map(column => columnToBindParameter(query.table, column)),
        executionMode = "fetchone",
        outputKind = "structure",
        returningColumnIndex = None,
        resultFields = resultFields
      )
    }

  private def parametersForColumns(columns: List[SqlQueryColumn]): SqlValidated[List[SqlCodegenParameter]] =
    columns.map(queryColumnToParameter).validNel

  private def queryColumnToParameter(column: SqlQueryColumn): SqlCodegenParameter =
    SqlCodegenParameter(
      name = column.memberName,
      typeName = column.typeName,
      optional = column.nullable,
      isStructure = column.isStructure,
      structureShapeId = column.structureShapeId
    )

  private def columnToBindParameter(table: SqlTable, column: SqlQueryColumn): SqlCodegenBindParameter = {
    val columnType                = tableColumnType(table, column.columnName)
    val (isJson, timestampFormat) = columnBindingMetadata(columnType)
    SqlCodegenBindParameter(
      memberName = column.memberName,
      typeName = column.typeName,
      isJson = isJson,
      jsonTypeName = column.jsonTypeName,
      timestampFormat = timestampFormat
    )
  }

  private def columnToResultField(
      table: SqlTable,
      column: SqlQueryColumn,
      columnIndex: Int
  ): SqlCodegenResultField = {
    val columnType                = tableColumnType(table, column.columnName)
    val (isJson, timestampFormat) = columnBindingMetadata(columnType)
    SqlCodegenResultField(
      fieldName = column.memberName,
      columnName = column.columnName,
      columnIndex = columnIndex,
      typeName = column.typeName,
      readTypeName = rowReadTypeName(column.typeName, columnType),
      isJson = isJson,
      timestampFormat = timestampFormat
    )
  }

  private def rowReadTypeName(typeName: String, columnType: Option[SqlColumnType]): String =
    columnType match {
      case Some(_: SqlColumnType.StringEnum) => "String"
      case Some(_: SqlColumnType.IntEnum)    => "Integer"
      case _                                 => typeName
    }

  private def tableColumnType(table: SqlTable, columnName: String): Option[SqlColumnType] =
    table.columns.find(_.name == columnName).map(_.columnType)

  private def columnBindingMetadata(
      columnType: Option[SqlColumnType]
  ): (Boolean, Option[SqlTimestampFormat]) =
    columnType match {
      case Some(SqlColumnType.Json)              => (true, None)
      case Some(SqlColumnType.Timestamp(format)) => (false, Some(format))
      case Some(_)                               => (false, None)
      case None                                  => (false, None)
    }

  private object SqlQueriesAdapter {
    def fromResolved(query: ResolvedSqlOperationQuery): SqlQueries =
      query match {
        case ResolvedSqlOperationQuery.Insert(insertQuery)       =>
          SqlQueries(inserts = List(insertQuery))
        case ResolvedSqlOperationQuery.Update(updateQuery)       =>
          SqlQueries(updates = List(updateQuery))
        case ResolvedSqlOperationQuery.Delete(deleteQuery)       =>
          SqlQueries(deletes = List(deleteQuery))
        case ResolvedSqlOperationQuery.SelectOne(selectOneQuery) =>
          SqlQueries(selectOnes = List(selectOneQuery))
        case ResolvedSqlOperationQuery.Select(selectQuery)       =>
          SqlQueries(selects = List(selectQuery))
      }
  }
}
