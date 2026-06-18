package com.jacoby6000.smithplates.sql.service.renderer

import com.jacoby6000.smithplates.sql.*
import com.jacoby6000.smithplates.sql.ddl.renderer.common.SqlSchemaDdlRenderer
import com.jacoby6000.smithplates.sql.ddl.renderer.common.SqlShared
import com.jacoby6000.smithplates.sql.model.*
import com.jacoby6000.smithplates.sql.service.SqlQueries
import software.amazon.smithy.model.shapes.ShapeId

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

    val enumSamples        = enumSampleLiterals(context, schema)
    val insertOperation    =
      sqlOperations.find(_.sql.exists(_.queryKind == "insert")).flatMap(buildInsertOperation(context, _, enumSamples))
    val selectOneOperation =
      sqlOperations
        .find(_.sql.exists(_.queryKind == "selectOne"))
        .flatMap(buildSelectOneOperation(context, _, enumSamples))
    val updateOperation    =
      sqlOperations.find(_.sql.exists(_.queryKind == "update")).flatMap(buildUpdateOperation(context, _, enumSamples))
    val deleteOperation    =
      sqlOperations.find(_.sql.exists(_.queryKind == "delete")).flatMap(buildDeleteOperation)

    (insertOperation, selectOneOperation) match {
      case (Some(insert), Some(selectOne)) =>
        val insertSqlOperation =
          sqlOperations.find(_.sql.exists(_.queryKind == "insert"))
        val seedStatements     =
          insertSqlOperation
            .map(operation => fixtureSeedStatements(context, schema, operation, enumSamples))
            .getOrElse(Nil)
        val testImports        = collectTestImports(context, insert, selectOne, updateOperation)
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
              context.packageName,
              context.name,
              context.dialectKey,
              testImports
            ),
            fixtureSeedStatements = seedStatements
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

  private def enumSampleLiterals(
      context: SqlCodegenServiceContext,
      schema: SqlSchema
  ): Map[String, String] =
    schema.tables.flatMap { table =>
      context.models.find(_.shapeId == table.shapeId).toList.flatMap { model =>
        model.members.flatMap { member =>
          table.columns.find(_.name == member.name).flatMap { column =>
            column.columnType match {
              case SqlColumnType.StringEnum(_, _, values) if values.nonEmpty =>
                Some(member.typeName -> s"\"${values.head}\"")
              case SqlColumnType.IntEnum(_, values) if values.nonEmpty       =>
                Some(member.typeName -> values.head.toString)
              case _                                                         =>
                None
            }
          }
        }
      }
    }.toMap

  private def buildInsertOperation(
      context: SqlCodegenServiceContext,
      operation: SqlCodegenOperation,
      enumSamples: Map[String, String]
  ): Option[SqlCodegenIntegrationTestOperation] = {
    val entityIdMemberName =
      operation.sql.flatMap(_.resultFields.headOption).map(_.fieldName).orElse(Some("id"))
    Some(
      SqlCodegenIntegrationTestOperation(
        name = operation.name,
        callArguments = renderCallArguments(context, operation.parameters, SampleVariant.Initial, enumSamples),
        updatedCallArguments = None,
        outputShapeId = operation.outputShapeId,
        entityIdMemberName = entityIdMemberName,
        mutationResultMemberName = None,
        resultAssertions = Nil,
        updatedResultAssertions = Nil
      )
    )
  }

  private def buildSelectOneOperation(
      context: SqlCodegenServiceContext,
      operation: SqlCodegenOperation,
      enumSamples: Map[String, String]
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
        entityIdMemberName = None,
        mutationResultMemberName = None,
        resultAssertions = resultAssertions(context, insertParameters, "fetched", SampleVariant.Initial, enumSamples),
        updatedResultAssertions =
          resultAssertions(context, updatedParameterSource, "fetched_after_update", SampleVariant.Updated, enumSamples)
      )
    )
  }

  private def buildUpdateOperation(
      context: SqlCodegenServiceContext,
      operation: SqlCodegenOperation,
      enumSamples: Map[String, String]
  ): Option[SqlCodegenIntegrationTestOperation] = {
    val mutationMemberName    = operation.sql.flatMap(_.mutationResultMemberName)
    val hasReturningStructure = operation.sql.exists(sql => sql.resultFields.nonEmpty)
    val resultAssertions      =
      if (hasReturningStructure) {
        val outputTypeName =
          operation.outputTypeName.getOrElse(
            throw new IllegalStateException(s"Missing output type for returning update ${operation.name}")
          )
        List(
          s"assert isinstance(updated, $outputTypeName)",
          "assert updated.id == entity_id"
        )
      } else {
        mutationMemberName.toList.map(memberName => s"assert updated.$memberName is True")
      }
    Some(
      SqlCodegenIntegrationTestOperation(
        name = operation.name,
        callArguments = {
          val valueParameters = operation.parameters.filterNot(_.name == "id")
          if (valueParameters.nonEmpty) {
            s"${renderCallArguments(context, valueParameters, SampleVariant.Updated, enumSamples)}, id=entity_id"
          } else {
            "id=entity_id"
          }
        },
        updatedCallArguments = None,
        outputShapeId = operation.outputShapeId,
        entityIdMemberName = None,
        mutationResultMemberName = mutationMemberName,
        resultAssertions = resultAssertions,
        updatedResultAssertions = Nil
      )
    )
  }

  private def buildDeleteOperation(operation: SqlCodegenOperation): Option[SqlCodegenIntegrationTestOperation] = {
    val mutationMemberName = operation.sql.flatMap(_.mutationResultMemberName)
    Some(
      SqlCodegenIntegrationTestOperation(
        name = operation.name,
        callArguments = "id=entity_id",
        updatedCallArguments = None,
        outputShapeId = operation.outputShapeId,
        entityIdMemberName = None,
        mutationResultMemberName = mutationMemberName,
        resultAssertions = mutationMemberName.toList.map(memberName => s"assert deleted.$memberName is True"),
        updatedResultAssertions = Nil
      )
    )
  }

  private def remapAssertionTarget(
      assertions: List[String],
      from: String,
      to: String
  ): List[String] =
    assertions.map(_.replace(s"$from.", s"$to."))

  private def renderCallArguments(
      context: SqlCodegenServiceContext,
      parameters: List[SqlCodegenParameter],
      variant: SampleVariant,
      enumSamples: Map[String, String]
  ): String =
    parameters
      .map(parameter => s"${parameter.name}=${sampleExpression(context, parameter, variant, enumSamples)}")
      .mkString(", ")

  private def resultAssertions(
      context: SqlCodegenServiceContext,
      parameters: List[SqlCodegenParameter],
      targetExpression: String,
      variant: SampleVariant,
      enumSamples: Map[String, String]
  ): List[String] =
    parameters.flatMap { parameter =>
      if (parameter.isStructure) {
        structureMembers(context, parameter).map { member =>
          s"""assert $targetExpression.${parameter.name}.${member.name} == ${sampleMemberExpression(
              context,
              member,
              variant,
              enumSamples
            )}"""
        }
      } else if (isUnionParameter(context, parameter)) {
        unionAssertion(context, parameter, targetExpression, variant, enumSamples).toList
      } else {
        val sample    = sampleExpression(context, parameter, variant, enumSamples)
        val assertion =
          if (sample == "None") {
            s"assert $targetExpression.${parameter.name} is None"
          } else {
            s"assert $targetExpression.${parameter.name} == $sample"
          }
        List(assertion)
      }
    }

  private def unionAssertion(
      context: SqlCodegenServiceContext,
      parameter: SqlCodegenParameter,
      targetExpression: String,
      variant: SampleVariant,
      enumSamples: Map[String, String]
  ): Option[String] =
    Some(
      s"""assert $targetExpression.${parameter.name} == ${sampleExpression(context, parameter, variant, enumSamples)}"""
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
      variant: SampleVariant,
      enumSamples: Map[String, String]
  ): String =
    if (parameter.optional) {
      "None"
    } else if (parameter.isStructure) {
      val members   = structureMembers(context, parameter)
      val arguments =
        members
          .map(member => s"${member.name}=${sampleMemberExpression(context, member, variant, enumSamples)}")
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
      val sampleValue = sampleLiteral(context, member.typeName, variant, member.name, enumSamples)
      s"""{"${member.name}": $sampleValue}"""
    } else {
      sampleLiteral(context, parameter.typeName, variant, parameter.name, enumSamples)
    }

  private def sampleMemberExpression(
      context: SqlCodegenServiceContext,
      member: SqlStructureMember,
      variant: SampleVariant,
      enumSamples: Map[String, String]
  ): String =
    sampleLiteral(context, member.typeName, variant, member.name, enumSamples)

  private def sampleLiteral(
      context: SqlCodegenServiceContext,
      typeName: String,
      variant: SampleVariant,
      seed: String,
      enumSamples: Map[String, String]
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
      case "Document"                                      => "{\"integration\": true}"
      case "Timestamp"                                     => "datetime.now(timezone.utc)"
      case other if context.models.exists(_.name == other) =>
        val structure = context.models.find(_.name == other).get
        val arguments =
          structure.members
            .map(member => s"${member.name}=${sampleMemberExpression(context, member, variant, enumSamples)}")
            .mkString(", ")
        s"$other($arguments)"
      case other if context.unions.exists(_.name == other) =>
        val union       = context.unions.find(_.name == other).get
        val member      = union.members.head
        val memberValue = sampleLiteral(context, member.typeName, variant, member.name, enumSamples)
        s"""{"${member.name}": $memberValue}"""
      case other if enumSamples.contains(other)            =>
        enumSamples(other)
      case other if typeName.startsWith("List[")           =>
        val inner = typeName.substring(5, typeName.length - 1)
        s"[${sampleLiteral(context, inner, variant, seed, enumSamples)}]"
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
    val serviceModuleBase           =
      ScalateSspTemplateEngine.renderServiceModuleBaseName("python/src/db", context.name)
    val assertionText               =
      List(
        insertOperation.resultAssertions.mkString("\n"),
        updateOperation.map(_.resultAssertions.mkString("\n")).getOrElse(""),
        selectOneOperation.resultAssertions.mkString("\n"),
        selectOneOperation.updatedResultAssertions.mkString("\n")
      ).mkString("\n")
    val callText                    =
      List(
        Some(insertOperation.callArguments),
        updateOperation.map(_.callArguments),
        updateOperation.flatMap(_.updatedCallArguments)
      ).flatten.mkString(" ")
    val operationResultNames        =
      context.models
        .filter(model =>
          model.namespace == SqlSelectOneDerivedOutputBuilder.DerivedNamespace ||
            model.namespace == SqlDeriveReturningDerivedOutputBuilder.DerivedNamespace)
        .map(_.name)
        .toSet
    val selectOneDerivedResultNames =
      context.models
        .filter(_.namespace == SqlSelectOneDerivedOutputBuilder.DerivedNamespace)
        .map(_.name)
        .toSet
    val returningDerivedResultNames =
      context.models
        .filter(_.namespace == SqlDeriveReturningDerivedOutputBuilder.DerivedNamespace)
        .map(_.name)
        .toSet
    val tableModelNames             =
      context.models
        .filter(model =>
          model.namespace != SqlSelectOneDerivedOutputBuilder.DerivedNamespace &&
            model.namespace != SqlDeriveReturningDerivedOutputBuilder.DerivedNamespace)
        .map(_.name)
        .toSet
    val unionNames                  = context.unions.map(_.name).toSet

    val modelImportNames    = scala.collection.mutable.LinkedHashSet.empty[String]
    val protocolImportNames = scala.collection.mutable.LinkedHashSet.empty[String]

    def assignImportName(name: String): Unit =
      if (selectOneDerivedResultNames.contains(name)) {
        protocolImportNames += name
      } else if (returningDerivedResultNames.contains(name)) {
        modelImportNames += name
      } else if (tableModelNames.contains(name) || unionNames.contains(name)) {
        modelImportNames += name
      }

    insertOperation.outputShapeId.foreach(shapeId => assignImportName(shapeId.getName))
    updateOperation.flatMap(_.outputShapeId).foreach(shapeId => assignImportName(shapeId.getName))
    selectOneOperation.outputShapeId.foreach(shapeId => assignImportName(shapeId.getName))
    operationResultNames.foreach { name =>
      if (assertionText.contains(name)) {
        assignImportName(name)
      }
    }
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
      importBlocks += s"from ${SqlCodegenPythonImports.qualifiedModule(context.packageName, s"models/${serviceModuleBase}_models")} import ("
      importBlocks ++= modelImportNames.toList.sorted.map(name => s"    $name,")
      importBlocks += ")"
    }
    if (protocolImportNames.nonEmpty) {
      importBlocks += s"from ${SqlCodegenPythonImports.qualifiedModule(context.packageName, s"${serviceModuleBase}_protocol")} import ("
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

  private def fixtureSeedStatements(
      context: SqlCodegenServiceContext,
      schema: SqlSchema,
      insertOperation: SqlCodegenOperation,
      enumSamples: Map[String, String]
  ): List[String] = {
    val resolvedInsertTable =
      for {
        sql       <- insertOperation.sql.toList
        tableName <- Option(sql.tableName).filter(_.nonEmpty).toList
        table     <- schema.tables.find(_.name == tableName).toList
      } yield table

    resolvedInsertTable.headOption.fold(Nil: List[String]) { insertTable =>
      val parameterSamples =
        insertOperation.parameters.map { parameter =>
          parameter.name -> sqlSampleLiteral(
            context,
            parameter.typeName,
            SampleVariant.Initial,
            parameter.name,
            enumSamples)
        }.toMap

      val directReferenceTables =
        insertTable.foreignKeys
          .filter(foreignKey => parameterSamples.contains(foreignKey.column))
          .flatMap(foreignKey => schema.tables.find(_.shapeId == foreignKey.referencesShape))
          .distinctBy(_.shapeId)

      val orderedSeedTables =
        directReferenceTables
          .flatMap(table => collectFixtureSeedTables(schema, table, Set.empty))
          .distinctBy(_.shapeId)

      orderedSeedTables.map { table =>
        renderFixtureSeedInsert(schema, table, parameterSamples, context, enumSamples)
      }
    }
  }

  private def collectFixtureSeedTables(
      schema: SqlSchema,
      table: SqlTable,
      visiting: Set[ShapeId]
  ): List[SqlTable] =
    if (visiting.contains(table.shapeId)) {
      Nil
    } else {
      val nextVisiting = visiting + table.shapeId
      val dependencies =
        table.foreignKeys.flatMap { foreignKey =>
          schema.tables.find(_.shapeId == foreignKey.referencesShape).toList.flatMap { referenced =>
            collectFixtureSeedTables(schema, referenced, nextVisiting)
          }
        }
      dependencies :+ table
    }

  private def renderFixtureSeedInsert(
      schema: SqlSchema,
      table: SqlTable,
      parameterSamples: Map[String, String],
      context: SqlCodegenServiceContext,
      enumSamples: Map[String, String]
  ): String = {
    val seedColumns =
      table.columns.filter { column =>
        column.autoGeneration.isEmpty && (!column.nullable || referencingForeignKey(schema, table, column).isDefined)
      }

    val columnNames = seedColumns.map(_.name)
    val values      =
      seedColumns.map { column =>
        fixtureColumnSeedLiteral(schema, table, column, parameterSamples, context, enumSamples)
      }

    s"INSERT INTO ${table.name} (${columnNames.mkString(", ")}) VALUES (${values.mkString(", ")})"
  }

  private def fixtureColumnSeedLiteral(
      schema: SqlSchema,
      table: SqlTable,
      column: SqlColumn,
      parameterSamples: Map[String, String],
      context: SqlCodegenServiceContext,
      enumSamples: Map[String, String]
  ): String =
    parameterSamples
      .get(column.name)
      .orElse {
        referencingForeignKey(schema, table, column).map { foreignKey =>
          sqlSampleLiteral(
            context,
            smithyTypeNameForColumn(context, table, foreignKey.column),
            SampleVariant.Initial,
            foreignKey.column,
            enumSamples)
        }
      }
      .getOrElse {
        sqlSampleLiteral(
          context,
          smithyTypeNameForColumn(context, table, column.name),
          SampleVariant.Initial,
          column.name,
          enumSamples)
      }

  private def referencingForeignKey(
      schema: SqlSchema,
      referencedTable: SqlTable,
      referencedColumn: SqlColumn
  ): Option[SqlForeignKey] =
    schema.tables.flatMap(_.foreignKeys).find { foreignKey =>
      foreignKey.referencesShape == referencedTable.shapeId &&
      foreignKey.referencesColumn == referencedColumn.name
    }

  private def smithyTypeNameForColumn(
      context: SqlCodegenServiceContext,
      table: SqlTable,
      columnName: String
  ): String =
    context.models
      .find(_.shapeId == table.shapeId)
      .flatMap(_.members.find(_.name == columnName))
      .map(_.typeName)
      .getOrElse(
        table.columns
          .find(_.name == columnName)
          .map(_.columnType)
          .collect {
            case SqlColumnType.StringEnum(_, _, _) => "String"
            case SqlColumnType.IntEnum(_, _)       => "Integer"
            case SqlColumnType.Json                => "Document"
            case SqlColumnType.Timestamp(_)        => "Timestamp"
            case SqlColumnType.Blob                => "Blob"
            case SqlColumnType.Boolean             => "Boolean"
            case other                             => other.toString
          }
          .getOrElse("String")
      )

  private def sqlSampleLiteral(
      context: SqlCodegenServiceContext,
      typeName: String,
      variant: SampleVariant,
      seed: String,
      enumSamples: Map[String, String]
  ): String = {
    val suffix =
      variant match {
        case SampleVariant.Initial => seed
        case SampleVariant.Updated => s"updated-$seed"
      }

    typeName match {
      case "String"                                        => s"'integration-${escapeSqlLiteral(suffix)}'"
      case "Integer" | "Long" | "BigInteger"               => if (variant == SampleVariant.Initial) "42" else "84"
      case "Float" | "Double"                              => if (variant == SampleVariant.Initial) "3.5" else "7.0"
      case "Boolean"                                       => "TRUE"
      case "Blob"                                          => s"X'00'"
      case "Document"                                      => "'{\"integration\": true}'"
      case "Timestamp"                                     => "CURRENT_TIMESTAMP"
      case other if enumSamples.contains(other)            =>
        val pythonLiteral = enumSamples(other)
        s"'${escapeSqlLiteral(pythonLiteral.stripPrefix("\"").stripSuffix("\""))}'"
      case other if context.models.exists(_.name == other) =>
        throw new IllegalArgumentException(
          s"Unsupported fixture seed SQL type for nested structure column: $other"
        )
      case other if context.unions.exists(_.name == other) =>
        throw new IllegalArgumentException(s"Unsupported fixture seed SQL type for union column: $other")
      case other if typeName.startsWith("List[")           =>
        throw new IllegalArgumentException(s"Unsupported fixture seed SQL type for list column: $other")
      case other                                           =>
        throw new IllegalArgumentException(s"Unsupported fixture seed SQL type: $other")
    }
  }

  private def escapeSqlLiteral(value: String): String =
    value.replace("'", "''")
}
