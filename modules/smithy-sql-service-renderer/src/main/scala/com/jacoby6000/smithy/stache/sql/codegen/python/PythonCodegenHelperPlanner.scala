package com.jacoby6000.smithy.stache.sql.codegen.python

import com.jacoby6000.smithy.stache.sql.codegen.*

final case class CodegenHelper(partialPath: String)

final case class PythonCodegenHelperPlan(
    helpers: List[CodegenHelper],
    attributes: Map[String, Any]
)

object PythonCodegenHelperPlanner {
  def plan(context: SqlCodegenServiceContext): PythonCodegenHelperPlan = {
    val operations               = context.operations
    val isPostgresDialect        = context.dialectKey == "postgres"
    val isSqliteDialect          = context.dialectKey == "sqlite"
    val rowReaders               = collectRowReaders(operations, context.dialectKey)
    val rowReadersCol            = collectRowReadersCol(operations)
    val timestampBinds           = collectTimestampBindHelpers(operations)
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

    val attributes =
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

    val helpers = buildHelperPartials(
      isPostgresDialect = isPostgresDialect,
      isSqliteDialect = isSqliteDialect,
      rowReaders = rowReaders,
      rowReadersCol = rowReadersCol,
      timestampBinds = timestampBinds,
      usesSqliteNamedRowMapper = usesSqliteNamedRowMapper
    )

    PythonCodegenHelperPlan(helpers = helpers, attributes = attributes)
  }

  private def buildHelperPartials(
      isPostgresDialect: Boolean,
      isSqliteDialect: Boolean,
      rowReaders: Set[String],
      rowReadersCol: Set[String],
      timestampBinds: Set[String],
      usesSqliteNamedRowMapper: Boolean
  ): List[CodegenHelper] = {
    val dialectPartials =
      if (isSqliteDialect) {
        Option.when(usesSqliteNamedRowMapper)(CodegenHelper("partials/row_mappers/sqlite_named_row")).toList
      } else {
        Nil
      }

    val scalarReaderPartials =
      List(
        Option.when(rowReaders.contains("_read_bool"))(CodegenHelper("partials/row_readers/read_bool")),
        Option.when(rowReaders.contains("_read_bytes"))(CodegenHelper("partials/row_readers/read_bytes")),
        Option.when(rowReaders.contains("_read_datetime"))(
          if (isSqliteDialect) {
            CodegenHelper("partials/row_readers/read_datetime_sqlite")
          } else {
            CodegenHelper("partials/row_readers/read_datetime_postgres")
          }
        ),
        Option.when(rowReaders.contains("_read_decimal"))(CodegenHelper("partials/row_readers/read_decimal")),
        Option.when(rowReaders.contains("_read_epoch_seconds"))(
          if (isSqliteDialect) {
            CodegenHelper("partials/row_readers/read_epoch_seconds_sqlite")
          } else {
            CodegenHelper("partials/row_readers/read_epoch_seconds_postgres")
          }
        ),
        Option.when(rowReaders.contains("_read_float"))(CodegenHelper("partials/row_readers/read_float")),
        Option.when(rowReaders.contains("_read_int"))(CodegenHelper("partials/row_readers/read_int")),
        Option.when(rowReaders.contains("_read_str"))(
          if (isPostgresDialect) {
            CodegenHelper("partials/row_readers/read_str_postgres")
          } else {
            CodegenHelper("partials/row_readers/read_str_sqlite")
          }
        )
      ).flatten

    val timestampBindPartials =
      List(
        Option.when(timestampBinds.contains("_timestamp_bind_datetime"))(
          CodegenHelper("partials/timestamps/bind_datetime")
        ),
        Option.when(timestampBinds.contains("_timestamp_bind_epoch_seconds"))(
          if (isSqliteDialect) {
            CodegenHelper("partials/timestamps/bind_epoch_seconds_sqlite")
          } else {
            CodegenHelper("partials/timestamps/bind_epoch_seconds_postgres")
          }
        )
      ).flatten

    val colReaderPartials =
      List(
        Option.when(rowReadersCol.contains("_read_bool_col"))(CodegenHelper("partials/row_readers/read_bool_col")),
        Option.when(rowReadersCol.contains("_read_bytes_col"))(CodegenHelper("partials/row_readers/read_bytes_col")),
        Option.when(rowReadersCol.contains("_read_datetime_col"))(
          if (isSqliteDialect) {
            CodegenHelper("partials/row_readers/read_datetime_sqlite_col")
          } else {
            CodegenHelper("partials/row_readers/read_datetime_postgres_col")
          }
        ),
        Option.when(rowReadersCol.contains("_read_decimal_col"))(
          CodegenHelper("partials/row_readers/read_decimal_col")),
        Option.when(rowReadersCol.contains("_read_epoch_seconds_col"))(
          CodegenHelper("partials/row_readers/read_epoch_seconds_postgres_col")
        ),
        Option.when(rowReadersCol.contains("_read_float_col"))(CodegenHelper("partials/row_readers/read_float_col")),
        Option.when(rowReadersCol.contains("_read_int_col"))(CodegenHelper("partials/row_readers/read_int_col")),
        Option.when(rowReadersCol.contains("_read_str_col"))(
          if (isSqliteDialect) {
            CodegenHelper("partials/row_readers/read_str_sqlite_col")
          } else {
            CodegenHelper("partials/row_readers/read_str_postgres_col")
          }
        )
      ).flatten

    dialectPartials ++ scalarReaderPartials ++ timestampBindPartials ++ colReaderPartials
  }

  private def collectSqliteClassRowFactories(
      operations: List[SqlCodegenOperation]
  ): List[Map[String, Any]] =
    operations
      .flatMap { operation =>
        operation.sql.toList.filter(SqlCodegenSqlBindingMetadata.canUseClassRow).flatMap { sql =>
          operation.outputClassName.map { className =>
            (
              className,
              sql.resultFields.map(resultFieldAttributesForFactory)
            )
          }
        }
      }
      .groupBy(_._1)
      .values
      .map(_.head)
      .map { case (className, resultFields) =>
        Map(
          "className"    -> className,
          "factoryName"  -> s"_${className}_row_factory",
          "resultFields" -> withLastFlag(resultFields),
          "i4"           -> "    ",
          "i8"           -> "        "
        )
      }
      .toList

  private def resultFieldAttributesForFactory(resultField: SqlCodegenResultField): Map[String, Any] =
    Map(
      "fieldName"          -> resultField.fieldName,
      "columnIndex"        -> resultField.columnIndex,
      "languageTypeName"   -> resultField.languageTypeName,
      "isJson"             -> resultField.isJson,
      "jsonReadExpression" -> resultField.jsonReadExpression.orNull,
      "rowReader"          -> resultField.rowReader.orNull
    )

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
            "name"             -> member.name,
            "languageTypeName" -> member.languageTypeName
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
      "languageTypeName" -> member.languageTypeName
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
        parameter.bindExpression.flatMap { expression =>
          if (expression.startsWith("_timestamp_bind_datetime(")) {
            Some("_timestamp_bind_datetime")
          } else if (expression.startsWith("_timestamp_bind_epoch_seconds(")) {
            Some("_timestamp_bind_epoch_seconds")
          } else {
            None
          }
        }
      }
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
          sql.resultFields.filter(_.isJson).map(_.languageTypeName)
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
          sql.resultFields.filterNot(_.isJson).flatMap(_.rowReader)
        }
      }
    (scalarReaders ++ fieldReaders).toSet
  }

  private def collectRowReadersCol(operations: List[SqlCodegenOperation]): Set[String] =
    operations
      .flatMap(_.sql.toList)
      .filter(SqlCodegenSqlBindingMetadata.usesDictRowFactory)
      .flatMap(_.resultFields.filterNot(_.isJson).flatMap(field => field.rowReader.map(reader => s"${reader}_col")))
      .toSet

  private def collectUsedJsonTypeNamesCol(operations: List[SqlCodegenOperation]): Set[String] =
    operations
      .flatMap(_.sql.toList)
      .filter(SqlCodegenSqlBindingMetadata.usesDictRowFactory)
      .flatMap(_.resultFields.filter(_.isJson).map(_.languageTypeName))
      .toSet
}

object SqlCodegenSqlBindingMetadata {
  def canUseClassRow(sql: SqlCodegenSqlBinding): Boolean =
    sql.queryKind == "selectOne" &&
      sql.resultFields.nonEmpty &&
      sql.resultFields.forall(field => !field.isJson)

  def usesDictRowFactory(sql: SqlCodegenSqlBinding): Boolean = {
    val classRow = canUseClassRow(sql)
    (sql.queryKind == "selectOne" && !classRow) ||
    (sql.queryKind == "insert" && sql.outputKind == "structure")
  }
}
