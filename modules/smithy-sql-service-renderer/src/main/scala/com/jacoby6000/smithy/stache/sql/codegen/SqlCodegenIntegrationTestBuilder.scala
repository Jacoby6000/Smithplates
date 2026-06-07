package com.jacoby6000.smithy.stache.sql.codegen

import com.jacoby6000.smithy.stache.sql.SqlSchema
import com.jacoby6000.smithy.stache.sql.shared.SqlSchemaDdlRenderer
import com.jacoby6000.smithy.stache.sql.shared.SqlShared

object SqlCodegenIntegrationTestBuilder {
  private enum SampleVariant {
    case Initial
    case Updated
  }

  def build(
      context: SqlCodegenServiceContext,
      schema: SqlSchema,
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
        val serviceFixtureName           = s"${context.fileName}_service"
        val selectOneResultAssertions    =
          selectOne.resultAssertions.map(line => s"    $line").mkString("\n")
        val updateLifecycleBlock         =
          updateOperation.map(
            renderUpdateLifecycleBlock(serviceFixtureName, selectOne, _, selectOne.updatedResultAssertions)
          )
        val deleteLifecycleBlock         =
          deleteOperation.map(renderDeleteLifecycleBlock(serviceFixtureName, selectOne, _))
        val transactionCommitTestBlock   =
          renderTransactionCommitTestBlock(
            serviceFixtureName,
            insert,
            selectOne,
            context.dialectKey,
            context.implementationClassName
          )
        val transactionRollbackTestBlock =
          renderTransactionRollbackTestBlock(
            serviceFixtureName,
            insert,
            selectOne,
            context.dialectKey,
            context.implementationClassName
          )
        Some(
          SqlCodegenIntegrationTestContext(
            schemaDdl = schemaDdl(schema, sqlOperations, context.dialectKey, schemaDdlRenderers),
            serviceFixtureName = serviceFixtureName,
            implementationModuleName = context.implementationModuleName,
            insertOperation = insert,
            selectOneOperation = selectOne,
            updateOperation = updateOperation,
            deleteOperation = deleteOperation,
            selectOneResultAssertions = selectOneResultAssertions,
            updateLifecycleBlock = updateLifecycleBlock,
            deleteLifecycleBlock = deleteLifecycleBlock,
            transactionCommitTestBlock = transactionCommitTestBlock,
            transactionRollbackTestBlock = transactionRollbackTestBlock,
            extraImports = collectExtraImports(context)
          )
        )
      case _                               =>
        None
    }
  }

  private def schemaDdl(
      schema: SqlSchema,
      sqlOperations: List[SqlCodegenOperation],
      dialectKey: String,
      schemaDdlRenderers: Map[String, SqlSchemaDdlRenderer]
  ): String = {
    val tableNames     = sqlOperations.flatMap(_.sql.map(_.tableName)).toSet
    val filteredSchema =
      schema.copy(
        tables = schema.tables.filter(table => tableNames.contains(table.name))
      )
    val ddlRenderer    =
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
        methodName = operation.methodName,
        callArguments = renderCallArguments(context, operation.parameters, SampleVariant.Initial),
        updatedCallArguments = None,
        outputClassName = operation.outputClassName,
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
        methodName = operation.methodName,
        callArguments = "id=entity_id",
        updatedCallArguments = None,
        outputClassName = operation.outputClassName,
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
        methodName = operation.methodName,
        callArguments = {
          val valueParameters = operation.parameters.filterNot(_.name == "id")
          if (valueParameters.nonEmpty) {
            s"${renderCallArguments(context, valueParameters, SampleVariant.Updated)}, id=entity_id"
          } else {
            "id=entity_id"
          }
        },
        updatedCallArguments = None,
        outputClassName = operation.outputClassName,
        resultAssertions = Nil,
        updatedResultAssertions = Nil
      )
    )

  private def buildDeleteOperation(operation: SqlCodegenOperation): Option[SqlCodegenIntegrationTestOperation] =
    Some(
      SqlCodegenIntegrationTestOperation(
        methodName = operation.methodName,
        callArguments = "id=entity_id",
        updatedCallArguments = None,
        outputClassName = operation.outputClassName,
        resultAssertions = Nil,
        updatedResultAssertions = Nil
      )
    )

  private def renderUpdateLifecycleBlock(
      serviceFixtureName: String,
      selectOne: SqlCodegenIntegrationTestOperation,
      update: SqlCodegenIntegrationTestOperation,
      updatedResultAssertions: List[String]
  ): String = {
    val outputClassName =
      selectOne.outputClassName.getOrElse(
        throw new IllegalStateException("selectOne integration test operation missing outputClassName")
      )
    val lines           =
      List(
        s"    updated = await $serviceFixtureName.${update.methodName}(${update.callArguments})",
        "    assert updated is True",
        "",
        s"    fetched_after_update = await $serviceFixtureName.${selectOne.methodName}(${selectOne.callArguments})",
        s"    assert isinstance(fetched_after_update, $outputClassName)"
      ) ++ updatedResultAssertions.map(line => s"    $line")
    lines.mkString("\n")
  }

  private def renderDeleteLifecycleBlock(
      serviceFixtureName: String,
      selectOne: SqlCodegenIntegrationTestOperation,
      delete: SqlCodegenIntegrationTestOperation
  ): String =
    List(
      s"    deleted = await $serviceFixtureName.${delete.methodName}(${delete.callArguments})",
      "    assert deleted is True",
      "",
      s"    missing = await $serviceFixtureName.${selectOne.methodName}(${selectOne.callArguments})",
      "    assert missing is None"
    ).mkString("\n")

  private def renderTransactionCommitTestBlock(
      serviceFixtureName: String,
      insert: SqlCodegenIntegrationTestOperation,
      selectOne: SqlCodegenIntegrationTestOperation,
      dialectKey: String,
      implementationClassName: String
  ): String = {
    val outputClassName         =
      selectOne.outputClassName.getOrElse(
        throw new IllegalStateException("selectOne integration test operation missing outputClassName")
      )
    val inTransactionAssertions =
      indentAssertionLines(selectOne.resultAssertions, spaces = 8)
    val afterCommitAssertions   =
      indentAssertionLines(
        remapAssertionTarget(selectOne.resultAssertions, "fetched", "fetched_after_commit"),
        spaces = 4
      )
    dialectKey match {
      case "sqlite"   =>
        val insertCall    = s"${insert.callArguments}, transaction=connection"
        val selectOneCall = s"${selectOne.callArguments}, transaction=connection"
        s"""@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_commit($serviceFixtureName: $implementationClassName) -> None:
    connection = $serviceFixtureName._connection
    await connection.execute("BEGIN")
    try:
        entity_id = await $serviceFixtureName.${insert.methodName}($insertCall)
        assert isinstance(entity_id, str)
        assert entity_id

        fetched = await $serviceFixtureName.${selectOne.methodName}($selectOneCall)
        assert isinstance(fetched, $outputClassName)
$inTransactionAssertions
        await connection.commit()
    except BaseException:
        await connection.rollback()
        raise

    fetched_after_commit = await $serviceFixtureName.${selectOne.methodName}(${selectOne.callArguments})
    assert isinstance(fetched_after_commit, $outputClassName)
$afterCommitAssertions"""
      case "postgres" =>
        val insertCall    = s"${insert.callArguments}, transaction=tx"
        val selectOneCall = s"${selectOne.callArguments}, transaction=tx"
        s"""@pytest.mark.integration
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_commit($serviceFixtureName: $implementationClassName) -> None:
    connection = $serviceFixtureName._connection
    async with connection.transaction() as tx:
        entity_id = await $serviceFixtureName.${insert.methodName}($insertCall)
        assert isinstance(entity_id, str)
        assert entity_id

        fetched = await $serviceFixtureName.${selectOne.methodName}($selectOneCall)
        assert isinstance(fetched, $outputClassName)
$inTransactionAssertions

    fetched_after_commit = await $serviceFixtureName.${selectOne.methodName}(${selectOne.callArguments})
    assert isinstance(fetched_after_commit, $outputClassName)
$afterCommitAssertions"""
      case other      =>
        throw new IllegalArgumentException(s"Unsupported integration test dialect key: $other")
    }
  }

  private def renderTransactionRollbackTestBlock(
      serviceFixtureName: String,
      insert: SqlCodegenIntegrationTestOperation,
      selectOne: SqlCodegenIntegrationTestOperation,
      dialectKey: String,
      implementationClassName: String
  ): String =
    dialectKey match {
      case "sqlite"   =>
        val insertCall = s"${insert.callArguments}, transaction=connection"
        s"""@pytest.mark.integration
@pytest.mark.sqlite
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_rollback($serviceFixtureName: $implementationClassName) -> None:
    connection = $serviceFixtureName._connection
    await connection.execute("BEGIN")
    entity_id = await $serviceFixtureName.${insert.methodName}($insertCall)
    assert isinstance(entity_id, str)
    assert entity_id
    await connection.rollback()

    missing = await $serviceFixtureName.${selectOne.methodName}(${selectOne.callArguments})
    assert missing is None"""
      case "postgres" =>
        val insertCall = s"${insert.callArguments}, transaction=tx"
        s"""@pytest.mark.integration
@pytest.mark.postgres
@pytest.mark.asyncio
async def test_derived_sql_methods_transaction_rollback($serviceFixtureName: $implementationClassName) -> None:
    connection = $serviceFixtureName._connection
    entity_id: str | None = None
    with pytest.raises(RuntimeError, match="rollback probe"):
        async with connection.transaction() as tx:
            entity_id = await $serviceFixtureName.${insert.methodName}($insertCall)
            raise RuntimeError("rollback probe")

    assert entity_id is not None
    missing = await $serviceFixtureName.${selectOne.methodName}(${selectOne.callArguments})
    assert missing is None"""
      case other      =>
        throw new IllegalArgumentException(s"Unsupported integration test dialect key: $other")
    }

  private def indentAssertionLines(assertions: List[String], spaces: Int): String =
    assertions.map(line => s"${" " * spaces}$line").mkString("\n")

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
  ): List[SqlCodegenMember] =
    parameter.structureShapeId
      .flatMap(shapeId => context.models.find(_.shapeId == shapeId))
      .map(_.members)
      .getOrElse(Nil)

  private def isUnionParameter(context: SqlCodegenServiceContext, parameter: SqlCodegenParameter): Boolean =
    context.unions.exists(_.name == parameter.pythonTypeName)

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
      s"${parameter.pythonTypeName}($arguments)"
    } else if (isUnionParameter(context, parameter)) {
      val union       =
        context.unions
          .find(_.name == parameter.pythonTypeName)
          .getOrElse(throw new IllegalStateException(s"Missing union model for ${parameter.pythonTypeName}"))
      val member      = union.members.headOption.getOrElse {
        throw new IllegalStateException(s"Union ${union.name} has no members for integration test sampling")
      }
      val sampleValue = sampleLiteral(context, member.pythonTypeName, variant, member.name)
      s"""{"${member.name}": $sampleValue}"""
    } else {
      sampleLiteral(context, parameter.pythonTypeName, variant, parameter.name)
    }

  private def sampleMemberExpression(
      context: SqlCodegenServiceContext,
      member: SqlCodegenMember,
      variant: SampleVariant
  ): String =
    sampleLiteral(context, member.pythonTypeName, variant, member.name)

  private def sampleLiteral(
      context: SqlCodegenServiceContext,
      pythonTypeName: String,
      variant: SampleVariant,
      seed: String
  ): String = {
    val suffix =
      variant match {
        case SampleVariant.Initial => seed
        case SampleVariant.Updated => s"updated-$seed"
      }

    pythonTypeName match {
      case "str"                                           => s"\"integration-$suffix\""
      case "int"                                           => if (variant == SampleVariant.Initial) "42" else "84"
      case "float"                                         => if (variant == SampleVariant.Initial) "3.5" else "7.0"
      case "bool"                                          => "True"
      case "bytes"                                         => s"b\"integration-$suffix\""
      case "datetime"                                      => "datetime.now(timezone.utc)"
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
        val memberValue = sampleLiteral(context, member.pythonTypeName, variant, member.name)
        s"""{"${member.name}": $memberValue}"""
      case other                                           =>
        throw new IllegalArgumentException(s"Unsupported integration test sample type: $other")
    }
  }

  private def collectExtraImports(context: SqlCodegenServiceContext): List[String] = {
    val needsDatetime =
      context.operations.exists(_.parameters.exists(_.pythonTypeName == "datetime")) ||
        context.models.exists(_.members.exists(_.pythonTypeName == "datetime")) ||
        context.unions.exists(_.members.exists(_.pythonTypeName == "datetime"))

    if (needsDatetime) {
      List("from datetime import datetime, timezone")
    } else {
      Nil
    }
  }
}
