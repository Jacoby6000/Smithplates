package com.jacoby6000.smithplates.sql.service.codegen

import com.jacoby6000.smithplates.sql.*
import com.jacoby6000.smithplates.sql.model.*
import com.jacoby6000.smithplates.sql.service.SqlQueries
import com.jacoby6000.smithplates.sql.shared.SqlSchemaDdlRenderer
import com.jacoby6000.smithplates.sql.shared.SqlShared

object SqlCodegenIntegrationTestBuilder {
  private enum SampleVariant {
    case Initial
    case Updated
  }

  def build(
      context: SqlCodegenServiceContext,
      schema: SqlSchema,
      queries: SqlQueries,
      schemaDdlRenderers: Map[String, SqlSchemaDdlRenderer]
  ): Option[SqlCodegenIntegrationTestContext] = {
    val sqlOperations = context.operations.filter(_.sql.isDefined)
    if (sqlOperations.isEmpty) {
      return None
    }

    val insertOperation    =
      sqlOperations.find(_.sql.exists(_.queryKind == "insert")).flatMap(buildInsertOperation(context, _))
    val selectOneOperation =
      sqlOperations.find(_.sql.exists(_.queryKind == "selectOne")).flatMap(buildSelectOneOperation(context, _))
    val updateOperation    =
      sqlOperations.find(_.sql.exists(_.queryKind == "update")).flatMap(buildUpdateOperation(context, _))
    val deleteOperation    =
      sqlOperations.find(_.sql.exists(_.queryKind == "delete")).flatMap(buildDeleteOperation)

    (insertOperation, selectOneOperation) match {
      case (Some(insert), Some(selectOne)) =>
        val testImports = collectTestImports(context, insert, selectOne, updateOperation)
        Some(
          SqlCodegenIntegrationTestContext(
            schemaDdl = schemaDdl(schema, sqlOperations, queries, context.dialectKey, schemaDdlRenderers),
            insertOperation = insert,
            selectOneOperation = selectOne,
            updateOperation = updateOperation,
            deleteOperation = deleteOperation,
            transactionCommitInTxAssertions = selectOne.resultAssertions,
            transactionCommitAfterAssertions =
              remapAssertionTarget(selectOne.resultAssertions, "fetched", "fetched_after_commit"),
            extraImports = collectExtraImports(insert, selectOne, updateOperation),
            testImports = testImports,
            localImportBlock = SqlCodegenPythonImports.integrationTestLocalImportBlock(
              context.name,
              context.dialectKey,
              testImports
            )
          )
        )
      case _                               =>
        None
    }
  }

  private def schemaDdl(
      schema: SqlSchema,
      sqlOperations: List[SqlCodegenOperation],
      queries: SqlQueries,
      dialectKey: String,
      schemaDdlRenderers: Map[String, SqlSchemaDdlRenderer]
  ): String = {
    val operationShapeIds = sqlOperations.map(_.shapeId).toSet
    val primaryTableNames = sqlOperations.flatMap(_.sql.map(_.tableName)).toSet
    val joinedTableNames  =
      queries.selectOnes
        .filter(query => operationShapeIds.contains(query.shapeId))
        .flatMap(_.nestedResults.map(_.table.name))
        .toSet
    val tableNames        = primaryTableNames ++ joinedTableNames
    val filteredSchema    =
      schema.copy(
        tables = schema.tables.filter(table => tableNames.contains(table.name))
      )
    val ddlRenderer       =
      schemaDdlRenderers.getOrElse(
        dialectKey,
        throw new IllegalStateException(s"schema DDL renderer for dialect '$dialectKey' is required")
      )
    SqlShared.formatDdlStatements(ddlRenderer.renderSchemaDdlStatements(filteredSchema))
  }

  private def buildInsertOperation(
      context: SqlCodegenServiceContext,
      operation: SqlCodegenOperation
  ): Option[SqlCodegenIntegrationTestOperation] =
    Some(
      SqlCodegenIntegrationTestOperation(
        name = operation.name,
        callArguments = renderCallArguments(context, operation.parameters, SampleVariant.Initial),
        updatedCallArguments = None,
        outputShapeId = operation.outputShapeId,
        resultAssertions = Nil,
        updatedResultAssertions = Nil
      )
    )

  private def buildSelectOneOperation(
      context: SqlCodegenServiceContext,
      operation: SqlCodegenOperation
  ): Option[SqlCodegenIntegrationTestOperation] = {
    val insertParameters       =
      context.operations
        .find(_.sql.exists(_.queryKind == "insert"))
        .map(_.parameters)
        .getOrElse(Nil)
    val updatedParameterSource =
      context.operations
        .find(_.sql.exists(_.queryKind == "update"))
        .map(_.parameters.filterNot(_.name == "id"))
        .filter(_.nonEmpty)
        .getOrElse(insertParameters)

    Some(
      SqlCodegenIntegrationTestOperation(
        name = operation.name,
        callArguments = "id=entity_id",
        updatedCallArguments = None,
        outputShapeId = operation.outputShapeId,
        resultAssertions = resultAssertions(context, insertParameters, "fetched", SampleVariant.Initial),
        updatedResultAssertions =
          resultAssertions(context, updatedParameterSource, "fetched_after_update", SampleVariant.Updated)
      )
    )
  }

  private def buildUpdateOperation(
      context: SqlCodegenServiceContext,
      operation: SqlCodegenOperation
  ): Option[SqlCodegenIntegrationTestOperation] =
    Some(
      SqlCodegenIntegrationTestOperation(
        name = operation.name,
        callArguments = {
          val valueParameters = operation.parameters.filterNot(_.name == "id")
          if (valueParameters.nonEmpty) {
            s"${renderCallArguments(context, valueParameters, SampleVariant.Updated)}, id=entity_id"
          } else {
            "id=entity_id"
          }
        },
        updatedCallArguments = None,
        outputShapeId = operation.outputShapeId,
        resultAssertions = Nil,
        updatedResultAssertions = Nil
      )
    )

  private def buildDeleteOperation(operation: SqlCodegenOperation): Option[SqlCodegenIntegrationTestOperation] =
    Some(
      SqlCodegenIntegrationTestOperation(
        name = operation.name,
        callArguments = "id=entity_id",
        updatedCallArguments = None,
        outputShapeId = operation.outputShapeId,
        resultAssertions = Nil,
        updatedResultAssertions = Nil
      )
    )

  private def remapAssertionTarget(
      assertions: List[String],
      from: String,
      to: String
  ): List[String] =
    assertions.map(_.replace(s"$from.", s"$to."))

  private def renderCallArguments(
      context: SqlCodegenServiceContext,
      parameters: List[SqlCodegenParameter],
      variant: SampleVariant
  ): String =
    parameters
      .map(parameter => s"${parameter.name}=${sampleExpression(context, parameter, variant)}")
      .mkString(", ")

  private def resultAssertions(
      context: SqlCodegenServiceContext,
      parameters: List[SqlCodegenParameter],
      targetExpression: String,
      variant: SampleVariant
  ): List[String] =
    parameters.flatMap { parameter =>
      if (parameter.isStructure) {
        structureMembers(context, parameter).map { member =>
          s"""assert $targetExpression.${parameter.name}.${member.name} == ${sampleMemberExpression(
              context,
              member,
              variant)}"""
        }
      } else if (isUnionParameter(context, parameter)) {
        unionAssertion(context, parameter, targetExpression, variant).toList
      } else {
        List(
          s"""assert $targetExpression.${parameter.name} == ${sampleExpression(context, parameter, variant)}"""
        )
      }
    }

  private def unionAssertion(
      context: SqlCodegenServiceContext,
      parameter: SqlCodegenParameter,
      targetExpression: String,
      variant: SampleVariant
  ): Option[String] =
    Some(
      s"""assert $targetExpression.${parameter.name} == ${sampleExpression(context, parameter, variant)}"""
    )

  private def structureMembers(
      context: SqlCodegenServiceContext,
      parameter: SqlCodegenParameter
  ): List[SqlStructureMember] =
    parameter.structureShapeId
      .flatMap(shapeId => context.models.find(_.shapeId == shapeId))
      .map(_.members)
      .getOrElse(Nil)

  private def isUnionParameter(context: SqlCodegenServiceContext, parameter: SqlCodegenParameter): Boolean =
    context.unions.exists(_.name == parameter.typeName)

  private def sampleExpression(
      context: SqlCodegenServiceContext,
      parameter: SqlCodegenParameter,
      variant: SampleVariant
  ): String =
    if (parameter.isStructure) {
      val members   = structureMembers(context, parameter)
      val arguments =
        members
          .map(member => s"${member.name}=${sampleMemberExpression(context, member, variant)}")
          .mkString(", ")
      s"${parameter.typeName}($arguments)"
    } else if (isUnionParameter(context, parameter)) {
      val union       =
        context.unions
          .find(_.name == parameter.typeName)
          .getOrElse(throw new IllegalStateException(s"Missing union model for ${parameter.typeName}"))
      val member      = union.members.headOption.getOrElse {
        throw new IllegalStateException(s"Union ${union.name} has no members for integration test sampling")
      }
      val sampleValue = sampleLiteral(context, member.typeName, variant, member.name)
      s"""{"${member.name}": $sampleValue}"""
    } else {
      sampleLiteral(context, parameter.typeName, variant, parameter.name)
    }

  private def sampleMemberExpression(
      context: SqlCodegenServiceContext,
      member: SqlStructureMember,
      variant: SampleVariant
  ): String =
    sampleLiteral(context, member.typeName, variant, member.name)

  private def sampleLiteral(
      context: SqlCodegenServiceContext,
      typeName: String,
      variant: SampleVariant,
      seed: String
  ): String = {
    val suffix =
      variant match {
        case SampleVariant.Initial => seed
        case SampleVariant.Updated => s"updated-$seed"
      }

    typeName match {
      case "String"                                        => s"\"integration-$suffix\""
      case "Integer" | "Long" | "BigInteger"               => if (variant == SampleVariant.Initial) "42" else "84"
      case "Float" | "Double"                              => if (variant == SampleVariant.Initial) "3.5" else "7.0"
      case "Boolean"                                       => "True"
      case "Blob"                                          => s"b\"integration-$suffix\""
      case "Timestamp"                                     => "datetime.now(timezone.utc)"
      case other if context.models.exists(_.name == other) =>
        val structure = context.models.find(_.name == other).get
        val arguments =
          structure.members
            .map(member => s"${member.name}=${sampleMemberExpression(context, member, variant)}")
            .mkString(", ")
        s"$other($arguments)"
      case other if context.unions.exists(_.name == other) =>
        val union       = context.unions.find(_.name == other).get
        val member      = union.members.head
        val memberValue = sampleLiteral(context, member.typeName, variant, member.name)
        s"""{"${member.name}": $memberValue}"""
      case other                                           =>
        throw new IllegalArgumentException(s"Unsupported integration test sample type: $other")
    }
  }

  private def collectTestImports(
      context: SqlCodegenServiceContext,
      insertOperation: SqlCodegenIntegrationTestOperation,
      selectOneOperation: SqlCodegenIntegrationTestOperation,
      updateOperation: Option[SqlCodegenIntegrationTestOperation]
  ): String = {
    val serviceModuleBase    =
      ScalateSspTemplateEngine.renderServiceModuleBaseName("python/src/db", context.name)
    val callText             =
      List(
        Some(insertOperation.callArguments),
        updateOperation.map(_.callArguments),
        updateOperation.flatMap(_.updatedCallArguments)
      ).flatten.mkString(" ")
    val operationResultNames =
      context.models
        .filter(_.namespace == SqlSelectOneDerivedOutputBuilder.DerivedNamespace)
        .map(_.name)
        .toSet
    val tableModelNames      =
      context.models
        .filter(_.namespace != SqlSelectOneDerivedOutputBuilder.DerivedNamespace)
        .map(_.name)
        .toSet
    val unionNames           = context.unions.map(_.name).toSet

    val modelImportNames    = scala.collection.mutable.LinkedHashSet.empty[String]
    val protocolImportNames = scala.collection.mutable.LinkedHashSet.empty[String]

    def assignImportName(name: String): Unit =
      if (operationResultNames.contains(name)) {
        protocolImportNames += name
      } else if (tableModelNames.contains(name) || unionNames.contains(name)) {
        modelImportNames += name
      }

    selectOneOperation.outputShapeId.foreach(shapeId => assignImportName(shapeId.getName))
    tableModelNames.foreach { name =>
      if (callText.contains(name)) {
        modelImportNames += name
      }
    }
    unionNames.foreach { name =>
      if (callText.contains(name)) {
        modelImportNames += name
      }
    }

    val importBlocks = List.newBuilder[String]
    if (modelImportNames.nonEmpty) {
      importBlocks += s"from ${serviceModuleBase}_models import ("
      importBlocks ++= modelImportNames.toList.sorted.map(name => s"    $name,")
      importBlocks += ")"
    }
    if (protocolImportNames.nonEmpty) {
      importBlocks += s"from ${serviceModuleBase}_protocol import ("
      importBlocks ++= protocolImportNames.toList.sorted.map(name => s"    $name,")
      importBlocks += ")"
    }

    val blocks = importBlocks.result()
    if (blocks.isEmpty) {
      ""
    } else {
      blocks.mkString("", "\n", "\n")
    }
  }

  private def collectExtraImports(
      insertOperation: SqlCodegenIntegrationTestOperation,
      selectOneOperation: SqlCodegenIntegrationTestOperation,
      updateOperation: Option[SqlCodegenIntegrationTestOperation]
  ): List[String] = {
    val generatedText =
      List(
        insertOperation.callArguments,
        updateOperation.map(_.callArguments).getOrElse(""),
        selectOneOperation.resultAssertions.mkString("\n"),
        selectOneOperation.updatedResultAssertions.mkString("\n")
      ).mkString("\n")

    if (generatedText.contains("datetime")) {
      List("from datetime import datetime, timezone")
    } else {
      Nil
    }
  }
}
