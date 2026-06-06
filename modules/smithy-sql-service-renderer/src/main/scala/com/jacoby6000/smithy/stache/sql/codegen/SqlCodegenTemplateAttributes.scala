package com.jacoby6000.smithy.stache.sql.codegen

import com.jacoby6000.smithy.stache.sql.query.SqlBindPlaceholder

object SqlCodegenTemplateAttributes {
  def forService(context: SqlCodegenServiceContext, templateRoot: String): Map[String, Any] = {
    val helperFlags = SqlCodegenHelperMetadata.collect(context)
    val attributes  =
      Map(
        "serviceName"              -> context.name,
        "serviceClassName"         -> context.name,
        "serviceFileName"          -> context.fileName,
        "serviceNamespace"         -> context.namespace,
        "serviceShapeId"           -> context.shapeId.toString,
        "serviceVersion"           -> context.version,
        "implementationClassName"  -> context.implementationClassName,
        "implementationModuleName" -> context.implementationModuleName,
        "hasSqlOperations"         -> context.hasSqlOperations,
        "dialectKey"               -> context.dialectKey,
        "isPostgresDialect"        -> (context.dialectKey == "postgres"),
        "isSqliteDialect"          -> (context.dialectKey == "sqlite"),
        "rowTypeName"              -> SqlCodegenDialectConfig.rowTypeName(context.dialectKey),
        "i4"                       -> "    ",
        "i8"                       -> "        ",
        "i12"                      -> "            ",
        "models"                   -> withLastFlag(context.models.map(modelAttributes(templateRoot, _))),
        "unions"                   -> withLastFlag(context.unions.map(unionAttributes)),
        "hasUnions"                -> context.unions.nonEmpty,
        "operations"               -> withLastFlag(
          context.operations.map(operationAttributes(context.bindPlaceholderStyle, context.dialectKey, _))
        ),
        "hasIntegrationTest"       -> context.integrationTest.isDefined,
        "integrationTest"          -> context.integrationTest.map(integrationTestAttributes).orNull
      ) ++ helperFlags

    attributes + (
      "rowReaderHelpers" ->
        MustacheTemplateEngine
          .renderClasspathPartial(
            templateRoot,
            "partials/helpers/row_reader_helpers",
            attributes
          )
          .stripTrailing()
    )
  }

  def renderOutputPath(pattern: String, context: SqlCodegenServiceContext): String =
    pattern
      .replace("{{serviceName}}", context.name)
      .replace("{{serviceClassName}}", context.name)
      .replace("{{serviceFileName}}", context.fileName)
      .replace("{{serviceNamespace}}", context.namespace)
      .replace("{{serviceShapeId}}", context.shapeId.toString)
      .replace("{{serviceVersion}}", context.version)
      .replace("{{implementationClassName}}", context.implementationClassName)

  private def modelAttributes(templateRoot: String, model: SqlCodegenStructure): Map[String, Any] = {
    val members        = withLastFlag(model.members.map(memberAttributes))
    Map(
      "name"        -> model.name,
      "className"   -> model.name,
      "shapeId"     -> model.shapeId.toString,
      "namespace"   -> model.namespace,
      "members"     -> members,
      "memberLines" -> MustacheTemplateEngine.renderClasspathPartial(
        templateRoot,
        "partials/models/member_lines",
        Map(
          "members" -> members,
          "i4"      -> "    "
        )
      ),
      "hasMembers"  -> model.members.nonEmpty
    )
  }

  private def unionAttributes(union: SqlCodegenUnion): Map[String, Any] =
    Map(
      "name"           -> union.name,
      "className"      -> union.name,
      "shapeId"        -> union.shapeId.toString,
      "namespace"      -> union.namespace,
      "members"        -> withLastFlag(union.members.map(unionMemberAttributes)),
      "unionTypeAlias" -> union.members.map(_.variantClassName).mkString(" | ")
    )

  private def unionMemberAttributes(member: SqlCodegenUnionMember): Map[String, Any] =
    Map(
      "name"             -> member.name,
      "variantClassName" -> member.variantClassName,
      "pythonTypeName"   -> member.pythonTypeName
    )

  private def memberAttributes(member: SqlCodegenMember): Map[String, Any] =
    Map(
      "name"               -> member.name,
      "typeName"           -> member.typeName,
      "pythonTypeName"     -> member.pythonTypeName,
      "optional"           -> member.optional,
      "required"           -> !member.optional,
      "isStructure"        -> member.isStructure,
      "structureClassName" -> member.structureShapeId.map(_.getName).orNull
    )

  private def operationAttributes(
      bindPlaceholderStyle: SqlBindPlaceholder,
      dialectKey: String,
      operation: SqlCodegenOperation
  ): Map[String, Any] = {
    val base =
      Map(
        "i4"                 -> "    ",
        "i8"                 -> "        ",
        "i12"                -> "            ",
        "i16"                -> "                ",
        "name"               -> operation.name,
        "methodName"         -> operation.methodName,
        "operationShapeId"   -> operation.shapeId.toString,
        "parameters"         -> withLastFlag(operation.parameters.map(parameterAttributes)),
        "hasParameters"      -> operation.parameters.nonEmpty,
        "hasOutput"          -> operation.outputClassName.isDefined,
        "outputClassName"    -> operation.outputClassName.orNull,
        "outputTypeName"     -> operation.outputClassName.getOrElse("Unit"),
        "errors"             -> withLastFlag(operation.errors.map(errorAttributes)),
        "hasErrors"          -> operation.errors.nonEmpty,
        "responseUnion"      -> operation.responseUnion,
        "pythonResponseType" -> operation.pythonResponseType,
        "hasSql"             -> operation.sql.isDefined
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
        val canUseClassRow     =
          dialectKey == "postgres" && SqlCodegenHelperMetadata.canUsePostgresClassRow(sql)
        val usesDictRowFactory =
          dialectKey == "postgres" && (
            (sql.queryKind == "selectOne" && !canUseClassRow) ||
              (sql.queryKind == "insert" && sql.outputKind == "structure")
          )
        base ++ Map[String, Any](
          "queryKind"            -> sql.queryKind,
          "sqlStatement"         -> SqlBindPlaceholder.format(
            sql.sqlStatement.segments,
            bindPlaceholderStyle
          ),
          "tableName"            -> sql.tableName,
          "bindParameters"       -> withBindParameterPositionFlags(sql.bindParameters.map(bindParameterAttributes)),
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
      "pythonTypeName"     -> parameter.pythonTypeName,
      "optional"           -> parameter.optional,
      "required"           -> !parameter.optional,
      "isStructure"        -> parameter.isStructure,
      "structureClassName" -> parameter.structureShapeId.map(_.getName).orNull
    )

  private def bindParameterAttributes(bindParameter: SqlCodegenBindParameter): Map[String, Any] =
    Map(
      "memberName"       -> bindParameter.memberName,
      "pythonExpression" -> bindParameter.pythonExpression
    )

  private def resultFieldAttributes(resultField: SqlCodegenResultField): Map[String, Any] =
    Map(
      "fieldName"             -> resultField.fieldName,
      "columnName"            -> resultField.columnName,
      "columnNameLiteral"     -> s"\"${resultField.columnName}\"",
      "columnIndex"           -> resultField.columnIndex,
      "pythonTypeName"        -> resultField.pythonTypeName,
      "isJson"                -> resultField.isJson,
      "jsonReadExpression"    -> resultField.jsonReadExpression.orNull,
      "jsonReadExpressionCol" -> jsonReadExpressionCol(resultField),
      "rowReader"             -> resultField.rowReader,
      "rowReaderCol"          -> s"${resultField.rowReader}_col"
    )

  private def jsonReadExpressionCol(resultField: SqlCodegenResultField): String =
    if (resultField.isJson) {
      s"_read_${resultField.pythonTypeName}_col(row, \"${resultField.columnName}\")"
    } else {
      ""
    }

  private def errorAttributes(error: SqlCodegenErrorType): Map[String, Any] =
    Map(
      "name"      -> error.name,
      "className" -> error.className,
      "shapeId"   -> error.shapeId.toString
    )

  private def integrationTestAttributes(
      integrationTest: SqlCodegenIntegrationTestContext
  ): Map[String, Any]                 =
    Map(
      "schemaDdl"                 -> integrationTest.schemaDdl,
      "implementationModuleName"  -> integrationTest.implementationModuleName,
      "serviceFixtureName"        -> integrationTest.serviceFixtureName,
      "extraImports"              -> integrationTest.extraImports.mkString("", "\n", "\n"),
      "insertOperation"           -> integrationOperationAttributes(integrationTest.insertOperation),
      "selectOneOperation"        -> integrationOperationAttributes(integrationTest.selectOneOperation),
      "updateOperation"           -> integrationTest.updateOperation.map(integrationOperationAttributes).orNull,
      "deleteOperation"           -> integrationTest.deleteOperation.map(integrationOperationAttributes).orNull,
      "hasUpdateOperation"        -> integrationTest.updateOperation.isDefined,
      "hasDeleteOperation"        -> integrationTest.deleteOperation.isDefined,
      "selectOneResultAssertions" -> integrationTest.selectOneResultAssertions,
      "updateLifecycleBlock"      -> integrationTest.updateLifecycleBlock.orNull,
      "deleteLifecycleBlock"      -> integrationTest.deleteLifecycleBlock.orNull,
      "hasUpdateLifecycleBlock"   -> integrationTest.updateLifecycleBlock.isDefined,
      "hasDeleteLifecycleBlock"   -> integrationTest.deleteLifecycleBlock.isDefined
    )

  private def integrationOperationAttributes(
      operation: SqlCodegenIntegrationTestOperation
  ): Map[String, Any]                 =
    Map(
      "methodName"                 -> operation.methodName,
      "callArguments"              -> operation.callArguments,
      "outputClassName"            -> operation.outputClassName.orNull,
      "hasOutputClassName"         -> operation.outputClassName.isDefined,
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

  private def withBindParameterPositionFlags(
      items: List[Map[String, Any]]
  ): List[Map[String, Any]] =
    items.zipWithIndex.map { case (item, index) =>
      item ++ Map(
        "first" -> (index == 0),
        "last"  -> (index == items.length - 1),
        "only"  -> (items.length == 1)
      )
    }
}

private object SqlCodegenHelperMetadata {
  def collect(context: SqlCodegenServiceContext): Map[String, Any] = {
    val operations        = context.operations
    val isPostgresDialect = context.dialectKey == "postgres"
    val rowReaders        = collectRowReaders(operations, isPostgresDialect)
    val rowReadersCol     =
      if (isPostgresDialect) {
        collectRowReadersCol(operations)
      } else {
        Set.empty[String]
      }
    val timestampBinds    = collectTimestampBindHelpers(operations)
    val usesJson          = usesJsonSerialization(operations)
    val usedJsonTypes     = collectUsedJsonPythonTypeNames(operations)
    val usedJsonTypesCol  =
      if (isPostgresDialect) {
        collectUsedJsonPythonTypeNamesCol(operations)
      } else {
        Set.empty[String]
      }
    val needsClassRow     =
      isPostgresDialect &&
        operations.exists(op => op.sql.exists(canUsePostgresClassRow))
    val needsDictRow      =
      isPostgresDialect && operations.exists(op =>
        op.sql.exists { sql =>
          val classRow = canUsePostgresClassRow(sql)
          (sql.queryKind == "selectOne" && !classRow) ||
          (sql.queryKind == "insert" && sql.outputKind == "structure")
        })

    Map(
      "needsClassRowImport"            -> needsClassRow,
      "needsDictRowImport"             -> needsDictRow,
      "needsPostgresRowFactoryImports" -> (needsClassRow || needsDictRow),
      "needsUuidTextLoader"            -> needsClassRow,
      "usesReadStr"                    -> rowReaders.contains("_read_str"),
      "usesReadInt"                    -> rowReaders.contains("_read_int"),
      "usesReadFloat"                  -> rowReaders.contains("_read_float"),
      "usesReadBool"                   -> rowReaders.contains("_read_bool"),
      "usesReadBytes"                  -> rowReaders.contains("_read_bytes"),
      "usesReadDatetime"               -> rowReaders.contains("_read_datetime"),
      "usesReadEpochSeconds"           -> rowReaders.contains("_read_epoch_seconds"),
      "usesReadDecimal"                -> rowReaders.contains("_read_decimal"),
      "usesReadStrCol"                 -> rowReadersCol.contains("_read_str_col"),
      "usesReadIntCol"                 -> rowReadersCol.contains("_read_int_col"),
      "usesReadFloatCol"               -> rowReadersCol.contains("_read_float_col"),
      "usesReadBoolCol"                -> rowReadersCol.contains("_read_bool_col"),
      "usesReadBytesCol"               -> rowReadersCol.contains("_read_bytes_col"),
      "usesReadDatetimeCol"            -> rowReadersCol.contains("_read_datetime_col"),
      "usesReadEpochSecondsCol"        -> rowReadersCol.contains("_read_epoch_seconds_col"),
      "usesReadDecimalCol"             -> rowReadersCol.contains("_read_decimal_col"),
      "usesTimestampBindDatetime"      -> timestampBinds.contains("_timestamp_bind_datetime"),
      "usesTimestampBindEpochSeconds"  -> timestampBinds.contains("_timestamp_bind_epoch_seconds"),
      "usesJson"                       -> usesJson,
      "needsDecimalImport"             -> (
        needsDecimalImport(context.dialectKey, rowReaders, operations) ||
          rowReadersCol.contains("_read_decimal_col") ||
          rowReadersCol.contains("_read_epoch_seconds_col")
      ),
      "needsDatetimeImports"           -> (
        needsDatetimeImports(rowReaders, operations) ||
          rowReadersCol.contains("_read_datetime_col") ||
          rowReadersCol.contains("_read_epoch_seconds_col")
      ),
      "needsTimezoneImport"            -> (
        (context.dialectKey == "sqlite") ||
          rowReaders.contains("_read_epoch_seconds") ||
          rowReadersCol.contains("_read_epoch_seconds_col") ||
          timestampBinds.nonEmpty
      ),
      "needsRowReaderModuleImport"     -> SqlCodegenDialectConfig.rowReaderModuleImport(context.dialectKey).isDefined,
      "needsCastImport"                -> (rowReaders.nonEmpty || rowReadersCol.nonEmpty || usesJson),
      "needsUuidImport"                -> (
        SqlCodegenDialectConfig.usesUuidRowConversion(context.dialectKey) &&
          (rowReaders.contains("_read_str") || rowReadersCol.contains("_read_str_col"))
      ),
      "jsonStructures"                 -> withLastFlag(
        context.models
          .filter(model => usedJsonTypes.contains(model.name))
          .map(jsonStructureAttributes)
      ),
      "jsonUnions"                     -> withLastFlag(
        context.unions
          .filter(union => usedJsonTypes.contains(union.name))
          .map(jsonUnionAttributes)
      ),
      "jsonStructuresCol"              -> withLastFlag(
        context.models
          .filter(model => usedJsonTypesCol.contains(model.name))
          .map(jsonStructureAttributes)
      ),
      "jsonUnionsCol"                  -> withLastFlag(
        context.unions
          .filter(union => usedJsonTypesCol.contains(union.name))
          .map(jsonUnionAttributes)
      )
    )
  }

  private def jsonStructureAttributes(structure: SqlCodegenStructure): Map[String, Any] =
    Map(
      "name"      -> structure.name,
      "className" -> structure.name,
      "dictClose" -> " }",
      "i4"        -> "    ",
      "i8"        -> "        ",
      "members"   -> withLastFlag(
        structure.members.map { member =>
          Map(
            "name"           -> member.name,
            "pythonTypeName" -> member.pythonTypeName
          )
        }
      )
    )

  private def jsonUnionAttributes(union: SqlCodegenUnion): Map[String, Any] =
    Map(
      "name"              -> union.name,
      "className"         -> union.name,
      "discriminatorKeys" -> union.members.map(member => s"\"${member.name}\"").mkString(", "),
      "members"           -> withLastFlag(union.members.map(unionMemberAttributes))
    )

  private def unionMemberAttributes(member: SqlCodegenUnionMember): Map[String, Any] =
    Map(
      "name"             -> member.name,
      "variantClassName" -> member.variantClassName,
      "pythonTypeName"   -> member.pythonTypeName
    )

  private def withLastFlag(items: List[Map[String, Any]]): List[Map[String, Any]] =
    items.zipWithIndex.map { case (item, index) =>
      item + ("last" -> (index == items.length - 1))
    }

  private def needsDecimalImport(
      dialectKey: String,
      readers: Set[String],
      operations: List[SqlCodegenOperation]
  ): Boolean =
    readers.contains("_read_decimal") ||
      (dialectKey == "postgres" && (
        readers.contains("_read_epoch_seconds") ||
          collectTimestampBindHelpers(operations).contains("_timestamp_bind_epoch_seconds")
      ))

  private def needsDatetimeImports(
      readers: Set[String],
      operations: List[SqlCodegenOperation]
  ): Boolean =
    readers.exists(_.startsWith("_read_datetime")) ||
      readers.contains("_read_epoch_seconds") ||
      collectTimestampBindHelpers(operations).nonEmpty

  private def collectTimestampBindHelpers(operations: List[SqlCodegenOperation]): Set[String] =
    operations
      .flatMap(_.sql.toList)
      .flatMap(_.bindParameters)
      .flatMap { parameter =>
        if (parameter.pythonExpression.startsWith("_timestamp_bind_datetime(")) {
          Some("_timestamp_bind_datetime")
        } else if (parameter.pythonExpression.startsWith("_timestamp_bind_epoch_seconds(")) {
          Some("_timestamp_bind_epoch_seconds")
        } else {
          None
        }
      }
      .toSet

  private def usesJsonSerialization(operations: List[SqlCodegenOperation]): Boolean =
    operations.exists(_.sql.exists { sql =>
      sql.bindParameters.exists(_.isJson) || sql.resultFields.exists(_.isJson)
    })

  private def collectUsedJsonPythonTypeNames(operations: List[SqlCodegenOperation]): Set[String] =
    operations
      .flatMap(_.sql.toList)
      .flatMap { sql =>
        sql.bindParameters.flatMap(_.jsonPythonTypeName) ++
          sql.resultFields.filter(_.isJson).map(_.pythonTypeName)
      }
      .toSet

  def canUsePostgresClassRow(sql: SqlCodegenSqlBinding): Boolean =
    sql.queryKind == "selectOne" &&
      sql.resultFields.nonEmpty &&
      sql.resultFields.forall(field => !field.isJson)

  private def collectRowReaders(
      operations: List[SqlCodegenOperation],
      isPostgresDialect: Boolean
  ): Set[String] = {
    val scalarReaders =
      operations.flatMap(_.sql.toList).flatMap { sql =>
        if (sql.queryKind == "insert" && sql.outputKind == "scalar") {
          List("_read_str")
        } else {
          Nil
        }
      }
    val fieldReaders  =
      operations.flatMap(_.sql.toList).flatMap { sql =>
        if (isPostgresDialect && canUsePostgresClassRow(sql)) {
          Nil
        } else if (isPostgresDialect && usesPostgresDictRowFactory(sql)) {
          Nil
        } else {
          sql.resultFields.filterNot(_.isJson).map(_.rowReader)
        }
      }
    (scalarReaders ++ fieldReaders).toSet
  }

  private def collectRowReadersCol(operations: List[SqlCodegenOperation]): Set[String] =
    operations
      .flatMap(_.sql.toList)
      .filter(usesPostgresDictRowFactory)
      .flatMap(_.resultFields.filterNot(_.isJson).map(field => s"${field.rowReader}_col"))
      .toSet

  private def usesPostgresDictRowFactory(sql: SqlCodegenSqlBinding): Boolean = {
    val classRow = SqlCodegenHelperMetadata.canUsePostgresClassRow(sql)
    (sql.queryKind == "selectOne" && !classRow) ||
    (sql.queryKind == "insert" && sql.outputKind == "structure")
  }

  private def collectUsedJsonPythonTypeNamesCol(operations: List[SqlCodegenOperation]): Set[String] =
    operations
      .flatMap(_.sql.toList)
      .filter(usesPostgresDictRowFactory)
      .flatMap(_.resultFields.filter(_.isJson).map(_.pythonTypeName))
      .toSet
}
