package com.jacoby6000.smithplates.sql.service.renderer

import com.jacoby6000.smithplates.codegen.core.Field
import com.jacoby6000.smithplates.codegen.core.Model
import com.jacoby6000.smithplates.codegen.core.ModelKind
import com.jacoby6000.smithplates.codegen.core.NeutralType
import com.jacoby6000.smithplates.codegen.core.OperationModel
import com.jacoby6000.smithplates.codegen.core.ServiceModel
import com.jacoby6000.smithplates.codegen.core.TypeResolver
import com.jacoby6000.smithplates.codegen.core.Variant
import com.jacoby6000.smithplates.codegen.core.planning.TemplateView
import com.jacoby6000.smithplates.codegen.core.strategy.RenderContext
import com.jacoby6000.smithplates.sql.SqlBindPlaceholder
import com.jacoby6000.smithplates.sql.model.SqlIntEnum
import com.jacoby6000.smithplates.sql.model.SqlStringEnum
import com.jacoby6000.smithplates.sql.model.SqlTimestampFormat
import com.jacoby6000.smithplates.sql.service.core.SqlIntegrationTestContext
import com.jacoby6000.smithplates.sql.service.core.SqlIntegrationTestOperation
import com.jacoby6000.smithplates.sql.service.core.SqlMeta
import com.jacoby6000.smithplates.sql.service.core.SqlMigrationContext
import com.jacoby6000.smithplates.sql.service.core.SqlMigrationEntry
import com.jacoby6000.smithplates.sql.service.core.SqlOperationBindParameter
import com.jacoby6000.smithplates.sql.service.core.SqlOperationMeta
import com.jacoby6000.smithplates.sql.service.core.SqlOperationParameter
import com.jacoby6000.smithplates.sql.service.core.SqlOperationResultField
import com.jacoby6000.smithplates.sql.service.core.SqlOperationSqlBinding
import com.jacoby6000.smithplates.sql.service.core.SqlSelectOneNestedBinding
import com.jacoby6000.smithplates.sql.service.core.SqlSelectOneOutputBinding
import com.jacoby6000.smithplates.sql.service.core.SqlServiceMeta

/** Neutral [[TemplateView]] helpers for SQL service-scoped SSP templates.
  *
  * This object is the single adapter between the neutral `TemplateView` (carrying
  * `ServiceModel[SqlServiceMeta, SqlOperationMeta]`) and the SQL SSP template layer. All template-facing accessors read
  * exclusively from the enriched `TemplateView` — there is no legacy `ServiceTemplateView` or `ServiceEnvelope`.
  */
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
    needsUuidImport: Boolean
)

object SqlNeutralServiceTemplateAttributes {
  type ServiceView = TemplateView[ServiceModel[SqlServiceMeta, SqlOperationMeta], SqlMeta]

  def enrichment(context: SqlCodegenServiceContext): (SqlServiceMeta, Map[String, SqlOperationMeta]) =
    internal.enrichFromContext(context)

  def serviceMeta(ctx: ServiceView): SqlServiceMeta =
    ctx.subject.meta.feature

  def operationMeta(ctx: ServiceView, operation: OperationModel[SqlOperationMeta]): SqlOperationMeta =
    operation.meta.feature

  def serviceName(ctx: ServiceView): String =
    serviceMeta(ctx).serviceName

  def serviceShapeId(ctx: ServiceView): String =
    serviceMeta(ctx).serviceShapeId

  def serviceModuleName(ctx: ServiceView): String =
    serviceMeta(ctx).moduleName

  def dialectKey(ctx: ServiceView): String =
    serviceMeta(ctx).dialectKey

  def packageName(ctx: ServiceView): String =
    serviceMeta(ctx).packageName

  def uuidTypeNames(ctx: ServiceView): Set[String] =
    serviceMeta(ctx).uuidTypeNames

  def enumTypeNames(ctx: ServiceView): Set[String] =
    serviceMeta(ctx).enumTypeNames

  def operations(ctx: ServiceView): List[OperationModel[SqlOperationMeta]] =
    ctx.subject.operations

  def hasOutput(operation: OperationModel[SqlOperationMeta]): Boolean =
    operation.meta.feature.hasOutput

  def outputShapeName(operation: OperationModel[SqlOperationMeta]): String =
    operation.meta.feature.outputShapeName

  def outputTypeName(operation: OperationModel[SqlOperationMeta]): String =
    operation.meta.feature.outputTypeName

  def isSelectOne(operation: OperationModel[SqlOperationMeta]): Boolean =
    operation.meta.feature.isSelectOne

  def hasSql(operation: OperationModel[SqlOperationMeta]): Boolean =
    operation.meta.feature.hasSql

  def sqlBodyKind(operation: OperationModel[SqlOperationMeta]): String =
    operation.meta.feature.sqlBodyKind

  def sqlStatement(operation: OperationModel[SqlOperationMeta]): String =
    operation.meta.feature.sqlStatement

  def sql(operation: OperationModel[SqlOperationMeta]): Option[SqlOperationSqlBinding] =
    operation.meta.feature.sql

  def parameters(operation: OperationModel[SqlOperationMeta]): List[SqlOperationParameter] =
    operation.meta.feature.parameters

  def bindParameters(operation: OperationModel[SqlOperationMeta]): List[SqlOperationBindParameter] =
    sql(operation).map(_.bindParameters).getOrElse(Nil)

  def resultFields(operation: OperationModel[SqlOperationMeta]): List[SqlOperationResultField] =
    sql(operation).map(_.resultFields).getOrElse(Nil)

  def returningColumnIndex(operation: OperationModel[SqlOperationMeta]): Option[Int] =
    sql(operation).flatMap(_.returningColumnIndex)

  def booleanResultFieldName(operation: OperationModel[SqlOperationMeta]): String =
    sql(operation).flatMap(_.booleanResultFieldName).getOrElse("")

  def selectOneNestedBindings(operation: OperationModel[SqlOperationMeta]): List[SqlSelectOneNestedBinding] =
    sql(operation).flatMap(_.selectOneOutput).map(_.nestedBindings).getOrElse(Nil)

  def errorNames(operation: OperationModel[SqlOperationMeta]): List[String] =
    operation.meta.feature.errors

  def integrationTest(ctx: ServiceView): Option[SqlIntegrationTestContext] =
    serviceMeta(ctx).integrationTest

  def migration(ctx: ServiceView): Option[SqlMigrationContext] =
    serviceMeta(ctx).migration

  def models(ctx: ServiceView): List[Model.Structure[SqlMeta]] =
    ctx.usedTypes
      .collect { case s: Model.Structure[SqlMeta] => s }
      .filter(_.id.namespace != SqlSelectOneDerivedOutputBuilder.DerivedNamespace)

  def operationResultModels(ctx: ServiceView): List[Model.Structure[SqlMeta]] =
    ctx.usedTypes
      .collect { case s: Model.Structure[SqlMeta] => s }
      .filter(_.id.namespace == SqlSelectOneDerivedOutputBuilder.DerivedNamespace)

  def unions(ctx: ServiceView): List[Model.Union[SqlMeta]] =
    ctx.usedTypes.collect { case u: Model.Union[SqlMeta] => u }

  def stringEnums(ctx: ServiceView): List[SqlStringEnum] =
    Nil

  def intEnums(ctx: ServiceView): List[SqlIntEnum] =
    Nil

  def usedJsonTypeNames(ctx: ServiceView): Set[String] =
    operations(ctx).flatMap { op =>
      bindParameters(op).filter(_.isJson).flatMap(_.jsonTypeName) ++
        resultFields(op).filter(_.isJson).map(_.typeName) ++
        selectOneNestedBindings(op).flatMap(_.fields.filter(_.isJson).map(_.typeName))
    }.toSet

  def usedJsonTypeNamesCol(ctx: ServiceView): Set[String] =
    operations(ctx)
      .filter(op => internal.usesDictRowFactory(sqlBodyKind(op)))
      .flatMap { op =>
        resultFields(op).filter(_.isJson).map(_.typeName) ++
          selectOneNestedBindings(op).flatMap(_.fields.filter(_.isJson).map(_.typeName))
      }
      .toSet

  def rowReaders(ctx: ServiceView): Set[String] = {
    val dialect       = dialectKey(ctx)
    val scalarReaders =
      operations(ctx)
        .filter(sqlBodyKind(_) == SqlCodegenSqlBodyKind.InsertScalar)
        .map(_ => "_read_str")
    val fieldReaders  =
      operations(ctx).flatMap { op =>
        sqlBodyKind(op) match {
          case SqlCodegenSqlBodyKind.InsertStructureDict | SqlCodegenSqlBodyKind.SelectOneDict =>
            Nil
          case SqlCodegenSqlBodyKind.SelectOneClassRow if dialect == "postgres"                =>
            Nil
          case SqlCodegenSqlBodyKind.InsertStructureIndex | SqlCodegenSqlBodyKind.SelectOneIndex |
              SqlCodegenSqlBodyKind.SelectOneClassRow | SqlCodegenSqlBodyKind.SelectOneJoinedFlat |
              SqlCodegenSqlBodyKind.SelectOneJoinedAggregate =>
            resultFields(op)
              .filterNot(_.isJson)
              .flatMap(field => internal.rowReaderForType(field.readTypeName, field.timestampFormat))
          case _                                                                               =>
            Nil
        }
      }
    (scalarReaders ++ fieldReaders).toSet
  }

  def rowReadersCol(ctx: ServiceView): Set[String] =
    operations(ctx)
      .filter(op => internal.usesDictRowFactory(sqlBodyKind(op)))
      .flatMap { op =>
        resultFields(op).filterNot(_.isJson).flatMap { field =>
          internal.rowReaderForType(field.readTypeName, field.timestampFormat).map(reader => s"${reader}_col")
        }
      }
      .toSet

  def timestampBinds(ctx: ServiceView): Set[String] = {
    val dialect = dialectKey(ctx)
    operations(ctx)
      .flatMap(bindParameters)
      .flatMap { bind =>
        SqlCodegenNeutralTypeUsage.timestampBindHelper(
          bind.typeName,
          bind.timestampFormat,
          dialect
        )
      }
      .toSet
  }

  def helperPartialPaths(ctx: ServiceView): List[String] = {
    val rowReadersSet     = rowReaders(ctx)
    val timestampBindsSet = timestampBinds(ctx)
    val dialect           = dialectKey(ctx)
    val namedRowMapper    =
      dialect == "sqlite" &&
        operations(ctx).exists(op => internal.usesDictRowFactory(sqlBodyKind(op)))
    val sqliteIncludes    =
      if (namedRowMapper) {
        List("fragments/row_mappers/sqlite_named_row")
      } else {
        Nil
      }
    val readerIncludes    =
      internal.rowReaderIncludeOrder.flatMap { reader =>
        if (rowReadersSet.contains(reader)) {
          internal.rowReaderPartialPath(reader, dialect)
        } else {
          None
        }
      }
    val bindIncludes      =
      internal.timestampBindIncludeOrder.flatMap { bindHelper =>
        if (timestampBindsSet.contains(bindHelper)) {
          internal.timestampBindPartialPath(bindHelper, dialect)
        } else {
          None
        }
      }
    sqliteIncludes ++ readerIncludes ++ bindIncludes
  }

  def helperColPartialPaths(ctx: ServiceView): List[String] = {
    val rowReadersColSet = rowReadersCol(ctx)
    val dialect          = dialectKey(ctx)
    internal.colReaderIncludeOrder.flatMap { reader =>
      if (rowReadersColSet.contains(reader)) {
        internal.colReaderPartialPath(reader, dialect)
      } else {
        None
      }
    }
  }

  def importRequirements(ctx: ServiceView): ImportRequirements = {
    val rowReadersSet     = rowReaders(ctx)
    val rowReadersColSet  = rowReadersCol(ctx)
    val timestampBindsSet = timestampBinds(ctx)
    val usesJson          = usedJsonTypeNames(ctx).nonEmpty
    val dialect           = dialectKey(ctx)
    val needsClassRow     =
      dialect == "postgres" &&
        operations(ctx).exists(sqlBodyKind(_) == SqlCodegenSqlBodyKind.SelectOneClassRow)
    val needsDictRow      =
      dialect == "postgres" &&
        operations(ctx).exists(op => internal.usesDictRowFactory(sqlBodyKind(op)))
    ImportRequirements(
      needsCastImport = rowReadersSet.nonEmpty || rowReadersColSet.nonEmpty || usesJson,
      needsJsonImport = usesJson,
      needsDecimalImport = internal.needsDecimalImport(dialect, rowReadersSet, timestampBindsSet) ||
        rowReadersColSet.contains("_read_decimal_col") ||
        rowReadersColSet.contains("_read_epoch_seconds_col"),
      needsDatetimeImports = internal.needsDatetimeImports(rowReadersSet, timestampBindsSet) ||
        rowReadersColSet.contains("_read_datetime_col") ||
        rowReadersColSet.contains("_read_epoch_seconds_col") ||
        jsonMappingUsesTimestamp(ctx),
      needsTimezoneImport = dialect == "sqlite" ||
        rowReadersSet.contains("_read_epoch_seconds") ||
        rowReadersColSet.contains("_read_epoch_seconds_col") ||
        timestampBindsSet.nonEmpty,
      needsSqlite3Import = dialect == "sqlite" && (rowReadersSet.nonEmpty || rowReadersColSet.nonEmpty || usesJson),
      needsClassRowImport = needsClassRow,
      needsDictRowImport = needsDictRow,
      needsUuidTextLoader = needsClassRow,
      needsUuidImport = dialect == "postgres" &&
        (rowReadersSet.contains("_read_str") || rowReadersColSet.contains("_read_str_col"))
    )
  }

  def unionsUsedAsJson(ctx: ServiceView): List[Model.Union[SqlMeta]] =
    unions(ctx).filter(union => usedJsonTypeNames(ctx).contains(union.id.name))

  def modelsUsedAsJson(ctx: ServiceView): List[Model.Structure[SqlMeta]] =
    models(ctx).filter(model => usedJsonTypeNames(ctx).contains(model.id.name))

  def unionsUsedAsJsonCol(ctx: ServiceView): List[Model.Union[SqlMeta]] =
    unions(ctx).filter(union => usedJsonTypeNamesCol(ctx).contains(union.id.name))

  def modelsUsedAsJsonCol(ctx: ServiceView): List[Model.Structure[SqlMeta]] =
    models(ctx).filter(model => usedJsonTypeNamesCol(ctx).contains(model.id.name))

  /** Transitive closure of structures reachable from `@sqlJson` columns: the structures directly used as JSON
    * columns, plus every structure transitively referenced by their fields or by JSON-union members. These
    * all need `_dump_` / `_map_to_` helpers because the union / structure dump helpers dispatch to leaf
    * structure helpers via `dumpValueExpression` / `mapValueExpression`.
    */
  def jsonStructureClosure(ctx: ServiceView): List[Model.Structure[SqlMeta]] = {
    val allModels         = models(ctx)
    val allModelNames     = allModels.map(_.id.name).toSet
    val allUnions         = unions(ctx)
    val allUnionNames     = allUnions.map(_.id.name).toSet
    val seedJsonStructures = modelsUsedAsJson(ctx) ++ modelsUsedAsJsonCol(ctx)
    val seedJsonUnions     = unionsUsedAsJson(ctx) ++ unionsUsedAsJsonCol(ctx)

    def referencedStructureNames(tpe: NeutralType): Set[String] = tpe match {
      case NeutralType.ModelRef(id) if allModelNames.contains(id.name) || allUnionNames.contains(id.name) => Set(id.name)
      case NeutralType.ListT(inner)                                       => referencedStructureNames(inner)
      case NeutralType.MapT(_, value)                                     => referencedStructureNames(value)
      case NeutralType.OptionalT(inner)                                   => referencedStructureNames(inner)
      case _                                                              => Set.empty
    }

    var visited = Set.empty[String]
    var frontier = (seedJsonStructures.map(_.id.name) ++ seedJsonUnions.map(_.id.name)).toSet
    while (frontier.nonEmpty) {
      visited ++= frontier
      val structFields = allModels.filter(m => frontier.contains(m.id.name)).flatMap(_.fields)
      val unionMembers = allUnions.filter(u => frontier.contains(u.id.name)).flatMap(_.members)
      val nextRefs     =
        (structFields.flatMap(f => referencedStructureNames(f.tpe)) ++
          unionMembers.flatMap(m => referencedStructureNames(m.tpe))).toSet -- visited
      frontier = nextRefs
    }
    allModels.filter(m => visited.contains(m.id.name)).sortBy(_.id.name)
  }

  def documentUsedAsJson(ctx: ServiceView): Boolean =
    usedJsonTypeNames(ctx).contains("Document")

  def documentUsedAsJsonCol(ctx: ServiceView): Boolean =
    usedJsonTypeNamesCol(ctx).contains("Document")

  def structureTypeNames(ctx: ServiceView): Set[String] =
    (models(ctx) ++ operationResultModels(ctx)).map(_.id.name).toSet

  def unionTypeNames(ctx: ServiceView): Set[String] =
    unions(ctx).map(_.id.name).toSet

  def jsonMappingUsesTimestamp(ctx: ServiceView): Boolean = {
    val closureModels = jsonStructureClosure(ctx)
    val jsonUnions = unionsUsedAsJson(ctx) ++ unionsUsedAsJsonCol(ctx)
    closureModels.exists(_.fields.exists(field => memberTypeName(ctx, field) == "datetime")) ||
    jsonUnions.exists(_.members.exists(member => internal.typeName(ctx, member.tpe) == "datetime"))
  }

  final case class ClassRowFactorySpec(
      name: String,
      resultFields: List[SqlOperationResultField]
  )

  def classRowFactories(ctx: ServiceView): List[ClassRowFactorySpec] =
    if (dialectKey(ctx) != "sqlite") {
      Nil
    } else {
      operations(ctx)
        .flatMap { op =>
          sql(op).toList
            .filter(s => SqlCodegenSqlBindingMetadata.canUseClassRow(s) && s.selectOneOutput.isEmpty)
            .flatMap { s =>
              if (outputShapeName(op).nonEmpty) {
                Some((outputShapeName(op), s.resultFields))
              } else {
                None
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
    }

  def unionDiscriminatorKeys(members: List[Variant]): String =
    members.map(member => s"\"${member.name}\"").mkString(", ")

  def renderType(ctx: ServiceView, tpe: NeutralType): String =
    ctx.typeRenderer.render(tpe, internal.renderContext(ctx))

  def renderType(ctx: ServiceView, tpe: Option[NeutralType]): String =
    tpe.map(t => renderType(ctx, t)).getOrElse("None")

  def memberTypeName(ctx: ServiceView, field: Field): String =
    internal.typeName(ctx, field.tpe)

  def memberOptional(field: Field): Boolean =
    field.tpe.isInstanceOf[NeutralType.OptionalT]

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def enrichFromContext(
        context: SqlCodegenServiceContext
    ): (SqlServiceMeta, Map[String, SqlOperationMeta]) = {
      val service = SqlServiceMeta(
        serviceName = context.name,
        serviceShapeId = context.shapeId.toString,
        moduleName = context.moduleName,
        namespace = context.namespace,
        packageName = context.packageName,
        dialectKey = context.dialectKey,
        bindPlaceholderStyle = context.bindPlaceholderStyle,
        hasSqlOperations = context.hasSqlOperations,
        uuidTypeNames = context.uuidTypeNames,
        enumTypeNames = (context.stringEnums.map(_.name) ++ context.intEnums.map(_.name)).toSet,
        integrationTest = context.integrationTest.map(convertIntegrationTest),
        migration = context.migration.map(convertMigration)
      )

      val ops = context.operations.map { op =>
        val sqlBinding = op.sql
        val bodyKind   = sqlBinding.flatMap(s => SqlCodegenSqlBodyKind.resolve(s)).getOrElse("")
        val formatted  = sqlBinding
          .map(s => SqlBindPlaceholder.format(s.sqlStatement.segments, context.bindPlaceholderStyle))
          .getOrElse("")

        op.name -> SqlOperationMeta(
          name = op.name,
          hasSql = op.sql.isDefined,
          parameters = op.parameters.map(p =>
            SqlOperationParameter(
              name = p.name,
              typeName = p.typeName,
              optional = p.optional,
              isStructure = p.isStructure,
              structureShapeId = p.structureShapeId.map(_.toString)
            )),
          hasOutput = op.outputTypeName.isDefined,
          outputShapeName = op.outputShapeId.map(_.getName).getOrElse(""),
          outputTypeName = op.outputTypeName.getOrElse(""),
          errors = op.errors.map(_.name),
          isSelectOne = op.isSelectOne,
          sqlBodyKind = bodyKind,
          sqlStatement = formatted,
          sql = sqlBinding.map(convertSqlBinding)
        )
      }.toMap

      (service, ops)
    }

    def convertSqlBinding(sql: SqlCodegenSqlBinding): SqlOperationSqlBinding =
      SqlOperationSqlBinding(
        queryKind = sql.queryKind,
        sqlStatement = sql.sqlStatement,
        tableName = sql.tableName,
        bindParameters = sql.bindParameters.map(convertBindParameter),
        executionMode = sql.executionMode,
        outputKind = sql.outputKind,
        returningColumnIndex = sql.returningColumnIndex,
        resultFields = sql.resultFields.map(convertResultField),
        booleanResultFieldName = sql.booleanResultFieldName,
        selectOneOutput = sql.selectOneOutput.map(convertSelectOneOutput)
      )

    def convertBindParameter(param: SqlCodegenBindParameter): SqlOperationBindParameter =
      SqlOperationBindParameter(
        memberName = param.memberName,
        typeName = param.typeName,
        optional = param.optional,
        isJson = param.isJson,
        jsonTypeName = param.jsonTypeName,
        timestampFormat = param.timestampFormat
      )

    def convertResultField(field: SqlCodegenResultField): SqlOperationResultField =
      SqlOperationResultField(
        fieldName = field.fieldName,
        columnName = field.columnName,
        columnIndex = field.columnIndex,
        typeName = field.typeName,
        readTypeName = field.readTypeName,
        optional = field.optional,
        isJson = field.isJson,
        timestampFormat = field.timestampFormat
      )

    def convertSelectOneOutput(output: SqlCodegenSelectOneOutputBinding): SqlSelectOneOutputBinding =
      SqlSelectOneOutputBinding(
        primaryFields = output.primaryFields.map(convertResultField),
        nestedBindings = output.nestedBindings.map(convertNestedBinding),
        hasCollectionJoin = output.hasCollectionJoin
      )

    def convertNestedBinding(nested: SqlCodegenSelectOneNestedBinding): SqlSelectOneNestedBinding =
      SqlSelectOneNestedBinding(
        memberName = nested.memberName,
        shapeName = nested.shapeName,
        cardinality = nested.cardinality,
        optional = nested.optional,
        fields = nested.fields.map(convertResultField)
      )

    def convertIntegrationTest(test: SqlCodegenIntegrationTestContext): SqlIntegrationTestContext =
      SqlIntegrationTestContext(
        schemaDdl = test.schemaDdl,
        setupSqlStatements = test.setupSqlStatements,
        insertOperation = convertTestOperation(test.insertOperation),
        selectOneOperation = convertTestOperation(test.selectOneOperation),
        updateOperation = test.updateOperation.map(convertTestOperation),
        deleteOperation = test.deleteOperation.map(convertTestOperation),
        transactionCommitInTxAssertions = test.transactionCommitInTxAssertions,
        transactionCommitAfterAssertions = test.transactionCommitAfterAssertions,
        extraImports = test.extraImports,
        testImports = test.testImports
      )

    def convertTestOperation(op: SqlCodegenIntegrationTestOperation): SqlIntegrationTestOperation =
      SqlIntegrationTestOperation(
        name = op.name,
        callArguments = op.callArguments,
        outputShapeName = op.outputShapeId.map(_.getName).getOrElse(""),
        outputValueAccessor = op.outputValueAccessor,
        resultAssertions = op.resultAssertions,
        updatedResultAssertions = op.updatedResultAssertions
      )

    def convertMigration(migration: SqlCodegenMigrationContext): SqlMigrationContext =
      SqlMigrationContext(
        migrationsDirectory = migration.migrationsDirectory,
        stateTableDdl = migration.stateTableDdl,
        stateTableName = SqlCodegenMigrationBuilder.StateTableName,
        migrations = migration.migrations.map(m =>
          SqlMigrationEntry(version = m.version, versionNumber = m.versionNumber, fileName = m.fileName))
      )

    def usesDictRowFactory(sqlBodyKind: String): Boolean =
      sqlBodyKind == SqlCodegenSqlBodyKind.InsertStructureDict ||
        sqlBodyKind == SqlCodegenSqlBodyKind.SelectOneDict

    def rowReaderForType(typeName: String, timestampFormat: Option[SqlTimestampFormat]): Option[String] =
      SqlCodegenNeutralTypeUsage.rowReaderForType(typeName, timestampFormat)

    def typeName(ctx: ServiceView, tpe: NeutralType): String =
      tpe match {
        case NeutralType.OptionalT(inner) => typeName(ctx, inner)
        case ref: NeutralType.ModelRef    =>
          ctx.typeRenderer.render(ref, renderContext(ctx))
        case other                        => renderType(ctx, other)
      }

    def renderContext(ctx: ServiceView): RenderContext[SqlMeta] =
      RenderContext(typeResolver = resolver(ctx), conventions = ctx.conventions)

    def resolver(ctx: ServiceView): TypeResolver[SqlMeta] =
      new TypeResolver[SqlMeta] {
        def resolve(ref: NeutralType.ModelRef): Option[Model[SqlMeta]] =
          ctx.usedTypes.find(_.id == ref.id)

        def classify(ref: NeutralType.ModelRef): Option[ModelKind] =
          ctx.usedTypes.find(_.id == ref.id).map(_.kind)

        def underlying(tpe: NeutralType): NeutralType =
          tpe match {
            case ref: NeutralType.ModelRef =>
              ctx.usedTypes.find(_.id == ref.id) match {
                case Some(alias: Model.Alias[SqlMeta]) => underlying(alias.underlying)
                case _                                 => tpe
              }
            case other                     => other
          }
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
