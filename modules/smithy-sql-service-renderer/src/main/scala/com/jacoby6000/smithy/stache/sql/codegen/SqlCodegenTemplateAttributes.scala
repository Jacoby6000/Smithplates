package com.jacoby6000.smithy.stache.sql.codegen

import com.jacoby6000.smithy.stache.sql.codegen.python.CodegenHelper
import com.jacoby6000.smithy.stache.sql.codegen.python.PythonCodegenHelperPlan
import com.jacoby6000.smithy.stache.sql.codegen.python.PythonCodegenHelperPlanner
import com.jacoby6000.smithy.stache.sql.codegen.python.SqlCodegenSqlBindingMetadata
import com.jacoby6000.smithy.stache.sql.query.SqlBindPlaceholder

object SqlCodegenTemplateAttributes {
  def forService(context: SqlCodegenServiceContext, templateRoot: String): Map[String, Any] = {
    val helperPlan     = PythonCodegenHelperPlanner.plan(context)
    val baseAttributes =
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
        "transactionTypeName"      -> SqlCodegenDialectConfig.transactionTypeName(context.dialectKey),
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
      ) ++ helperPlan.attributes

    val attributes = context.integrationTest match {
      case Some(integrationTest) =>
        baseAttributes ++ flattenIntegrationTestAttributes(integrationTestAttributes(integrationTest))
      case None                  =>
        baseAttributes
    }

    attributes ++ Map(
      "rowReaderHelpers" -> renderHelperPartials(templateRoot, attributes, helperPlan)
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

  private def renderHelperPartials(
      templateRoot: String,
      attributes: Map[String, Any],
      helperPlan: PythonCodegenHelperPlan
  ): String = {
    val sqliteFactoryPartials            = renderLoopedPartials(
      templateRoot,
      attributes,
      "sqliteClassRowFactories",
      "partials/row_factories/sqlite_class_row_factory"
    )
    val jsonUnionPartials                = renderLoopedPartials(
      templateRoot,
      attributes,
      "jsonUnions",
      "partials/json/union_helpers"
    )
    val jsonStructurePartials            = renderLoopedPartials(
      templateRoot,
      attributes,
      "jsonStructures",
      "partials/json/structure_helpers"
    )
    val jsonUnionColPostgresPartials     = renderLoopedPartials(
      templateRoot,
      attributes,
      "jsonUnionsColPostgres",
      "partials/json/union_helpers_col"
    )
    val jsonStructureColPostgresPartials = renderLoopedPartials(
      templateRoot,
      attributes,
      "jsonStructuresColPostgres",
      "partials/json/structure_helpers_col"
    )
    val jsonUnionColSqlitePartials       = renderLoopedPartials(
      templateRoot,
      attributes,
      "jsonUnionsColSqlite",
      "partials/json/union_helpers_col_sqlite"
    )
    val jsonStructureColSqlitePartials   = renderLoopedPartials(
      templateRoot,
      attributes,
      "jsonStructuresColSqlite",
      "partials/json/structure_helpers_col_sqlite"
    )

    val colReaderPartialPaths                   =
      Set(
        "partials/row_readers/read_bool_col",
        "partials/row_readers/read_bytes_col",
        "partials/row_readers/read_datetime_sqlite_col",
        "partials/row_readers/read_datetime_postgres_col",
        "partials/row_readers/read_decimal_col",
        "partials/row_readers/read_epoch_seconds_postgres_col",
        "partials/row_readers/read_float_col",
        "partials/row_readers/read_int_col",
        "partials/row_readers/read_str_sqlite_col",
        "partials/row_readers/read_str_postgres_col"
      )
    val renderPlanned                           = (helpers: List[CodegenHelper]) =>
      helpers.map { helper =>
        ScalateSspTemplateEngine
          .renderClasspathPartial(templateRoot, helper.partialPath, attributes)
          .stripTrailing()
      }
    val (colReaderHelpers, preColReaderHelpers) =
      helperPlan.helpers.partition(helper => colReaderPartialPaths.contains(helper.partialPath))

    (sqliteFactoryPartials ++
      renderPlanned(preColReaderHelpers) ++
      jsonUnionPartials ++
      jsonStructurePartials ++
      renderPlanned(colReaderHelpers) ++
      jsonUnionColPostgresPartials ++
      jsonStructureColPostgresPartials ++
      jsonUnionColSqlitePartials ++
      jsonStructureColSqlitePartials)
      .filter(_.nonEmpty)
      .mkString("\n\n")
      .stripTrailing()
  }

  private def renderLoopedPartials(
      templateRoot: String,
      attributes: Map[String, Any],
      attributeKey: String,
      partialPath: String
  ): List[String] =
    attributes
      .get(attributeKey)
      .map(_.asInstanceOf[List[Map[String, Any]]])
      .getOrElse(Nil)
      .map { itemAttributes =>
        ScalateSspTemplateEngine
          .renderClasspathPartial(templateRoot, partialPath, attributes ++ itemAttributes)
          .stripTrailing()
      }

  private def modelAttributes(templateRoot: String, model: SqlCodegenStructure): Map[String, Any] = {
    val members        = withLastFlag(model.members.map(memberAttributes))
    Map(
      "name"        -> model.name,
      "className"   -> model.name,
      "shapeId"     -> model.shapeId.toString,
      "namespace"   -> model.namespace,
      "members"     -> members,
      "memberLines" -> ScalateSspTemplateEngine.renderClasspathPartial(
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
      "languageTypeName" -> member.languageTypeName
    )

  private def memberAttributes(member: SqlCodegenMember): Map[String, Any] =
    Map(
      "name"               -> member.name,
      "typeName"           -> member.typeName,
      "languageTypeName"   -> member.languageTypeName,
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
        "i4"               -> "    ",
        "i8"               -> "        ",
        "i12"              -> "            ",
        "i16"              -> "                ",
        "name"             -> operation.name,
        "methodName"       -> operation.methodName,
        "operationShapeId" -> operation.shapeId.toString,
        "parameters"       -> withLastFlag(operation.parameters.map(parameterAttributes)),
        "hasParameters"    -> operation.parameters.nonEmpty,
        "hasOutput"        -> operation.outputClassName.isDefined,
        "outputClassName"  -> operation.outputClassName.orNull,
        "outputTypeName"   -> operation.outputClassName.getOrElse("Unit"),
        "errors"           -> withLastFlag(operation.errors.map(errorAttributes)),
        "hasErrors"        -> operation.errors.nonEmpty,
        "responseUnion"    -> operation.responseUnion,
        "responseType"     -> operation.responseType,
        "hasSql"           -> operation.sql.isDefined
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
          "classRowFactoryName"  -> "",
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
          "classRowFactoryName"  -> (
            if (canUseClassRow && dialectKey == "sqlite") {
              s"_${operation.outputClassName.getOrElse("")}_row_factory"
            } else {
              ""
            }
          ),
          "returningColumnIndex" -> sql.returningColumnIndex.map(_.asInstanceOf[Any]).orNull,
          "resultFields"         -> withLastFlag(sql.resultFields.map(resultFieldAttributes)),
          "hasResultFields"      -> sql.resultFields.nonEmpty,
          "txI8"                 -> "            ",
          "txI12"                -> "                ",
          "txI16"                -> "                    ",
          "txI20"                -> "                        "
        )
    }
  }

  private def parameterAttributes(parameter: SqlCodegenParameter): Map[String, Any] =
    Map(
      "name"               -> parameter.name,
      "typeName"           -> parameter.typeName,
      "languageTypeName"   -> parameter.languageTypeName,
      "optional"           -> parameter.optional,
      "required"           -> !parameter.optional,
      "isStructure"        -> parameter.isStructure,
      "structureClassName" -> parameter.structureShapeId.map(_.getName).orNull
    )

  private def bindParameterAttributes(bindParameter: SqlCodegenBindParameter): Map[String, Any] =
    Map(
      "memberName"     -> bindParameter.memberName,
      "bindExpression" -> bindParameter.bindExpression.orNull
    )

  private def resultFieldAttributes(resultField: SqlCodegenResultField): Map[String, Any] =
    Map(
      "fieldName"                     -> resultField.fieldName,
      "columnName"                    -> resultField.columnName,
      "columnNameLiteral"             -> s"\"${resultField.columnName}\"",
      "columnIndex"                   -> resultField.columnIndex,
      "languageTypeName"              -> resultField.languageTypeName,
      "isJson"                        -> resultField.isJson,
      "jsonReadExpression"            -> resultField.jsonReadExpression.orNull,
      "jsonReadExpressionCol"         -> jsonReadExpressionCol(resultField),
      "jsonReadExpressionNamedRowCol" -> jsonReadExpressionCol(resultField, "named_row"),
      "rowReader"                     -> resultField.rowReader.orNull,
      "rowReaderCol"                  -> resultField.rowReader.map(reader => s"${reader}_col").orNull
    )

  private def jsonReadExpressionCol(resultField: SqlCodegenResultField, rowVar: String = "row"): String =
    if (resultField.isJson) {
      s"_read_${resultField.languageTypeName}_col($rowVar, \"${resultField.columnName}\")"
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
  ): Map[String, Any]                        =
    Map(
      "schemaDdl"                        -> integrationTest.schemaDdl,
      "i4"                               -> "    ",
      "i8"                               -> "        ",
      "i12"                              -> "            ",
      "implementationModuleName"         -> integrationTest.implementationModuleName,
      "implementationClassName"          -> integrationTest.implementationClassName,
      "serviceFixtureName"               -> integrationTest.serviceFixtureName,
      "extraImports"                     -> integrationTest.extraImports.mkString("", "\n", "\n"),
      "insertOperation"                  -> integrationOperationAttributes(integrationTest.insertOperation),
      "selectOneOperation"               -> integrationOperationAttributes(integrationTest.selectOneOperation),
      "updateOperation"                  -> integrationTest.updateOperation.map(integrationOperationAttributes).orNull,
      "deleteOperation"                  -> integrationTest.deleteOperation.map(integrationOperationAttributes).orNull,
      "hasUpdateOperation"               -> integrationTest.updateOperation.isDefined,
      "hasDeleteOperation"               -> integrationTest.deleteOperation.isDefined,
      "transactionCommitInTxAssertions"  -> withLastFlag(
        integrationTest.transactionCommitInTxAssertions.map(assertion => Map("line" -> s"        $assertion"))
      ),
      "transactionCommitAfterAssertions" -> withLastFlag(
        integrationTest.transactionCommitAfterAssertions.map(assertion => Map("line" -> s"    $assertion"))
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
  ): Map[String, Any]                 =
    Map(
      "methodName"                 -> operation.methodName,
      "callArguments"              -> operation.callArguments,
      "outputClassName"            -> operation.outputClassName.orNull,
      "hasOutputClassName"         -> operation.outputClassName.isDefined,
      "resultAssertions"           -> withLastFlag(
        operation.resultAssertions.map(assertion => Map("line" -> s"    $assertion"))
      ),
      "updatedResultAssertions"    -> withLastFlag(
        operation.updatedResultAssertions.map(assertion => Map("line" -> s"    $assertion"))
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
