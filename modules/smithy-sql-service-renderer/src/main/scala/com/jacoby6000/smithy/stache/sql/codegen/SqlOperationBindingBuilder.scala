package com.jacoby6000.smithy.stache.sql.codegen

import cats.syntax.all.*
import com.jacoby6000.smithy.stache.sql.*
import com.jacoby6000.smithy.stache.sql.codegen.language.LanguageTypeResolver
import com.jacoby6000.smithy.stache.sql.codegen.python.PythonSqlBindingEnricher
import com.jacoby6000.smithy.stache.sql.codegen.python.PythonTypeMapper
import com.jacoby6000.smithy.stache.sql.query.SqlParameterizedStatement
import com.jacoby6000.smithy.stache.sql.query.SqlQueryRenderer
import com.jacoby6000.smithy.stache.sql.service.SqlOperation
import com.jacoby6000.smithy.stache.sql.service.SqlQueries
import com.jacoby6000.smithy.stache.sql.service.SqlQueryColumn
import com.jacoby6000.smithy.stache.sql.service.codegen.ResolvedSqlOperationQuery
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.StructureShape

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

object SqlOperationBindingBuilder {
  def build(
      model: Model,
      operation: SqlOperation,
      query: ResolvedSqlOperationQuery,
      queryRenderer: SqlQueryRenderer
  ): SqlValidated[SqlCodegenSqlBinding] =
    buildNeutral(model, operation, query, queryRenderer).map { binding =>
      PythonSqlBindingEnricher.enrich(binding, queryRenderer.key)
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

  private def buildNeutral(
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
            buildInsertBinding(model, operation, insertQuery, statement).validNel
          case ResolvedSqlOperationQuery.Update(updateQuery)       =>
            buildUpdateBinding(model, updateQuery, statement).validNel
          case ResolvedSqlOperationQuery.Delete(deleteQuery)       =>
            buildDeleteBinding(model, deleteQuery, statement).validNel
          case ResolvedSqlOperationQuery.SelectOne(selectOneQuery) =>
            buildSelectOneBinding(model, selectOneQuery, statement).validNel
          case ResolvedSqlOperationQuery.Select(_)                 =>
            InvalidPluginConfig("unreachable select branch").invalidNel
        }
    }

  private def buildInsertBinding(
      model: Model,
      operation: SqlOperation,
      query: com.jacoby6000.smithy.stache.sql.service.SqlInsertQuery,
      statement: SqlParameterizedStatement
  ): SqlCodegenSqlBinding = {
    val bindParameters =
      query.columns.map(column => columnToBindParameter(model, query.table.shapeId, column))
    val outputShapeId  = operation.outputShape.getOrElse(SqlCodegenShapeGraph.UnitShapeId)

    if (isPreludeShape(outputShapeId)) {
      SqlCodegenSqlBinding(
        queryKind = "insert",
        sqlStatement = statement,
        tableName = query.table.name,
        bindParameters = bindParameters,
        executionMode = "fetchone",
        outputKind = "scalar",
        returningColumnIndex = query.returningColumns.headOption.map(_ => 0),
        resultFields = Nil
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
        resultFields = resultFields
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
      resultFields = Nil
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
      resultFields = Nil
    )

  private def buildSelectOneBinding(
      model: Model,
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
      resultFields = resultFields
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
      languageTypeName = member.languageTypeName,
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
      val resolved = LanguageTypeResolver.resolveMember(model, column.memberName, member, PythonTypeMapper)
      memberToParameter(resolved).copy(optional = false)
    }

  private def columnToBindParameter(
      model: Model,
      tableShapeId: ShapeId,
      column: SqlQueryColumn
  ): SqlCodegenBindParameter = {
    val isJson           = isJsonMember(model, tableShapeId, column.memberName)
    val jsonTypeName     =
      if (isJson) {
        Some(jsonTypeNameForMember(model, tableShapeId, column.memberName))
      } else {
        None
      }
    val memberAndType    =
      tableStructure(model, tableShapeId).toOption.flatMap { tableStructure =>
        tableStructure.getMember(column.memberName).toScala.map { member =>
          val resolved = LanguageTypeResolver.resolveMember(model, column.memberName, member, PythonTypeMapper)
          (member, resolved.languageTypeName)
        }
      }
    val languageTypeName = memberAndType.map(_._2).getOrElse("str")
    val timestampFormat  =
      memberAndType.flatMap { case (member, _) =>
        PythonSqlBindingEnricher.resolveTimestampFormat(model, member)
      }
    SqlCodegenBindParameter(
      memberName = column.memberName,
      languageTypeName = languageTypeName,
      isJson = isJson,
      jsonTypeName = jsonTypeName,
      timestampFormat = timestampFormat
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
        val resolved   = LanguageTypeResolver.resolveMember(model, memberName, member, PythonTypeMapper)
        val columnName = member.sqlColumnName(memberName)
        neutralResultField(model, member, memberName, columnName, columnIndex, resolved.languageTypeName)
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
            val resolved = LanguageTypeResolver.resolveMember(model, memberName, member, PythonTypeMapper)
            neutralResultField(
              model,
              member,
              memberName,
              columnName,
              columnIndex,
              resolved.languageTypeName
            )
        }
    }

  private def neutralResultField(
      model: Model,
      member: software.amazon.smithy.model.shapes.MemberShape,
      memberName: String,
      columnName: String,
      columnIndex: Int,
      languageTypeName: String
  ): SqlCodegenResultField = {
    val isJson          = member.sqlJson
    val timestampFormat = PythonSqlBindingEnricher.resolveTimestampFormat(model, member)
    SqlCodegenResultField(
      fieldName = memberName,
      columnName = columnName,
      columnIndex = columnIndex,
      languageTypeName = languageTypeName,
      isJson = isJson,
      timestampFormat = timestampFormat
    )
  }

  private def jsonTypeNameForMember(
      model: Model,
      tableShapeId: ShapeId,
      memberName: String
  ): String =
    tableStructure(model, tableShapeId).toOption.fold(memberName) { tableStructure =>
      val member = tableStructure.getMember(memberName).get()
      LanguageTypeResolver
        .resolveMember(model, memberName, member, PythonTypeMapper, SqlCodegenMemberRole.SqlTableRow)
        .languageTypeName
    }

  private def isPreludeShape(shapeId: ShapeId): Boolean =
    SqlCodegenShapeGraph.isPreludeShape(shapeId) && shapeId != SqlCodegenShapeGraph.UnitShapeId

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
