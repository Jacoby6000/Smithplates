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
    val helperIncludes           =
      collectHelperIncludes(rowReaders, timestampBinds, usesSqliteNamedRowMapper)
    val helperColIncludes        = collectHelperColIncludes(rowReadersCol)

    Map(
      "needsTransactionImports"        -> false,
      "needsClassRowImport"            -> needsClassRow,
      "needsDictRowImport"             -> needsDictRow,
      "needsPostgresRowFactoryImports" -> (needsClassRow || needsDictRow),
      "sqliteClassRowFactories"        -> sqliteClassRowFactories,
      "usesSqliteNamedRowMapper"       -> usesSqliteNamedRowMapper,
      "helperIncludes"                 -> helperIncludes,
      "helperColIncludes"              -> helperColIncludes,
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

  private def collectHelperIncludes(
      rowReaders: Set[String],
      timestampBinds: Set[String],
      usesSqliteNamedRowMapper: Boolean
  ): List[String] = {
    val sqliteIncludes =
      if (usesSqliteNamedRowMapper) {
        List("partials/row_mappers/sqlite_named_row")
      } else {
        Nil
      }
    val readerIncludes =
      rowReaderIncludeOrder.flatMap { reader =>
        if (rowReaders.contains(reader)) {
          rowReaderPartialPath(reader)
        } else {
          None
        }
      }
    val bindIncludes   =
      timestampBindIncludeOrder.flatMap { bindHelper =>
        if (timestampBinds.contains(bindHelper)) {
          timestampBindPartialPath(bindHelper)
        } else {
          None
        }
      }
    sqliteIncludes ++ readerIncludes ++ bindIncludes
  }

  private def collectHelperColIncludes(rowReadersCol: Set[String]): List[String] =
    colReaderIncludeOrder.flatMap { reader =>
      if (rowReadersCol.contains(reader)) {
        colReaderPartialPath(reader)
      } else {
        None
      }
    }

  private val rowReaderIncludeOrder: List[String] =
    List(
      "_read_bool",
      "_read_bytes",
      "_read_datetime",
      "_read_decimal",
      "_read_epoch_seconds",
      "_read_float",
      "_read_int",
      "_read_str"
    )

  private val timestampBindIncludeOrder: List[String] =
    List(
      "_timestamp_bind_datetime",
      "_timestamp_bind_epoch_seconds"
    )

  private val colReaderIncludeOrder: List[String] =
    List(
      "_read_bool_col",
      "_read_bytes_col",
      "_read_datetime_col",
      "_read_decimal_col",
      "_read_epoch_seconds_col",
      "_read_float_col",
      "_read_int_col",
      "_read_str_col"
    )

  private def rowReaderPartialPath(reader: String): Option[String] =
    reader match {
      case "_read_bool" | "_read_bytes" | "_read_decimal" | "_read_float" | "_read_int" =>
        Some(s"partials/row_readers/${reader.stripPrefix("_")}")
      case "_read_datetime" | "_read_epoch_seconds" | "_read_str"                       =>
        Some(s"partials/row_readers/${reader.stripPrefix("_")}")
      case _                                                                            =>
        None
    }

  private def colReaderPartialPath(reader: String): Option[String] =
    reader match {
      case "_read_bool_col" | "_read_bytes_col" | "_read_decimal_col" | "_read_float_col" | "_read_int_col" =>
        Some(s"partials/row_readers/${reader.stripPrefix("_")}")
      case "_read_datetime_col" | "_read_str_col"                                                           =>
        Some(s"partials/row_readers/${reader.stripPrefix("_")}")
      case "_read_epoch_seconds_col"                                                                        =>
        Some("partials/row_readers/read_epoch_seconds_postgres_col")
      case _                                                                                                =>
        None
    }

  private def timestampBindPartialPath(bindHelper: String): Option[String] =
    bindHelper match {
      case "_timestamp_bind_datetime"      =>
        Some("partials/timestamps/bind_datetime")
      case "_timestamp_bind_epoch_seconds" =>
        Some("partials/timestamps/bind_epoch_seconds")
      case _                               =>
        None
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
