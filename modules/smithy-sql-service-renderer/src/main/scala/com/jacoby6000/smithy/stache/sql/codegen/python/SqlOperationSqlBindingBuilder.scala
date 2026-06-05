package com.jacoby6000.smithy.stache.sql.codegen.python

import cats.syntax.all.*
import com.jacoby6000.smithy.stache.sql.*
import com.jacoby6000.smithy.stache.sql.codegen.*
import com.jacoby6000.smithy.stache.sql.service.SqlOperation
import com.jacoby6000.smithy.stache.sql.service.SqlQueries
import com.jacoby6000.smithy.stache.sql.service.SqlQueryColumn
import com.jacoby6000.smithy.stache.sql.service.codegen.ResolvedSqlOperationQuery
import com.jacoby6000.smithy.stache.sql.service.shared.SqlQueryRenderer
import com.jacoby6000.smithy.stache.sql.shared.SqlParameterizedStatement
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.StructureShape

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

object SqlOperationSqlBindingBuilder {
  def build(
      model: Model,
      operation: SqlOperation,
      query: ResolvedSqlOperationQuery
  ): SqlValidated[SqlCodegenSqlBinding] =
    query match {
      case ResolvedSqlOperationQuery.Select(_) =>
        InvalidPluginConfig(
          s"sql-service-codegen does not yet generate aiosqlite implementations for @sqlDeriveSelect on ${operation.shapeId.toString}"
        ).invalidNel
      case other                               =>
        val statement: SqlParameterizedStatement =
          SqlQueryRenderer
            .renderQueryUnits(
              SqlQueriesAdapter.fromResolved(other)
            )
            .headOption
            .map(_.statement)
            .getOrElse(SqlParameterizedStatement(List("")))

        other match {
          case ResolvedSqlOperationQuery.Insert(insertQuery)       =>
            buildInsertBinding(model, operation, insertQuery, statement).validNel
          case ResolvedSqlOperationQuery.Update(updateQuery)       =>
            buildUpdateBinding(model, updateQuery, statement).validNel
          case ResolvedSqlOperationQuery.Delete(deleteQuery)       =>
            buildDeleteBinding(model, deleteQuery, statement).validNel
          case ResolvedSqlOperationQuery.SelectOne(selectOneQuery) =>
            buildSelectOneBinding(model, operation, selectOneQuery, statement).validNel
          case ResolvedSqlOperationQuery.Select(_)                 =>
            InvalidPluginConfig("unreachable select branch").invalidNel
        }
    }

  def parametersFromQuery(
      model: Model,
      operation: SqlOperation,
      query: ResolvedSqlOperationQuery
  ): SqlValidated[List[SqlCodegenParameter]] =
    query match {
      case ResolvedSqlOperationQuery.Insert(insertQuery)       =>
        parametersForColumns(model, insertQuery.table.shapeId, insertQuery.columns)
      case ResolvedSqlOperationQuery.Update(updateQuery)       =>
        parametersForColumns(model, updateQuery.table.shapeId, updateQuery.setColumns ++ updateQuery.whereColumns)
      case ResolvedSqlOperationQuery.Delete(deleteQuery)       =>
        parametersForColumns(model, deleteQuery.table.shapeId, deleteQuery.whereColumns)
      case ResolvedSqlOperationQuery.SelectOne(selectOneQuery) =>
        parametersForColumns(model, selectOneQuery.table.shapeId, selectOneQuery.whereColumns)
      case ResolvedSqlOperationQuery.Select(_)                 =>
        InvalidPluginConfig(
          s"@sqlDeriveSelect on ${operation.shapeId.toString} is not supported for aiosqlite implementation generation"
        ).invalidNel
    }

  private def buildInsertBinding(
      model: Model,
      operation: SqlOperation,
      query: com.jacoby6000.smithy.stache.sql.service.SqlInsertQuery,
      statement: SqlParameterizedStatement
  ): SqlCodegenSqlBinding = {
    val bindParameters = query.columns.map(column => columnToBindParameter(model, query.table.shapeId, column))
    val outputShapeId  = operation.outputShape.getOrElse(SqlCodegenTypeResolver.UnitShapeId)

    if (isPreludeShape(outputShapeId)) {
      SqlCodegenSqlBinding(
        queryKind = "insert",
        sqlStatement = statement,
        tableName = query.table.name,
        bindParameters = bindParameters,
        executionMode = "fetchone",
        outputKind = "scalar",
        returningColumnIndex = query.returningColumns.headOption.map(_ => 0),
        resultFields = Nil,
        notFoundErrorClassName = None
      )
    } else {
      val resultFields =
        query.returningColumns.zipWithIndex.flatMap { case (columnName, index) =>
          columnNameToResultField(model, query.table.shapeId, columnName, index)
        }
      SqlCodegenSqlBinding(
        queryKind = "insert",
        sqlStatement = statement,
        tableName = query.table.name,
        bindParameters = bindParameters,
        executionMode = "fetchone",
        outputKind = "structure",
        returningColumnIndex = None,
        resultFields = resultFields,
        notFoundErrorClassName = None
      )
    }
  }

  private def buildUpdateBinding(
      model: Model,
      query: com.jacoby6000.smithy.stache.sql.service.SqlUpdateQuery,
      statement: SqlParameterizedStatement
  ): SqlCodegenSqlBinding =
    SqlCodegenSqlBinding(
      queryKind = "update",
      sqlStatement = statement,
      tableName = query.table.name,
      bindParameters = (query.setColumns ++ query.whereColumns).map(column =>
        columnToBindParameter(model, query.table.shapeId, column)),
      executionMode = if (query.returningColumns.nonEmpty) "fetchone" else "rowcount",
      outputKind = "boolean",
      returningColumnIndex = None,
      resultFields = Nil,
      notFoundErrorClassName = None
    )

  private def buildDeleteBinding(
      model: Model,
      query: com.jacoby6000.smithy.stache.sql.service.SqlDeleteQuery,
      statement: SqlParameterizedStatement
  ): SqlCodegenSqlBinding =
    SqlCodegenSqlBinding(
      queryKind = "delete",
      sqlStatement = statement,
      tableName = query.table.name,
      bindParameters = query.whereColumns.map(column => columnToBindParameter(model, query.table.shapeId, column)),
      executionMode = "fetchone",
      outputKind = "boolean",
      returningColumnIndex = None,
      resultFields = Nil,
      notFoundErrorClassName = None
    )

  private def buildSelectOneBinding(
      model: Model,
      operation: SqlOperation,
      query: com.jacoby6000.smithy.stache.sql.service.SqlSelectOneQuery,
      statement: SqlParameterizedStatement
  ): SqlCodegenSqlBinding = {
    val resultFields =
      query.selectColumns.zipWithIndex.flatMap { case (column, index) =>
        memberNameToResultField(model, query.table.shapeId, column.memberName, index)
      }
    SqlCodegenSqlBinding(
      queryKind = "selectOne",
      sqlStatement = statement,
      tableName = query.table.name,
      bindParameters = query.whereColumns.map(column => columnToBindParameter(model, query.table.shapeId, column)),
      executionMode = "fetchone",
      outputKind = "structure",
      returningColumnIndex = None,
      resultFields = resultFields,
      notFoundErrorClassName = operation.errorShapes.headOption.map(SqlCodegenNaming.className)
    )
  }

  private def parametersForColumns(
      model: Model,
      tableShapeId: ShapeId,
      columns: List[SqlQueryColumn]
  ): SqlValidated[List[SqlCodegenParameter]] =
    columns.traverse(column => requiredQueryParameter(model, tableShapeId, column))

  private def tableStructure(model: Model, tableShapeId: ShapeId): SqlValidated[StructureShape] =
    model.getShape(tableShapeId).toScala.flatMap(_.asStructureShape.toScala) match {
      case Some(structure) => structure.validNel
      case None            =>
        InvalidCodegenShape(tableShapeId, "expected a @sqlTable structure for SQL bind parameters").invalidNel
    }

  private def memberToParameter(member: SqlCodegenMember): SqlCodegenParameter =
    SqlCodegenParameter(
      name = member.name,
      typeName = member.typeName,
      pythonTypeName = member.pythonTypeName,
      optional = member.optional,
      isStructure = member.isStructure,
      structureShapeId = member.structureShapeId
    )

  private def requiredQueryParameter(
      model: Model,
      tableShapeId: ShapeId,
      column: SqlQueryColumn
  ): SqlValidated[SqlCodegenParameter] =
    tableStructure(model, tableShapeId).map { tableStructure =>
      val member   = tableStructure.getMember(column.memberName).get()
      val resolved = SqlCodegenTypeResolver.resolveMember(model, column.memberName, member)
      memberToParameter(resolved).copy(optional = false)
    }

  private def columnToBindParameter(
      model: Model,
      tableShapeId: ShapeId,
      column: SqlQueryColumn
  ): SqlCodegenBindParameter = {
    val isJson             = isJsonMember(model, tableShapeId, column.memberName)
    val jsonPythonTypeName =
      if (isJson) {
        Some(jsonPythonTypeNameForMember(model, tableShapeId, column.memberName))
      } else {
        None
      }
    SqlCodegenBindParameter(
      memberName = column.memberName,
      pythonExpression = if (isJson) {
        s"_json_bind_${jsonPythonTypeName.get}(${column.memberName})"
      } else {
        column.memberName
      },
      isJson = isJson,
      jsonPythonTypeName = jsonPythonTypeName
    )
  }

  private def isJsonMember(model: Model, tableShapeId: ShapeId, memberName: String): Boolean =
    tableStructure(model, tableShapeId).toOption
      .flatMap { tableStructure =>
        tableStructure.getMember(memberName).toScala.map(_.sqlJson)
      }
      .getOrElse(false)

  private def memberNameToResultField(
      model: Model,
      tableShapeId: ShapeId,
      memberName: String,
      columnIndex: Int
  ): Option[SqlCodegenResultField] =
    tableStructure(model, tableShapeId).toOption.flatMap { tableStructure =>
      tableStructure.getMember(memberName).toScala.map { member =>
        val resolved = SqlCodegenTypeResolver.resolveMember(model, memberName, member)
        jsonResultField(
          member,
          memberName,
          columnIndex,
          resolved.pythonTypeName
        )
      }
    }

  private def columnNameToResultField(
      model: Model,
      tableShapeId: ShapeId,
      columnName: String,
      columnIndex: Int
  ): Option[SqlCodegenResultField] =
    tableStructure(model, tableShapeId).toOption.flatMap { tableStructure =>
      tableStructure.getAllMembers.asScala.toList
        .collectFirst {
          case (memberName, member) if member.sqlColumnName(memberName) == columnName =>
            val resolved = SqlCodegenTypeResolver.resolveMember(model, memberName, member)
            jsonResultField(
              member,
              memberName,
              columnIndex,
              resolved.pythonTypeName
            )
        }
    }

  private def jsonResultField(
      member: software.amazon.smithy.model.shapes.MemberShape,
      memberName: String,
      columnIndex: Int,
      pythonTypeName: String
  ): SqlCodegenResultField = {
    val isJson = member.sqlJson
    SqlCodegenResultField(
      fieldName = memberName,
      columnIndex = columnIndex,
      pythonTypeName = pythonTypeName,
      isJson = isJson,
      jsonReadExpression = if (isJson) {
        Some(s"_read_$pythonTypeName(row, $columnIndex)")
      } else {
        None
      }
    )
  }

  private def jsonPythonTypeNameForMember(
      model: Model,
      tableShapeId: ShapeId,
      memberName: String
  ): String =
    tableStructure(model, tableShapeId).toOption.fold(memberName) { tableStructure =>
      val member = tableStructure.getMember(memberName).get()
      SqlCodegenTypeResolver.resolveMember(model, memberName, member, SqlCodegenMemberRole.SqlTableRow).pythonTypeName
    }

  private def isPreludeShape(shapeId: ShapeId): Boolean =
    SqlCodegenTypeResolver.isPreludeShape(shapeId) && shapeId != SqlCodegenTypeResolver.UnitShapeId

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
