package com.jacoby6000.smithplates.sql.service.renderer

import com.jacoby6000.smithplates.sql.*
import com.jacoby6000.smithplates.sql.model.*

final case class ImportRequirements(
    needsCastImport: Boolean,
    needsJsonImport: Boolean,
    needsDecimalImport: Boolean,
    needsDatetimeImports: Boolean,
    needsTimezoneImport: Boolean,
    needsSqlite3Import: Boolean,
    needsClassRowImport: Boolean,
    needsDictRowImport: Boolean,
    needsUuidTextLoader: Boolean,
    needsUuidImport: Boolean,
    needsAnyImport: Boolean
)

object SqlCodegenHelperAttributes {
  def usedJsonTypeNames(operations: List[TemplateOperationView]): Set[String] =
    operations.flatMap { operation =>
      operation.bindParameters.flatMap(bind =>
        Option.when(bind.isJson && bind.jsonTypeName.nonEmpty)(bind.jsonTypeName)) ++
        operation.resultFields.filter(_.isJson).map(_.typeName) ++
        internal.nestedJsonTypeNames(operation)
    }.toSet

  def usedJsonTypeNamesCol(operations: List[TemplateOperationView]): Set[String] =
    operations
      .filter(operation => internal.usesDictRowFactory(operation.sqlBodyKind))
      .flatMap(operation =>
        operation.resultFields.filter(_.isJson).map(_.typeName) ++
          internal.nestedJsonTypeNames(operation))
      .toSet

  def classRowFactories(operations: List[SqlCodegenOperation]): List[ClassRowFactorySpec] =
    operations
      .flatMap { operation =>
        operation.sql.toList
          .filter(sql => SqlCodegenSqlBindingMetadata.canUseClassRow(sql) && sql.selectOneOutput.isEmpty)
          .flatMap { sql =>
            operation.outputShapeId.map(_.getName).map { outputShapeName =>
              (outputShapeName, sql.resultFields)
            }
          }
      }
      .groupBy(_._1)
      .values
      .map(_.head)
      .map { case (outputShapeName, resultFields) =>
        ClassRowFactorySpec(outputShapeName, resultFields)
      }
      .toList

  def rowReaders(operations: List[TemplateOperationView], dialectKey: String): Set[String] = {
    val scalarReaders =
      operations
        .filter(_.sqlBodyKind == SqlCodegenSqlBodyKind.InsertScalar)
        .map(_ => "_read_str")
    val fieldReaders  =
      operations.flatMap { operation =>
        operation.sqlBodyKind match {
          case SqlCodegenSqlBodyKind.InsertStructureDict | SqlCodegenSqlBodyKind.SelectOneDict =>
            Nil
          case SqlCodegenSqlBodyKind.SelectOneClassRow if dialectKey == "postgres"             =>
            Nil
          case SqlCodegenSqlBodyKind.InsertStructureIndex | SqlCodegenSqlBodyKind.SelectOneIndex |
              SqlCodegenSqlBodyKind.SelectOneClassRow | SqlCodegenSqlBodyKind.SelectOneJoinedFlat |
              SqlCodegenSqlBodyKind.SelectOneJoinedAggregate =>
            operation.resultFields
              .filterNot(_.isJson)
              .flatMap(field => internal.rowReaderForType(field.readTypeName, field.timestampFormat))
          case _                                                                               =>
            Nil
        }
      }
    (scalarReaders ++ fieldReaders).toSet
  }

  def rowReadersCol(operations: List[TemplateOperationView]): Set[String] =
    operations
      .filter(operation => internal.usesDictRowFactory(operation.sqlBodyKind))
      .flatMap(_.resultFields.filterNot(_.isJson).flatMap { field =>
        internal.rowReaderForType(field.readTypeName, field.timestampFormat).map(reader => s"${reader}_col")
      })
      .toSet

  def timestampBinds(operations: List[TemplateOperationView], dialectKey: String): Set[String] =
    operations
      .flatMap(_.bindParameters)
      .flatMap(bind =>
        SqlCodegenNeutralTypeUsage.timestampBindHelper(
          bind.typeName,
          internal.parseTimestampFormat(bind.timestampFormat),
          dialectKey
        ))
      .toSet

  def helperPartialPaths(ctx: ServiceTemplateView): List[String] = {
    val rowReadersSet     = rowReaders(ctx.operations, ctx.dialectKey)
    val timestampBindsSet = timestampBinds(ctx.operations, ctx.dialectKey)
    val namedRowMapper    =
      ctx.dialectKey == "sqlite" &&
        ctx.operations.exists(operation => internal.usesDictRowFactory(operation.sqlBodyKind))
    val sqliteIncludes    =
      if (namedRowMapper) {
        List("fragments/row_mappers/sqlite_named_row")
      } else {
        Nil
      }
    val readerIncludes    =
      internal.rowReaderIncludeOrder.flatMap { reader =>
        if (rowReadersSet.contains(reader)) {
          internal.rowReaderPartialPath(reader, ctx.dialectKey)
        } else {
          None
        }
      }
    val bindIncludes      =
      internal.timestampBindIncludeOrder.flatMap { bindHelper =>
        if (timestampBindsSet.contains(bindHelper)) {
          internal.timestampBindPartialPath(bindHelper, ctx.dialectKey)
        } else {
          None
        }
      }
    sqliteIncludes ++ readerIncludes ++ bindIncludes
  }

  def helperColPartialPaths(ctx: ServiceTemplateView): List[String] = {
    val rowReadersColSet = rowReadersCol(ctx.operations)
    internal.colReaderIncludeOrder.flatMap { reader =>
      if (rowReadersColSet.contains(reader)) {
        internal.colReaderPartialPath(reader, ctx.dialectKey)
      } else {
        None
      }
    }
  }

  def importRequirements(ctx: ServiceTemplateView): ImportRequirements = {
    val rowReadersSet     = rowReaders(ctx.operations, ctx.dialectKey)
    val rowReadersColSet  = rowReadersCol(ctx.operations)
    val timestampBindsSet = timestampBinds(ctx.operations, ctx.dialectKey)
    val usesJson          = usedJsonTypeNames(ctx.operations).nonEmpty
    val needsClassRow     =
      ctx.dialectKey == "postgres" &&
        ctx.operations.exists(_.sqlBodyKind == SqlCodegenSqlBodyKind.SelectOneClassRow)
    val needsDictRow      =
      ctx.dialectKey == "postgres" &&
        ctx.operations.exists(operation => internal.usesDictRowFactory(operation.sqlBodyKind))
    ImportRequirements(
      needsCastImport = rowReadersSet.nonEmpty || rowReadersColSet.nonEmpty || usesJson,
      needsJsonImport = usesJson,
      needsDecimalImport = internal.needsDecimalImport(ctx.dialectKey, rowReadersSet, timestampBindsSet) ||
        rowReadersColSet.contains("_read_decimal_col") ||
        rowReadersColSet.contains("_read_epoch_seconds_col"),
      needsDatetimeImports = internal.needsDatetimeImports(rowReadersSet, timestampBindsSet) ||
        rowReadersColSet.contains("_read_datetime_col") ||
        rowReadersColSet.contains("_read_epoch_seconds_col"),
      needsTimezoneImport = ctx.dialectKey == "sqlite" ||
        rowReadersSet.contains("_read_epoch_seconds") ||
        rowReadersColSet.contains("_read_epoch_seconds_col") ||
        timestampBindsSet.nonEmpty,
      needsSqlite3Import =
        ctx.dialectKey == "sqlite" && (rowReadersSet.nonEmpty || rowReadersColSet.nonEmpty || usesJson),
      needsClassRowImport = needsClassRow,
      needsDictRowImport = needsDictRow,
      needsUuidTextLoader = needsClassRow,
      needsUuidImport = ctx.dialectKey == "postgres" &&
        (rowReadersSet.contains("_read_str") || rowReadersColSet.contains("_read_str_col")),
      needsAnyImport = internal.usesDocument(ctx)
    )
  }

  def usesDocument(ctx: ServiceTemplateView): Boolean =
    internal.usesDocument(ctx)

  def unionDiscriminatorKeys(union: TemplateUnionView): String =
    union.members.map(member => s"\"${member.name}\"").mkString(", ")

  def unionsUsedAsJson(ctx: ServiceTemplateView): List[TemplateUnionView] =
    ctx.unions.filter(union => ctx.usedJsonTypeNames.contains(union.name))

  def modelsUsedAsJson(ctx: ServiceTemplateView): List[TemplateModelView] =
    ctx.models.filter(model => ctx.usedJsonTypeNames.contains(model.name))

  def unionsUsedAsJsonCol(ctx: ServiceTemplateView): List[TemplateUnionView] =
    ctx.unions.filter(union => ctx.usedJsonTypeNamesCol.contains(union.name))

  def modelsUsedAsJsonCol(ctx: ServiceTemplateView): List[TemplateModelView] =
    ctx.models.filter(model => ctx.usedJsonTypeNamesCol.contains(model.name))

  def documentUsedAsJson(ctx: ServiceTemplateView): Boolean =
    ctx.usedJsonTypeNames.contains("Document")

  def documentUsedAsJsonCol(ctx: ServiceTemplateView): Boolean =
    ctx.usedJsonTypeNamesCol.contains("Document")

  final case class ClassRowFactorySpec(
      name: String,
      resultFields: List[SqlCodegenResultField]
  )

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {

    /** JSON columns on `@sqlDeriveSelectOne` join tables need their `_read_*` helpers emitted too. */
    def nestedJsonTypeNames(operation: TemplateOperationView): List[String] =
      operation.selectOneNestedBindings.flatMap(_.fields.filter(_.isJson).map(_.typeName))

    def usesDocument(ctx: ServiceTemplateView): Boolean =
      ctx.models.exists(model => model.members.exists(_.typeName == "Document")) ||
        ctx.operations.exists { operation =>
          operation.parameters.exists(_.typeName == "Document") ||
          operation.resultFields.exists(_.typeName == "Document") ||
          operation.bindParameters.exists(_.typeName == "Document")
        }

    def usesDictRowFactory(sqlBodyKind: String): Boolean =
      sqlBodyKind == SqlCodegenSqlBodyKind.InsertStructureDict ||
        sqlBodyKind == SqlCodegenSqlBodyKind.SelectOneDict

    def rowReaderForType(typeName: String, timestampFormat: String): Option[String] =
      SqlCodegenNeutralTypeUsage.rowReaderForType(
        typeName,
        parseTimestampFormat(timestampFormat)
      )

    def parseTimestampFormat(timestampFormat: String): Option[SqlTimestampFormat] =
      timestampFormat match {
        case "DateTime"     => Some(SqlTimestampFormat.DateTime)
        case "EpochSeconds" => Some(SqlTimestampFormat.EpochSeconds)
        case _              => None
      }

    val rowReaderIncludeOrder: List[String] =
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

    val timestampBindIncludeOrder: List[String] =
      List(
        "_timestamp_bind_datetime",
        "_timestamp_bind_epoch_seconds"
      )

    val colReaderIncludeOrder: List[String] =
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

    def rowReaderPartialPath(reader: String, dialectKey: String): Option[String] =
      reader match {
        case "_read_bool" | "_read_bytes" | "_read_decimal" | "_read_float" | "_read_int" =>
          Some(s"fragments/row_readers/${reader.stripPrefix("_")}")
        case "_read_datetime" | "_read_epoch_seconds" | "_read_str"                       =>
          Some(s"fragments/row_readers/${reader.stripPrefix("_")}_$dialectKey")
        case _                                                                            =>
          None
      }

    def colReaderPartialPath(reader: String, dialectKey: String): Option[String] =
      reader match {
        case "_read_bool_col" | "_read_bytes_col" | "_read_decimal_col" | "_read_float_col" | "_read_int_col" =>
          Some(s"fragments/row_readers/${reader.stripPrefix("_")}")
        case "_read_datetime_col" | "_read_str_col"                                                           =>
          Some(s"fragments/row_readers/${reader.stripPrefix("_")}_$dialectKey")
        case "_read_epoch_seconds_col"                                                                        =>
          Some("fragments/row_readers/read_epoch_seconds_postgres_col")
        case _                                                                                                =>
          None
      }

    def timestampBindPartialPath(bindHelper: String, dialectKey: String): Option[String] =
      bindHelper match {
        case "_timestamp_bind_datetime"      =>
          Some("fragments/timestamps/bind_datetime")
        case "_timestamp_bind_epoch_seconds" =>
          Some(s"fragments/timestamps/bind_epoch_seconds_$dialectKey")
        case _                               =>
          None
      }

    def needsDecimalImport(
        dialectKey: String,
        readers: Set[String],
        timestampBinds: Set[String]
    ): Boolean =
      readers.contains("_read_decimal") ||
        (dialectKey == "postgres" && (
          readers.contains("_read_epoch_seconds") ||
            timestampBinds.contains("_timestamp_bind_epoch_seconds")
        ))

    def needsDatetimeImports(
        readers: Set[String],
        timestampBinds: Set[String]
    ): Boolean =
      readers.exists(_.startsWith("_read_datetime")) ||
        readers.contains("_read_epoch_seconds") ||
        timestampBinds.nonEmpty
  }
}
