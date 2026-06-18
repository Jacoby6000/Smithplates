package com.jacoby6000.smithplates.sql.service.renderer

import com.jacoby6000.smithplates.sql.*
import com.jacoby6000.smithplates.sql.ddl.renderer.common.SqlSchemaDdlRenderer
import com.jacoby6000.smithplates.sql.ddl.renderer.common.SqlShared
import com.jacoby6000.smithplates.sql.model.*
import com.jacoby6000.smithplates.sql.service.SqlQueries

import java.nio.charset.StandardCharsets
import java.util.UUID

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
        val testImports     = collectTestImports(context, insert, selectOne, updateOperation)
        val insertTableName =
          sqlOperations
            .find(_.sql.exists(_.queryKind == "insert"))
            .flatMap(_.sql.map(_.tableName))
        Some(
          SqlCodegenIntegrationTestContext(
            schemaDdl = schemaDdl(schema, sqlOperations, queries, context.dialectKey, schemaDdlRenderers),
            setupSqlStatements = insertTableName.toList.flatMap(requiredForeignKeySetupStatements(schema, _)),
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

  final private case class SeedTable(
      table: SqlTable,
      valueSeeds: Map[String, String],
      variant: SampleVariant
  )

  private def requiredForeignKeySetupStatements(schema: SqlSchema, primaryTableName: String): List[String] = {
    val tableByName    = schema.tables.map(table => table.name -> table).toMap
    val tableByShapeId = schema.tables.map(table => table.shapeId -> table).toMap

    def requiredForeignKeys(table: SqlTable): List[SqlForeignKey] =
      table.foreignKeys.filter { foreignKey =>
        table.columns.find(_.name == foreignKey.column).exists(column => !column.nullable)
      }

    def collectDependencies(table: SqlTable, seen: Set[String], variant: SampleVariant): List[SeedTable] =
      requiredForeignKeys(table).flatMap { foreignKey =>
        tableByShapeId.get(foreignKey.referencesShape).toList.flatMap { targetTable =>
          if (targetTable.name == primaryTableName || seen.contains(targetTable.name)) {
            Nil
          } else {
            collectDependencies(targetTable, seen + targetTable.name, variant) :+
              SeedTable(targetTable, Map(foreignKey.referencesColumn -> foreignKey.column), variant)
          }
        }
      }

    val seedTables =
      tableByName
        .get(primaryTableName)
        .toList
        .flatMap(table =>
          collectDependencies(table, Set(table.name), SampleVariant.Initial) ++
            collectDependencies(table, Set(table.name), SampleVariant.Updated))
        .foldLeft(Vector.empty[SeedTable]) { (orderedSeeds, seed) =>
          val existingIndex =
            orderedSeeds.indexWhere(existing => existing.table == seed.table && existing.variant == seed.variant)
          if (existingIndex < 0) {
            orderedSeeds :+ seed
          } else {
            val existing = orderedSeeds(existingIndex)
            orderedSeeds.updated(existingIndex, existing.copy(valueSeeds = existing.valueSeeds ++ seed.valueSeeds))
          }
        }

    seedTables
      .flatMap { seed =>
        val table      = seed.table
        val variant    = seed.variant
        val valueSeeds = seed.valueSeeds
        val columns    =
          table.columns.filter(column =>
            table.primaryKeys.contains(column.name) || (!column.nullable && column.autoGeneration.isEmpty))
        if (columns.isEmpty) {
          None
        } else {
          val columnNames = columns.map(_.name).mkString(", ")
          val values      =
            columns
              .map(column =>
                sampleSqlLiteral(column.columnType, valueSeeds.getOrElse(column.name, column.name), variant))
              .mkString(", ")
          Some(s"INSERT INTO ${table.name} ($columnNames) VALUES ($values) ON CONFLICT DO NOTHING;")
        }
      }
      .distinct
      .toList
  }

  private def sampleSqlLiteral(columnType: SqlColumnType, seed: String, variant: SampleVariant): String = {
    val suffix =
      variant match {
        case SampleVariant.Initial => seed
        case SampleVariant.Updated => s"updated-$seed"
      }

    columnType match {
      case SqlColumnType.Uuid                                       =>
        singleQuoted(uuidSample(suffix))
      case SqlColumnType.Text | SqlColumnType.Varchar(_)            =>
        singleQuoted(s"integration-$suffix")
      case SqlColumnType.StringEnum(_, _, values)                   =>
        singleQuoted(values.headOption.getOrElse(s"integration-$suffix"))
      case SqlColumnType.Integer | SqlColumnType.BigInt             =>
        "42"
      case SqlColumnType.IntEnum(_, values)                         =>
        values.headOption.map(_.toString).getOrElse("42")
      case SqlColumnType.Boolean                                    =>
        "true"
      case SqlColumnType.Blob                                       =>
        "X'00'"
      case SqlColumnType.Json                                       =>
        singleQuoted("{}")
      case SqlColumnType.Timestamp(SqlTimestampFormat.EpochSeconds) =>
        "1704067200"
      case SqlColumnType.Timestamp(SqlTimestampFormat.DateTime)     =>
        singleQuoted("2024-01-01T00:00:00Z")
    }
  }

  private def singleQuoted(value: String): String =
    s"'${value.replace("'", "''")}'"

  private def uuidSample(seed: String): String =
    UUID.nameUUIDFromBytes(s"integration-$seed".getBytes(StandardCharsets.UTF_8)).toString

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
  ): Option[SqlCodegenIntegrationTestOperation] =
    Some(
      SqlCodegenIntegrationTestOperation(
        name = operation.name,
        callArguments = renderCallArguments(context, operation.parameters, SampleVariant.Initial, enumSamples),
        updatedCallArguments = None,
        outputShapeId = operation.outputShapeId,
        outputValueAccessor = outputValueAccessor(operation),
        resultAssertions = Nil,
        updatedResultAssertions = Nil
      )
    )

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
        outputValueAccessor = outputValueAccessor(operation),
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
  ): Option[SqlCodegenIntegrationTestOperation] =
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
        outputValueAccessor = outputValueAccessor(operation),
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
        outputValueAccessor = outputValueAccessor(operation),
        resultAssertions = Nil,
        updatedResultAssertions = Nil
      )
    )

  private def outputValueAccessor(operation: SqlCodegenOperation): String =
    operation.sql match {
      case Some(sql) if sql.queryKind == "insert" && sql.outputKind == "structure" =>
        sql.resultFields.headOption.map(field => s".${field.fieldName}").getOrElse("")
      case Some(sql) if sql.outputKind == "booleanStructure"                       =>
        sql.booleanResultFieldName.map(fieldName => s".$fieldName").getOrElse("")
      case _                                                                       =>
        ""
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
      case "String"                                        =>
        val sample = if (isIdentifierSeed(seed)) uuidSample(suffix) else s"integration-$suffix"
        s"\"$sample\""
      case "Integer" | "Long" | "BigInteger"               => if (variant == SampleVariant.Initial) "42" else "84"
      case "Float" | "Double"                              => if (variant == SampleVariant.Initial) "3.5" else "7.0"
      case "Boolean"                                       => "True"
      case "Blob"                                          => s"b\"integration-$suffix\""
      case "Document"                                      => "{\"integration\": True}"
      case "Timestamp"                                     => "datetime.now(timezone.utc)"
      case other if context.uuidTypeNames.contains(other)  =>
        s"\"${uuidSample(suffix)}\""
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

  private def isIdentifierSeed(seed: String): Boolean =
    seed == "id" || seed.endsWith("_id")

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
}
