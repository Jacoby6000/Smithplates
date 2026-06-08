package com.jacoby6000.smithy.stache.sql.codegen

import com.jacoby6000.smithy.stache.sql.*

object SqlCodegenHelperAttributes {
  def forService(context: SqlCodegenServiceContext): Map[String, Any] = {
    val operations               = context.operations
    val isPostgresDialect        = context.dialectKey == "postgres"
    val isSqliteDialect          = context.dialectKey == "sqlite"
    val rowReaders               = collectRowReaders(operations, context.dialectKey)
    val rowReadersCol            = collectRowReadersCol(operations)
    val timestampBinds           = collectTimestampBindHelpers(operations, context.dialectKey)
    val usesJson                 = usesJsonSerialization(operations)
    val usedJsonTypes            = collectUsedJsonTypeNames(operations)
    val usedJsonTypesCol         = collectUsedJsonTypeNamesCol(operations)
    val needsClassRow            =
      isPostgresDialect && operations.exists(op => op.sql.exists(SqlCodegenSqlBindingMetadata.canUseClassRow))
    val needsDictRow             =
      isPostgresDialect && operations.exists(op => op.sql.exists(SqlCodegenSqlBindingMetadata.usesDictRowFactory))
    val sqliteClassRowFactories  =
      if (isSqliteDialect) {
        collectSqliteClassRowFactories(operations)
      } else {
        Nil
      }
    val usesSqliteNamedRowMapper =
      isSqliteDialect && operations.exists(op => op.sql.exists(SqlCodegenSqlBindingMetadata.usesDictRowFactory))

    Map(
      "needsTransactionImports"        -> false,
      "needsClassRowImport"            -> needsClassRow,
      "needsDictRowImport"             -> needsDictRow,
      "needsPostgresRowFactoryImports" -> (needsClassRow || needsDictRow),
      "sqliteClassRowFactories"        -> sqliteClassRowFactories,
      "usesSqliteNamedRowMapper"       -> usesSqliteNamedRowMapper,
      "needsUuidTextLoader"            -> needsClassRow,
      "usesJson"                       -> usesJson,
      "needsDecimalImport"             -> (
        needsDecimalImport(context.dialectKey, rowReaders, timestampBinds) ||
          rowReadersCol.contains("_read_decimal_col") ||
          rowReadersCol.contains("_read_epoch_seconds_col")
      ),
      "needsDatetimeImports"           -> (
        needsDatetimeImports(rowReaders, timestampBinds) ||
          rowReadersCol.contains("_read_datetime_col") ||
          rowReadersCol.contains("_read_epoch_seconds_col")
      ),
      "needsTimezoneImport"            -> (
        (context.dialectKey == "sqlite") ||
          rowReaders.contains("_read_epoch_seconds") ||
          rowReadersCol.contains("_read_epoch_seconds_col") ||
          timestampBinds.nonEmpty
      ),
      "needsRowReaderModuleImport"     -> (context.dialectKey == "sqlite"),
      "needsCastImport"                -> (rowReaders.nonEmpty || rowReadersCol.nonEmpty || usesJson),
      "needsUuidImport"                -> (
        (context.dialectKey == "postgres") &&
          (rowReaders.contains("_read_str") || rowReadersCol.contains("_read_str_col"))
      ),
      "usesReadBool"                   -> rowReaders.contains("_read_bool"),
      "usesReadBytes"                  -> rowReaders.contains("_read_bytes"),
      "usesReadDatetime"               -> rowReaders.contains("_read_datetime"),
      "usesReadDecimal"                -> rowReaders.contains("_read_decimal"),
      "usesReadEpochSeconds"           -> rowReaders.contains("_read_epoch_seconds"),
      "usesReadFloat"                  -> rowReaders.contains("_read_float"),
      "usesReadInt"                    -> rowReaders.contains("_read_int"),
      "usesReadStr"                    -> rowReaders.contains("_read_str"),
      "usesTimestampBindDatetime"      -> timestampBinds.contains("_timestamp_bind_datetime"),
      "usesTimestampBindEpochSeconds"  -> timestampBinds.contains("_timestamp_bind_epoch_seconds"),
      "usesReadBoolCol"                -> rowReadersCol.contains("_read_bool_col"),
      "usesReadBytesCol"               -> rowReadersCol.contains("_read_bytes_col"),
      "usesReadDatetimeCol"            -> rowReadersCol.contains("_read_datetime_col"),
      "usesReadDecimalCol"             -> rowReadersCol.contains("_read_decimal_col"),
      "usesReadEpochSecondsCol"        -> rowReadersCol.contains("_read_epoch_seconds_col"),
      "usesReadFloatCol"               -> rowReadersCol.contains("_read_float_col"),
      "usesReadIntCol"                 -> rowReadersCol.contains("_read_int_col"),
      "usesReadStrCol"                 -> rowReadersCol.contains("_read_str_col"),
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
      "jsonUnionsColSqlite"            -> withLastFlag(
        if (isSqliteDialect) {
          context.unions
            .filter(union => usedJsonTypesCol.contains(union.name))
            .map(jsonUnionAttributes)
        } else {
          Nil
        }
      ),
      "jsonStructuresColSqlite"        -> withLastFlag(
        if (isSqliteDialect) {
          context.models
            .filter(model => usedJsonTypesCol.contains(model.name))
            .map(jsonStructureAttributes)
        } else {
          Nil
        }
      ),
      "jsonUnionsColPostgres"          -> withLastFlag(
        if (isPostgresDialect) {
          context.unions
            .filter(union => usedJsonTypesCol.contains(union.name))
            .map(jsonUnionAttributes)
        } else {
          Nil
        }
      ),
      "jsonStructuresColPostgres"      -> withLastFlag(
        if (isPostgresDialect) {
          context.models
            .filter(model => usedJsonTypesCol.contains(model.name))
            .map(jsonStructureAttributes)
        } else {
          Nil
        }
      )
    )
  }

  private def collectSqliteClassRowFactories(
      operations: List[SqlCodegenOperation]
  ): List[Map[String, Any]] =
    operations
      .flatMap { operation =>
        operation.sql.toList.filter(SqlCodegenSqlBindingMetadata.canUseClassRow).flatMap { sql =>
          operation.outputShapeId.map(_.getName).map { outputShapeName =>
            (
              outputShapeName,
              sql.resultFields.map(resultFieldAttributesForFactory)
            )
          }
        }
      }
      .groupBy(_._1)
      .values
      .map(_.head)
      .map { case (outputShapeName, resultFields) =>
        Map(
          "name"         -> outputShapeName,
          "resultFields" -> withLastFlag(resultFields)
        )
      }
      .toList

  private def resultFieldAttributesForFactory(resultField: SqlCodegenResultField): Map[String, Any] =
    Map(
      "fieldName"       -> resultField.fieldName,
      "columnIndex"     -> resultField.columnIndex,
      "typeName"        -> resultField.typeName,
      "isJson"          -> resultField.isJson,
      "timestampFormat" -> timestampFormatName(resultField.timestampFormat)
    )

  private def jsonStructureAttributes(structure: SqlStructure): Map[String, Any] =
    Map(
      "name"      -> structure.name,
      "dictClose" -> " }",
      "members"   -> withLastFlag(
        structure.members.map { member =>
          Map(
            "name"     -> member.name,
            "typeName" -> member.typeName
          )
        }
      )
    )

  private def jsonUnionAttributes(union: SqlUnion): Map[String, Any] =
    Map(
      "name"              -> union.name,
      "discriminatorKeys" -> union.members.map(member => s"\"${member.name}\"").mkString(", "),
      "members"           -> withLastFlag(union.members.map(unionMemberAttributes))
    )

  private def unionMemberAttributes(member: SqlUnionMember): Map[String, Any] =
    Map(
      "name"     -> member.name,
      "typeName" -> member.typeName
    )

  private def withLastFlag(items: List[Map[String, Any]]): List[Map[String, Any]] =
    items.zipWithIndex.map { case (item, index) =>
      item + ("last" -> (index == items.length - 1))
    }

  private def needsDecimalImport(
      dialectKey: String,
      readers: Set[String],
      timestampBinds: Set[String]
  ): Boolean =
    readers.contains("_read_decimal") ||
      (dialectKey == "postgres" && (
        readers.contains("_read_epoch_seconds") ||
          timestampBinds.contains("_timestamp_bind_epoch_seconds")
      ))

  private def needsDatetimeImports(
      readers: Set[String],
      timestampBinds: Set[String]
  ): Boolean =
    readers.exists(_.startsWith("_read_datetime")) ||
      readers.contains("_read_epoch_seconds") ||
      timestampBinds.nonEmpty

  private def collectTimestampBindHelpers(
      operations: List[SqlCodegenOperation],
      dialectKey: String
  ): Set[String] =
    operations
      .flatMap(_.sql.toList)
      .flatMap(_.bindParameters)
      .flatMap(parameter =>
        SqlCodegenNeutralTypeUsage.timestampBindHelper(parameter.typeName, parameter.timestampFormat, dialectKey))
      .toSet

  private def usesJsonSerialization(operations: List[SqlCodegenOperation]): Boolean =
    operations.exists(_.sql.exists { sql =>
      sql.bindParameters.exists(_.isJson) || sql.resultFields.exists(_.isJson)
    })

  private def collectUsedJsonTypeNames(operations: List[SqlCodegenOperation]): Set[String] =
    operations
      .flatMap(_.sql.toList)
      .flatMap { sql =>
        sql.bindParameters.flatMap(_.jsonTypeName) ++
          sql.resultFields.filter(_.isJson).map(_.typeName)
      }
      .toSet

  private def collectRowReaders(
      operations: List[SqlCodegenOperation],
      dialectKey: String
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
        if (SqlCodegenSqlBindingMetadata.usesDictRowFactory(sql)) {
          Nil
        } else if (SqlCodegenSqlBindingMetadata.canUseClassRow(sql) && dialectKey == "postgres") {
          Nil
        } else {
          sql.resultFields
            .filterNot(_.isJson)
            .flatMap(field => SqlCodegenNeutralTypeUsage.rowReaderForType(field.typeName, field.timestampFormat))
        }
      }
    (scalarReaders ++ fieldReaders).toSet
  }

  private def collectRowReadersCol(operations: List[SqlCodegenOperation]): Set[String] =
    operations
      .flatMap(_.sql.toList)
      .filter(SqlCodegenSqlBindingMetadata.usesDictRowFactory)
      .flatMap(_.resultFields.filterNot(_.isJson).flatMap { field =>
        SqlCodegenNeutralTypeUsage
          .rowReaderForType(field.typeName, field.timestampFormat)
          .map(reader => s"${reader}_col")
      })
      .toSet

  private def collectUsedJsonTypeNamesCol(operations: List[SqlCodegenOperation]): Set[String] =
    operations
      .flatMap(_.sql.toList)
      .filter(SqlCodegenSqlBindingMetadata.usesDictRowFactory)
      .flatMap(_.resultFields.filter(_.isJson).map(_.typeName))
      .toSet

  private def timestampFormatName(format: Option[SqlTimestampFormat]): Any =
    format.map {
      case SqlTimestampFormat.DateTime     => "DateTime"
      case SqlTimestampFormat.EpochSeconds => "EpochSeconds"
    }.orNull
}
