package com.jacoby6000.smithplates.sql.service.renderer

import com.jacoby6000.smithplates.sql.*
import com.jacoby6000.smithplates.sql.ddl.renderer.common.SqlSchemaDdlRenderer
import com.jacoby6000.smithplates.sql.ddl.renderer.common.SqlShared
import com.jacoby6000.smithplates.sql.model.*
import com.jacoby6000.smithplates.sql.service.SqlQueries

import java.nio.charset.StandardCharsets
import java.util.UUID

object SqlCodegenIntegrationTestBuilder {
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

    val enumSamples        = internal.enumSampleLiterals(context, schema)
    val insertOperation    =
      sqlOperations
        .find(_.sql.exists(_.queryKind == "insert"))
        .flatMap(internal.buildInsertOperation(context, _, enumSamples))
    val selectOneOperation =
      sqlOperations
        .find(_.sql.exists(_.queryKind == "selectOne"))
        .flatMap(internal.buildSelectOneOperation(context, _, enumSamples))
    val updateOperation    =
      sqlOperations
        .find(_.sql.exists(_.queryKind == "update"))
        .flatMap(internal.buildUpdateOperation(context, _, enumSamples))
    val deleteOperation    =
      sqlOperations.find(_.sql.exists(_.queryKind == "delete")).flatMap(internal.buildDeleteOperation)

    (insertOperation, selectOneOperation) match {
      case (Some(insert), Some(selectOne)) =>
        val testImports     = internal.collectTestImports(context, insert, selectOne, updateOperation)
        val insertTableName =
          sqlOperations
            .find(_.sql.exists(_.queryKind == "insert"))
            .flatMap(_.sql.map(_.tableName))
        Some(
          SqlCodegenIntegrationTestContext(
            schemaDdl = internal.schemaDdl(schema, sqlOperations, queries, context.dialectKey, schemaDdlRenderers),
            setupSqlStatements = insertTableName.toList.flatMap(internal.requiredForeignKeySetupStatements(schema, _)),
            insertOperation = insert,
            selectOneOperation = selectOne,
            updateOperation = updateOperation,
            deleteOperation = deleteOperation,
            transactionCommitInTxAssertions = selectOne.resultAssertions,
            transactionCommitAfterAssertions =
              internal.remapAssertionTarget(selectOne.resultAssertions, "fetched", "fetched_after_commit"),
            extraImports = internal.collectExtraImports(insert, selectOne, updateOperation),
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

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    enum SampleVariant {
      case Initial
      case Updated
    }

    final case class SeedTable(
        table: SqlTable,
        valueSeeds: Map[String, String],
        variant: SampleVariant
    )

    def schemaDdl(
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

    def requiredForeignKeySetupStatements(schema: SqlSchema, primaryTableName: String): List[String] = {
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

    def sampleSqlLiteral(columnType: SqlColumnType, seed: String, variant: SampleVariant): String = {
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

    def singleQuoted(value: String): String =
      s"'${value.replace("'", "''")}'"

    def uuidSample(seed: String): String =
      UUID.nameUUIDFromBytes(s"integration-$seed".getBytes(StandardCharsets.UTF_8)).toString

    def enumSampleLiterals(
        context: SqlCodegenServiceContext,
        schema: SqlSchema
    ): Map[String, String] =
      schema.tables.flatMap { table =>
        context.models.find(_.shapeId == table.shapeId).toList.flatMap { model =>
          model.members.flatMap { member =>
            table.columns.find(_.name == member.name).flatMap { column =>
              column.columnType match {
                case _: SqlColumnType.StringEnum =>
                  context.stringEnums
                    .find(_.name == member.typeName)
                    .flatMap(_.members.headOption.map(enumMember =>
                      member.typeName -> s"${member.typeName}.${enumMember.name}"))
                case _: SqlColumnType.IntEnum    =>
                  context.intEnums
                    .find(_.name == member.typeName)
                    .flatMap(_.members.headOption.map(enumMember =>
                      member.typeName -> s"${member.typeName}.${enumMember.name}"))
                case _                           =>
                  None
              }
            }
          }
        }
      }.toMap

    def buildInsertOperation(
        context: SqlCodegenServiceContext,
        operation: SqlCodegenOperation,
        enumSamples: Map[String, String]
    ): Option[SqlCodegenIntegrationTestOperation] =
      Some(
        SqlCodegenIntegrationTestOperation(
          name = operation.name,
          callArguments = renderCallArguments(context, operation.parameters, SampleVariant.Initial, enumSamples),
          outputShapeId = operation.outputShapeId,
          outputValueAccessor = outputValueAccessor(operation),
          resultAssertions = Nil,
          updatedResultAssertions = Nil
        )
      )

    def buildSelectOneOperation(
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
          outputShapeId = operation.outputShapeId,
          outputValueAccessor = outputValueAccessor(operation),
          resultAssertions = resultAssertions(context, insertParameters, "fetched", SampleVariant.Initial, enumSamples),
          updatedResultAssertions = resultAssertions(
            context,
            updatedParameterSource,
            "fetched_after_update",
            SampleVariant.Updated,
            enumSamples)
        )
      )
    }

    def buildUpdateOperation(
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
          outputShapeId = operation.outputShapeId,
          outputValueAccessor = outputValueAccessor(operation),
          resultAssertions = Nil,
          updatedResultAssertions = Nil
        )
      )

    def buildDeleteOperation(operation: SqlCodegenOperation): Option[SqlCodegenIntegrationTestOperation] =
      Some(
        SqlCodegenIntegrationTestOperation(
          name = operation.name,
          callArguments = "id=entity_id",
          outputShapeId = operation.outputShapeId,
          outputValueAccessor = outputValueAccessor(operation),
          resultAssertions = Nil,
          updatedResultAssertions = Nil
        )
      )

    def outputValueAccessor(operation: SqlCodegenOperation): String =
      operation.sql match {
        case Some(sql) if sql.queryKind == "insert" && sql.outputKind == "structure" =>
          sql.resultFields.headOption.map(field => s".${field.fieldName}").getOrElse("")
        case Some(sql) if sql.outputKind == "booleanStructure"                       =>
          sql.booleanResultFieldName.map(fieldName => s".$fieldName").getOrElse("")
        case _                                                                       =>
          ""
      }

    def remapAssertionTarget(
        assertions: List[String],
        from: String,
        to: String
    ): List[String] =
      assertions.map(_.replace(s"""$from[""", s"""$to["""))

    def renderCallArguments(
        context: SqlCodegenServiceContext,
        parameters: List[SqlCodegenParameter],
        variant: SampleVariant,
        enumSamples: Map[String, String]
    ): String =
      parameters
        .map(parameter => s"${parameter.name}=${sampleExpression(context, parameter, variant, enumSamples)}")
        .mkString(", ")

    def resultAssertions(
        context: SqlCodegenServiceContext,
        parameters: List[SqlCodegenParameter],
        targetExpression: String,
        variant: SampleVariant,
        enumSamples: Map[String, String]
    ): List[String] =
      parameters.flatMap { parameter =>
        if (parameter.isStructure) {
          structureMembers(context, parameter).map { member =>
            s"""assert $targetExpression["${parameter.name}"]["${member.name}"] == ${sampleMemberExpression(
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
              s"assert $targetExpression[\"${parameter.name}\"] is None"
            } else {
              s"assert $targetExpression[\"${parameter.name}\"] == $sample"
            }
          List(assertion)
        }
      }

    def unionAssertion(
        context: SqlCodegenServiceContext,
        parameter: SqlCodegenParameter,
        targetExpression: String,
        variant: SampleVariant,
        enumSamples: Map[String, String]
    ): Option[String] =
      Some(
        s"""assert $targetExpression["${parameter.name}"] == ${sampleExpression(
            context,
            parameter,
            variant,
            enumSamples)}"""
      )

    def structureMembers(
        context: SqlCodegenServiceContext,
        parameter: SqlCodegenParameter
    ): List[SqlStructureMember] =
      parameter.structureShapeId
        .flatMap(shapeId => context.models.find(_.shapeId == shapeId))
        .map(_.members)
        .getOrElse(Nil)

    def isUnionParameter(context: SqlCodegenServiceContext, parameter: SqlCodegenParameter): Boolean =
      context.unions.exists(_.name == parameter.typeName)

    def sampleExpression(
        context: SqlCodegenServiceContext,
        parameter: SqlCodegenParameter,
        variant: SampleVariant,
        enumSamples: Map[String, String]
    ): String =
      if (parameter.optional) {
        "None"
      } else if (parameter.isStructure) {
        val members = structureMembers(context, parameter)
        val entries =
          members
            .map(member => s"\"${member.name}\": ${sampleMemberExpression(context, member, variant, enumSamples)}")
            .mkString(", ")
        s"{$entries}"
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

    def sampleMemberExpression(
        context: SqlCodegenServiceContext,
        member: SqlStructureMember,
        variant: SampleVariant,
        enumSamples: Map[String, String]
    ): String =
      sampleLiteral(context, member.typeName, variant, member.name, enumSamples)

    def sampleLiteral(
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
          val entries   =
            structure.members
              .map(member => s"\"${member.name}\": ${sampleMemberExpression(context, member, variant, enumSamples)}")
              .mkString(", ")
          s"{$entries}"
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

    def isIdentifierSeed(seed: String): Boolean =
      seed == "id" || seed.endsWith("_id")

    def collectTestImports(
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
          updateOperation.map(_.callArguments)
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
      val enumNames            = SqlCodegenPythonImports.enumTypeNameSet(context)

      val modelImportNames    = scala.collection.mutable.LinkedHashSet.empty[String]
      val protocolImportNames = scala.collection.mutable.LinkedHashSet.empty[String]
      val enumImportNames     = scala.collection.mutable.LinkedHashSet.empty[String]

      def assignImportName(name: String): Unit =
        if (operationResultNames.contains(name)) {
          protocolImportNames += name
        } else if (tableModelNames.contains(name) || unionNames.contains(name)) {
          modelImportNames += name
        } else if (enumNames.contains(name)) {
          enumImportNames += name
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
      val assertionText =
        (selectOneOperation.resultAssertions ++ selectOneOperation.updatedResultAssertions).mkString("\n")
      enumNames.foreach { name =>
        if (callText.contains(name) || assertionText.contains(name)) {
          enumImportNames += name
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
      enumImportNames.toList.sorted.foreach { enumName =>
        importBlocks +=
          s"from ${SqlCodegenPythonImports.qualifiedModule(context.packageName, SqlCodegenSnakeCase.toSnakeCase(enumName))} import $enumName"
      }

      val blocks = importBlocks.result()
      if (blocks.isEmpty) {
        ""
      } else {
        blocks.mkString("", "\n", "\n")
      }
    }

    def collectExtraImports(
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
}
