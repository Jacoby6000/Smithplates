package com.jacoby6000.smithy.stache.sql.codegen

import com.jacoby6000.smithy.stache.sql.PostgresDialect
import com.jacoby6000.smithy.stache.sql.shared.SqlBindPlaceholder

object SqlCodegenTemplateAttributes {
  def forService(context: SqlCodegenServiceContext): Map[String, Any] =
    Map(
      "serviceName" -> context.name,
      "serviceClassName" -> context.name,
      "serviceFileName" -> context.fileName,
      "serviceNamespace" -> context.namespace,
      "serviceShapeId" -> context.shapeId.toString,
      "serviceVersion" -> context.version,
      "implementationClassName" -> context.implementationClassName,
      "implementationModuleName" -> context.implementationModuleName,
      "hasSqlOperations" -> context.hasSqlOperations,
      "i4" -> "    ",
      "i8" -> "        ",
      "i12" -> "            ",
      "models" -> withLastFlag(context.models.map(modelAttributes)),
      "unions" -> withLastFlag(context.unions.map(unionAttributes)),
      "hasUnions" -> context.unions.nonEmpty,
      "operations" -> withLastFlag(context.operations.map(operationAttributes(context.bindPlaceholderStyle, _))),
      "rowReaderImports" -> renderRowReaderImports(context.dialect, context.operations),
      "rowReaderHelpers" -> renderRowReaderHelpers(context, context.operations),
      "hasIntegrationTest" -> context.integrationTest.isDefined,
      "integrationTest" -> context.integrationTest.map(integrationTestAttributes).orNull
    )

  def renderOutputPath(pattern: String, context: SqlCodegenServiceContext): String =
    pattern
      .replace("{{serviceName}}", context.name)
      .replace("{{serviceClassName}}", context.name)
      .replace("{{serviceFileName}}", context.fileName)
      .replace("{{serviceNamespace}}", context.namespace)
      .replace("{{serviceShapeId}}", context.shapeId.toString)
      .replace("{{serviceVersion}}", context.version)
      .replace("{{implementationClassName}}", context.implementationClassName)

  private def modelAttributes(model: SqlCodegenStructure): Map[String, Any] =
    Map(
      "name" -> model.name,
      "className" -> model.name,
      "shapeId" -> model.shapeId.toString,
      "namespace" -> model.namespace,
      "members" -> withLastFlag(model.members.map(memberAttributes)),
      "memberLines" -> renderPythonMemberLines(model.members),
      "hasMembers" -> model.members.nonEmpty
    )

  private def renderPythonMemberLines(members: List[SqlCodegenMember]): String =
    members
      .map { member =>
        val typeAnnotation =
          if (member.optional) {
            s"${member.pythonTypeName} | None"
          } else {
            member.pythonTypeName
          }
        s"    ${member.name}: $typeAnnotation"
      }
      .mkString("\n")

  private def unionAttributes(union: SqlCodegenUnion): Map[String, Any] =
    Map(
      "name" -> union.name,
      "className" -> union.name,
      "shapeId" -> union.shapeId.toString,
      "namespace" -> union.namespace,
      "members" -> withLastFlag(union.members.map(unionMemberAttributes)),
      "unionTypeAlias" -> union.members.map(_.variantClassName).mkString(" | ")
    )

  private def unionMemberAttributes(member: SqlCodegenUnionMember): Map[String, Any] =
    Map(
      "name" -> member.name,
      "variantClassName" -> member.variantClassName,
      "pythonTypeName" -> member.pythonTypeName
    )

  private def memberAttributes(member: SqlCodegenMember): Map[String, Any] =
    Map(
      "name" -> member.name,
      "typeName" -> member.typeName,
      "pythonTypeName" -> member.pythonTypeName,
      "optional" -> member.optional,
      "required" -> !member.optional,
      "isStructure" -> member.isStructure,
      "structureClassName" -> member.structureShapeId.map(_.getName).orNull
    )

  private def operationAttributes(
      bindPlaceholderStyle: SqlBindPlaceholder,
      operation: SqlCodegenOperation
  ): Map[String, Any] = {
    val base =
      Map(
        "name" -> operation.name,
        "methodName" -> operation.methodName,
        "operationShapeId" -> operation.shapeId.toString,
        "parameters" -> withLastFlag(operation.parameters.map(parameterAttributes)),
        "hasParameters" -> operation.parameters.nonEmpty,
        "hasOutput" -> operation.outputClassName.isDefined,
        "outputClassName" -> operation.outputClassName.orNull,
        "outputTypeName" -> operation.outputClassName.getOrElse("Unit"),
        "errors" -> withLastFlag(operation.errors.map(errorAttributes)),
        "hasErrors" -> operation.errors.nonEmpty,
        "responseUnion" -> operation.responseUnion,
        "pythonResponseType" -> operation.pythonResponseType,
        "hasSql" -> operation.sql.isDefined
      )

    operation.sql match {
      case None =>
        base ++ Map[String, Any](
          "queryKind" -> "",
          "sqlStatement" -> "",
          "tableName" -> "",
          "bindParameters" -> Nil,
          "hasBindParameters" -> false,
          "executionMode" -> "",
          "isRowcountExecution" -> false,
          "outputKind" -> "",
          "isInsertScalar" -> false,
          "isInsertStructure" -> false,
          "isBooleanMutation" -> false,
          "isSelectOne" -> false,
          "returningColumnIndex" -> null,
          "resultFields" -> Nil,
          "hasResultFields" -> false,
          "notFoundErrorClassName" -> null,
          "hasNotFoundError" -> false
        )
      case Some(sql) =>
        base ++ Map[String, Any](
          "queryKind" -> sql.queryKind,
          "sqlStatement" -> SqlBindPlaceholder.format(
            sql.sqlStatement.segments,
            bindPlaceholderStyle
          ),
          "tableName" -> sql.tableName,
          "bindParameters" -> withLastFlag(sql.bindParameters.map(bindParameterAttributes)),
          "bindTupleExpression" -> renderBindTupleExpression(sql.bindParameters),
          "hasBindParameters" -> sql.bindParameters.nonEmpty,
          "executionMode" -> sql.executionMode,
          "isRowcountExecution" -> (sql.executionMode == "rowcount"),
          "outputKind" -> sql.outputKind,
          "isInsertScalar" -> (sql.queryKind == "insert" && sql.outputKind == "scalar"),
          "isInsertStructure" -> (sql.queryKind == "insert" && sql.outputKind == "structure"),
          "isBooleanMutation" -> (sql.outputKind == "boolean"),
          "isSelectOne" -> (sql.queryKind == "selectOne"),
          "returningColumnIndex" -> sql.returningColumnIndex.map(_.asInstanceOf[Any]).orNull,
          "resultFields" -> withLastFlag(sql.resultFields.map(resultFieldAttributes)),
          "hasResultFields" -> sql.resultFields.nonEmpty,
          "notFoundErrorClassName" -> sql.notFoundErrorClassName.orNull,
          "hasNotFoundError" -> sql.notFoundErrorClassName.isDefined
        )
    }
  }

  private def parameterAttributes(parameter: SqlCodegenParameter): Map[String, Any] =
    Map(
      "name" -> parameter.name,
      "typeName" -> parameter.typeName,
      "pythonTypeName" -> parameter.pythonTypeName,
      "optional" -> parameter.optional,
      "required" -> !parameter.optional,
      "isStructure" -> parameter.isStructure,
      "structureClassName" -> parameter.structureShapeId.map(_.getName).orNull
    )

  private def bindParameterAttributes(bindParameter: SqlCodegenBindParameter): Map[String, Any] =
    Map(
      "memberName" -> bindParameter.memberName,
      "pythonExpression" -> bindParameter.pythonExpression
    )

  private def renderBindTupleExpression(bindParameters: List[SqlCodegenBindParameter]): String =
    bindParameters.map(_.pythonExpression) match {
      case Nil          => "()"
      case one :: Nil   => s"($one,)"
      case expressions  => expressions.mkString("(", ", ", ")")
    }

  private def resultFieldAttributes(resultField: SqlCodegenResultField): Map[String, Any] =
    Map(
      "fieldName" -> resultField.fieldName,
      "columnIndex" -> resultField.columnIndex,
      "pythonTypeName" -> resultField.pythonTypeName,
      "isJson" -> resultField.isJson,
      "jsonReadExpression" -> resultField.jsonReadExpression.orNull,
      "rowReader" -> rowReaderExpression(resultField.pythonTypeName)
    )

  private def rowReaderExpression(pythonTypeName: String): String =
    pythonTypeName match {
      case "str"      => "_read_str"
      case "int"      => "_read_int"
      case "float"    => "_read_float"
      case "bool"     => "_read_bool"
      case "bytes"    => "_read_bytes"
      case "datetime" => "_read_datetime"
      case "Decimal"  => "_read_decimal"
      case other      => other
    }

  private def renderRowReaderHelpers(
      context: SqlCodegenServiceContext,
      operations: List[SqlCodegenOperation]
  ): String = {
    val rowTypeName = SqlCodegenDialectConfig.rowTypeName(context.dialect)
    val readers = collectRowReaders(operations).toList.sorted
    val scalarHelpers = readers.map(readerName => renderRowReaderHelper(readerName, rowTypeName, context.dialect))
    val jsonTypeHelpers = renderJsonTypeHelpers(context, operations, rowTypeName, context.dialect)

    (scalarHelpers ++ jsonTypeHelpers).mkString("\n\n")
  }

  private def renderJsonTypeHelpers(
      context: SqlCodegenServiceContext,
      operations: List[SqlCodegenOperation],
      rowTypeName: String,
      dialect: com.jacoby6000.smithy.stache.sql.SqlDialect
  ): List[String] = {
    val usedTypeNames = collectUsedJsonPythonTypeNames(operations)
    val unionHelpers =
      context.unions
        .filter(union => usedTypeNames.contains(union.name))
        .map(renderUnionJsonHelpers(_, rowTypeName, dialect))
    val structureHelpers =
      context.models
        .filter(model => usedTypeNames.contains(model.name))
        .map(renderStructureJsonHelpers(_, rowTypeName, dialect))

    unionHelpers ++ structureHelpers
  }

  private def collectUsedJsonPythonTypeNames(operations: List[SqlCodegenOperation]): Set[String] =
    operations.flatMap(_.sql.toList).flatMap { sql =>
      sql.bindParameters.flatMap(_.jsonPythonTypeName) ++
        sql.resultFields.filter(_.isJson).map(_.pythonTypeName)
    }.toSet

  private def renderJsonRowData(dialect: com.jacoby6000.smithy.stache.sql.SqlDialect): String =
    dialect match {
      case PostgresDialect =>
        """value = row[index]
if isinstance(value, dict):
    data = cast(dict[str, object], value)
else:
    data = cast(dict[str, object], json.loads(cast(str, value)))"""
      case _ =>
        "data = cast(dict[str, object], json.loads(_read_str(row, index)))"
    }

  private def renderUnionJsonHelpers(
      union: SqlCodegenUnion,
      rowTypeName: String,
      dialect: com.jacoby6000.smithy.stache.sql.SqlDialect
  ): String = {
    val discriminatorKeys = union.members.map(member => s"\"${member.name}\"").mkString(", ")

    s"""def _json_bind_${union.name}(value: ${union.name}) -> str:
    present = [key for key in ($discriminatorKeys) if key in value]
    if len(present) != 1:
        raise ValueError("${union.name} union value must contain exactly one member key")
    return json.dumps(cast(object, value))


def _read_${union.name}(row: $rowTypeName, index: int) -> ${union.name}:
    ${renderJsonRowData(dialect).split("\n").mkString("\n    ")}
    present = [key for key in ($discriminatorKeys) if key in data]
    if len(present) != 1:
        raise ValueError(f"unknown ${union.name} discriminator: {sorted(data.keys())}")
    return cast(${union.name}, data)"""
  }

  private def renderStructureJsonHelpers(
      structure: SqlCodegenStructure,
      rowTypeName: String,
      dialect: com.jacoby6000.smithy.stache.sql.SqlDialect
  ): String = {
    val readFieldAssignments =
      structure.members
        .map { member =>
          s"${memberIndent}${memberIndent}${member.name}=cast(${member.pythonTypeName}, data[\"${member.name}\"]),"
        }
        .mkString("\n")
    val bindPayloadEntries =
      structure.members
        .map { member =>
          s"\"${member.name}\": cast(object, getattr(value, \"${member.name}\"))"
        }
        .mkString(", ")

    s"""def _json_bind_${structure.name}(value: ${structure.name}) -> str:
    payload = { $bindPayloadEntries }
    return json.dumps(payload)


def _read_${structure.name}(row: $rowTypeName, index: int) -> ${structure.name}:
    ${renderJsonRowData(dialect).split("\n").mkString("\n    ")}
    return ${structure.name}(
$readFieldAssignments
${memberIndent})"""
  }

  private val memberIndent = "    "

  private def renderRowReaderImports(dialect: com.jacoby6000.smithy.stache.sql.SqlDialect, operations: List[SqlCodegenOperation]): String = {
    val readers = collectRowReaders(operations)
    val usesJson = usesJsonSerialization(operations)
    if (readers.isEmpty && !usesJson) {
      ""
    } else {
      val imports =
        List(
          SqlCodegenDialectConfig.rowReaderModuleImport(dialect),
          if (readers.nonEmpty || usesJson) Some("from typing import cast") else None,
          if (SqlCodegenDialectConfig.usesUuidRowConversion(dialect)) Some("import uuid") else None,
          if (usesJson) Some("import json") else None,
          if (readers.contains("_read_datetime")) Some("from datetime import datetime") else None,
          if (readers.contains("_read_decimal")) Some("from decimal import Decimal") else None
        ).flatten

      imports.mkString("", "\n", "\n")
    }
  }

  private def usesJsonSerialization(operations: List[SqlCodegenOperation]): Boolean =
    operations.exists(_.sql.exists { sql =>
      sql.bindParameters.exists(_.isJson) || sql.resultFields.exists(_.isJson)
    })

  private def collectRowReaders(operations: List[SqlCodegenOperation]): Set[String] = {
    val scalarReaders =
      operations.flatMap(_.sql.toList).flatMap { sql =>
        if (sql.queryKind == "insert" && sql.outputKind == "scalar") {
          List("_read_str")
        } else {
          Nil
        }
      }
    val fieldReaders =
      operations.flatMap(_.sql.toList).flatMap { sql =>
        sql.resultFields.filterNot(_.isJson).map(field => rowReaderExpression(field.pythonTypeName))
      }
    (scalarReaders ++ fieldReaders).toSet
  }

  private def renderRowReaderHelper(
      readerName: String,
      rowTypeName: String,
      dialect: com.jacoby6000.smithy.stache.sql.SqlDialect
  ): String =
    readerName match {
      case "_read_str" if dialect == PostgresDialect =>
        s"""def _read_str(row: $rowTypeName, index: int) -> str:
    value = row[index]
    if isinstance(value, uuid.UUID):
        return str(value)
    return cast(str, value)"""
      case "_read_str" =>
        s"""def _read_str(row: $rowTypeName, index: int) -> str:
    return cast(str, row[index])"""
      case "_read_int" =>
        s"""def _read_int(row: $rowTypeName, index: int) -> int:
    return cast(int, row[index])"""
      case "_read_float" =>
        s"""def _read_float(row: $rowTypeName, index: int) -> float:
    return cast(float, row[index])"""
      case "_read_bool" =>
        s"""def _read_bool(row: $rowTypeName, index: int) -> bool:
    return cast(bool, row[index])"""
      case "_read_bytes" =>
        s"""def _read_bytes(row: $rowTypeName, index: int) -> bytes:
    return cast(bytes, row[index])"""
      case "_read_datetime" =>
        s"""def _read_datetime(row: $rowTypeName, index: int) -> datetime:
    return cast(datetime, row[index])"""
      case "_read_decimal" =>
        s"""def _read_decimal(row: $rowTypeName, index: int) -> Decimal:
    return cast(Decimal, row[index])"""
      case other =>
        throw new IllegalArgumentException(s"Unsupported row reader helper: $other")
    }

  private def errorAttributes(error: SqlCodegenErrorType): Map[String, Any] =
    Map(
      "name" -> error.name,
      "className" -> error.className,
      "shapeId" -> error.shapeId.toString
    )

  private def integrationTestAttributes(
      integrationTest: SqlCodegenIntegrationTestContext
    ): Map[String, Any] =
    Map(
      "schemaDdl" -> integrationTest.schemaDdl,
      "implementationModuleName" -> integrationTest.implementationModuleName,
      "serviceFixtureName" -> integrationTest.serviceFixtureName,
      "extraImports" -> integrationTest.extraImports.mkString("", "\n", "\n"),
      "insertOperation" -> integrationOperationAttributes(integrationTest.insertOperation),
      "selectOneOperation" -> integrationOperationAttributes(integrationTest.selectOneOperation),
      "updateOperation" -> integrationTest.updateOperation.map(integrationOperationAttributes).orNull,
      "deleteOperation" -> integrationTest.deleteOperation.map(integrationOperationAttributes).orNull,
      "hasUpdateOperation" -> integrationTest.updateOperation.isDefined,
      "hasDeleteOperation" -> integrationTest.deleteOperation.isDefined,
      "selectOneResultAssertions" -> integrationTest.selectOneResultAssertions,
      "updateLifecycleBlock" -> integrationTest.updateLifecycleBlock.orNull,
      "deleteLifecycleBlock" -> integrationTest.deleteLifecycleBlock.orNull,
      "hasUpdateLifecycleBlock" -> integrationTest.updateLifecycleBlock.isDefined,
      "hasDeleteLifecycleBlock" -> integrationTest.deleteLifecycleBlock.isDefined
    )

  private def integrationOperationAttributes(
      operation: SqlCodegenIntegrationTestOperation
    ): Map[String, Any] =
    Map(
      "methodName" -> operation.methodName,
      "callArguments" -> operation.callArguments,
      "outputClassName" -> operation.outputClassName.orNull,
      "notFoundErrorClassName" -> operation.notFoundErrorClassName.orNull,
      "hasNotFoundError" -> operation.notFoundErrorClassName.isDefined,
      "hasOutputClassName" -> operation.outputClassName.isDefined,
      "resultAssertions" -> withLastFlag(
        operation.resultAssertions.map(assertion => Map("line" -> assertion))
      ),
      "updatedResultAssertions" -> withLastFlag(
        operation.updatedResultAssertions.map(assertion => Map("line" -> assertion))
      ),
      "hasResultAssertions" -> operation.resultAssertions.nonEmpty,
      "hasUpdatedResultAssertions" -> operation.updatedResultAssertions.nonEmpty
    )

  private def withLastFlag(items: List[Map[String, Any]]): List[Map[String, Any]] =
    items.zipWithIndex.map { case (item, index) =>
      item + ("last" -> (index == items.length - 1))
    }
}
