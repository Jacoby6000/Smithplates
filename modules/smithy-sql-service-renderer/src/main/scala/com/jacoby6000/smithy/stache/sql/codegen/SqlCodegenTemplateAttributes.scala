package com.jacoby6000.smithy.stache.sql.codegen

import com.jacoby6000.smithy.stache.sql.*
import com.jacoby6000.smithy.stache.sql.query.SqlBindPlaceholder

object SqlCodegenTemplateAttributes {
  def forService(context: SqlCodegenServiceContext): Map[String, Any] = {
    val baseAttributes =
      Map(
        "serviceName"        -> context.name,
        "serviceNamespace"   -> context.namespace,
        "serviceShapeId"     -> context.shapeId.toString,
        "serviceVersion"     -> context.version,
        "hasSqlOperations"   -> context.hasSqlOperations,
        "dialectKey"         -> context.dialectKey,
        "isPostgresDialect"  -> (context.dialectKey == "postgres"),
        "isSqliteDialect"    -> (context.dialectKey == "sqlite"),
        "models"             -> withLastFlag(context.models.map(modelAttributes)),
        "unions"             -> withLastFlag(context.unions.map(unionAttributes)),
        "hasUnions"          -> context.unions.nonEmpty,
        "operations"         -> withLastFlag(
          context.operations.map(operationAttributes(context.bindPlaceholderStyle, context.dialectKey, _))
        ),
        "hasIntegrationTest" -> context.integrationTest.isDefined,
        "integrationTest"    -> context.integrationTest.map(integrationTestAttributes).orNull
      ) ++ SqlCodegenHelperAttributes.forService(context)

    context.integrationTest match {
      case Some(integrationTest) =>
        baseAttributes ++ flattenIntegrationTestAttributes(integrationTestAttributes(integrationTest))
      case None                  =>
        baseAttributes
    }
  }

  def renderOutputPath(pattern: String, context: SqlCodegenServiceContext, templateRoot: String): String =
    pattern
      .replace("{{serviceName}}", context.name)
      .replace("{{serviceClassName}}", context.name)
      .replace(
        "{{serviceFileName}}",
        ScalateSspTemplateEngine.renderServiceModuleBaseName(templateRoot, context.name)
      )
      .replace("{{serviceNamespace}}", context.namespace)
      .replace("{{serviceShapeId}}", context.shapeId.toString)
      .replace("{{serviceVersion}}", context.version)

  private def modelAttributes(model: SqlStructure): Map[String, Any] = {
    val members       = withLastFlag(model.members.map(memberAttributes))
    Map(
      "name"       -> model.name,
      "shapeId"    -> model.shapeId.toString,
      "namespace"  -> model.namespace,
      "members"    -> members,
      "hasMembers" -> model.members.nonEmpty
    )
  }

  private def unionAttributes(union: SqlUnion): Map[String, Any] =
    Map(
      "name"      -> union.name,
      "shapeId"   -> union.shapeId.toString,
      "namespace" -> union.namespace,
      "members"   -> withLastFlag(union.members.map(unionMemberAttributes))
    )

  private def unionMemberAttributes(member: SqlUnionMember): Map[String, Any] =
    Map(
      "name"     -> member.name,
      "typeName" -> member.typeName
    )

  private def memberAttributes(member: SqlStructureMember): Map[String, Any] =
    Map(
      "name"               -> member.name,
      "typeName"           -> member.typeName,
      "optional"           -> member.optional,
      "required"           -> !member.optional,
      "isStructure"        -> member.isStructure,
      "structureShapeName" -> member.structureShapeId.map(_.getName).orNull
    )

  private def operationAttributes(
      bindPlaceholderStyle: SqlBindPlaceholder,
      dialectKey: String,
      operation: SqlCodegenOperation
  ): Map[String, Any] = {
    val base =
      Map(
        "name"             -> operation.name,
        "operationShapeId" -> operation.shapeId.toString,
        "parameters"       -> withLastFlag(operation.parameters.map(parameterAttributes)),
        "hasParameters"    -> operation.parameters.nonEmpty,
        "hasOutput"        -> operation.outputTypeName.isDefined,
        "outputShapeName"  -> operation.outputShapeId.map(_.getName).orNull,
        "outputTypeName"   -> operation.outputTypeName.orNull,
        "errors"           -> withLastFlag(operation.errors.map(errorAttributes)),
        "hasErrors"        -> operation.errors.nonEmpty,
        "hasSql"           -> operation.sql.isDefined,
        "dialectKey"       -> dialectKey
      )

    operation.sql match {
      case None      =>
        base ++ Map[String, Any](
          "queryKind"            -> "",
          "sqlStatement"         -> "",
          "tableName"            -> "",
          "bindParameters"       -> Nil,
          "hasBindParameters"    -> false,
          "executionMode"        -> "",
          "isRowcountExecution"  -> false,
          "outputKind"           -> "",
          "isInsertScalar"       -> false,
          "isInsertStructure"    -> false,
          "isBooleanMutation"    -> false,
          "isSelectOne"          -> false,
          "canUseClassRow"       -> false,
          "usesDictRowFactory"   -> false,
          "returningColumnIndex" -> null,
          "resultFields"         -> Nil,
          "hasResultFields"      -> false
        )
      case Some(sql) =>
        val canUseClassRow     = SqlCodegenSqlBindingMetadata.canUseClassRow(sql)
        val usesDictRowFactory = SqlCodegenSqlBindingMetadata.usesDictRowFactory(sql)
        base ++ Map[String, Any](
          "queryKind"            -> sql.queryKind,
          "sqlStatement"         -> SqlBindPlaceholder.format(
            sql.sqlStatement.segments,
            bindPlaceholderStyle
          ),
          "tableName"            -> sql.tableName,
          "bindParameters"       -> sql.bindParameters.map(bindParameterAttributes(dialectKey, _)),
          "hasBindParameters"    -> sql.bindParameters.nonEmpty,
          "executionMode"        -> sql.executionMode,
          "isRowcountExecution"  -> (sql.executionMode == "rowcount"),
          "outputKind"           -> sql.outputKind,
          "isInsertScalar"       -> (sql.queryKind == "insert" && sql.outputKind == "scalar"),
          "isInsertStructure"    -> (sql.queryKind == "insert" && sql.outputKind == "structure"),
          "isBooleanMutation"    -> (sql.outputKind == "boolean"),
          "isSelectOne"          -> (sql.queryKind == "selectOne"),
          "canUseClassRow"       -> canUseClassRow,
          "usesDictRowFactory"   -> usesDictRowFactory,
          "returningColumnIndex" -> sql.returningColumnIndex.map(_.asInstanceOf[Any]).orNull,
          "resultFields"         -> withLastFlag(sql.resultFields.map(resultFieldAttributes)),
          "hasResultFields"      -> sql.resultFields.nonEmpty
        )
    }
  }

  private def parameterAttributes(parameter: SqlCodegenParameter): Map[String, Any] =
    Map(
      "name"               -> parameter.name,
      "typeName"           -> parameter.typeName,
      "optional"           -> parameter.optional,
      "required"           -> !parameter.optional,
      "isStructure"        -> parameter.isStructure,
      "structureShapeName" -> parameter.structureShapeId.map(_.getName).orNull
    )

  private def bindParameterAttributes(
      dialectKey: String,
      bindParameter: SqlCodegenBindParameter
  ): Map[String, Any]      =
    Map(
      "memberName"      -> bindParameter.memberName,
      "typeName"        -> bindParameter.typeName,
      "isJson"          -> bindParameter.isJson,
      "jsonTypeName"    -> bindParameter.jsonTypeName.orNull,
      "timestampFormat" -> timestampFormatName(bindParameter.timestampFormat),
      "dialectKey"      -> dialectKey
    )

  private def resultFieldAttributes(resultField: SqlCodegenResultField): Map[String, Any] =
    Map(
      "fieldName"         -> resultField.fieldName,
      "columnName"        -> resultField.columnName,
      "columnNameLiteral" -> s"\"${resultField.columnName}\"",
      "columnIndex"       -> resultField.columnIndex,
      "typeName"          -> resultField.typeName,
      "isJson"            -> resultField.isJson,
      "timestampFormat"   -> timestampFormatName(resultField.timestampFormat)
    )

  private def timestampFormatName(format: Option[SqlTimestampFormat]): Any =
    format.map {
      case SqlTimestampFormat.DateTime     => "DateTime"
      case SqlTimestampFormat.EpochSeconds => "EpochSeconds"
    }.orNull

  private def errorAttributes(error: SqlCodegenErrorType): Map[String, Any] =
    Map(
      "name"    -> error.name,
      "shapeId" -> error.shapeId.toString
    )

  private def integrationTestAttributes(
      integrationTest: SqlCodegenIntegrationTestContext
  ): Map[String, Any]                        =
    Map(
      "schemaDdl"                        -> integrationTest.schemaDdl,
      "extraImports"                     -> integrationTest.extraImports.mkString("", "\n", "\n"),
      "insertOperation"                  -> integrationOperationAttributes(integrationTest.insertOperation),
      "selectOneOperation"               -> integrationOperationAttributes(integrationTest.selectOneOperation),
      "updateOperation"                  -> integrationTest.updateOperation.map(integrationOperationAttributes).orNull,
      "deleteOperation"                  -> integrationTest.deleteOperation.map(integrationOperationAttributes).orNull,
      "hasUpdateOperation"               -> integrationTest.updateOperation.isDefined,
      "hasDeleteOperation"               -> integrationTest.deleteOperation.isDefined,
      "transactionCommitInTxAssertions"  -> withLastFlag(
        integrationTest.transactionCommitInTxAssertions.map(assertion => Map("line" -> assertion))
      ),
      "transactionCommitAfterAssertions" -> withLastFlag(
        integrationTest.transactionCommitAfterAssertions.map(assertion => Map("line" -> assertion))
      )
    )

  private def flattenIntegrationTestAttributes(integrationTest: Map[String, Any]): Map[String, Any] = {
    val operationPrefixes = List("insertOperation", "selectOneOperation", "updateOperation", "deleteOperation")
    val dottedOperations  = operationPrefixes
      .flatMap { prefix =>
        integrationTest.get(prefix).collect { case operation: Map[String, Any] @unchecked =>
          operation.map { case (key, value) => s"$prefix.$key" -> value }
        }
      }
      .flatten
      .toMap
    integrationTest ++ dottedOperations
  }

  private def integrationOperationAttributes(
      operation: SqlCodegenIntegrationTestOperation
  ): Map[String, Any]                       =
    Map(
      "name"                       -> operation.name,
      "callArguments"              -> operation.callArguments,
      "outputShapeName"            -> operation.outputShapeId.map(_.getName).orNull,
      "hasOutputShapeName"         -> operation.outputShapeId.isDefined,
      "resultAssertions"           -> withLastFlag(
        operation.resultAssertions.map(assertion => Map("line" -> assertion))
      ),
      "updatedResultAssertions"    -> withLastFlag(
        operation.updatedResultAssertions.map(assertion => Map("line" -> assertion))
      ),
      "hasResultAssertions"        -> operation.resultAssertions.nonEmpty,
      "hasUpdatedResultAssertions" -> operation.updatedResultAssertions.nonEmpty
    )

  private def withLastFlag(items: List[Map[String, Any]]): List[Map[String, Any]] =
    items.zipWithIndex.map { case (item, index) =>
      item + ("last" -> (index == items.length - 1))
    }

}
