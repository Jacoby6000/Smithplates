package com.jacoby6000.smithplates.sql.service.renderer

import com.jacoby6000.smithplates.sql.*
import com.jacoby6000.smithplates.sql.model.*
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlBindPlaceholder

final case class TemplateAssertionLine(
    line: String,
    last: Boolean = false
)

final case class TemplateMemberView(
    name: String,
    typeName: String,
    optional: Boolean,
    last: Boolean = false
)

final case class TemplateModelView(
    name: String,
    hasMembers: Boolean,
    members: List[TemplateMemberView]
)

final case class TemplateUnionMemberView(
    name: String,
    typeName: String,
    last: Boolean = false
)

final case class TemplateUnionView(
    name: String,
    members: List[TemplateUnionMemberView]
)

final case class TemplateErrorView(
    name: String,
    last: Boolean = false
)

final case class TemplateParameterView(
    name: String,
    typeName: String,
    optional: Boolean,
    last: Boolean = false
)

final case class TemplateBindParameterView(
    memberName: String,
    typeName: String,
    optional: Boolean,
    isJson: Boolean,
    jsonTypeName: String,
    timestampFormat: String,
    dialectKey: String,
    last: Boolean = false
)

final case class TemplateResultFieldView(
    fieldName: String,
    columnNameLiteral: String,
    columnIndex: Int,
    typeName: String,
    readTypeName: String,
    optional: Boolean,
    isJson: Boolean,
    timestampFormat: String,
    isEnum: Boolean = false,
    last: Boolean = false
)

final case class TemplateSelectOneNestedBindingView(
    memberName: String,
    shapeName: String,
    isCollection: Boolean,
    optional: Boolean,
    fields: List[TemplateResultFieldView]
)

final case class TemplateOperationView(
    name: String,
    hasSql: Boolean,
    parameters: List[TemplateParameterView],
    hasOutput: Boolean,
    outputShapeName: String,
    outputTypeName: String,
    errors: List[TemplateErrorView],
    isSelectOne: Boolean,
    sqlBodyKind: String,
    sqlStatement: String,
    bindParameters: List[TemplateBindParameterView],
    returningColumnIndex: java.lang.Integer,
    resultFields: List[TemplateResultFieldView],
    booleanResultFieldName: String,
    selectOneNestedBindings: List[TemplateSelectOneNestedBindingView] = Nil,
    last: Boolean = false
)

final case class TemplateClassRowFactoryView(
    name: String,
    resultFields: List[TemplateResultFieldView],
    last: Boolean = false
)

final case class TemplateIntegrationTestOperationView(
    name: String,
    callArguments: String,
    outputShapeName: String,
    outputValueAccessor: String,
    hasResultAssertions: Boolean,
    resultAssertions: List[TemplateAssertionLine],
    hasUpdatedResultAssertions: Boolean,
    updatedResultAssertions: List[TemplateAssertionLine]
)

object TemplateIntegrationTestOperationView {
  val empty: TemplateIntegrationTestOperationView =
    TemplateIntegrationTestOperationView(
      name = "",
      callArguments = "",
      outputShapeName = "",
      outputValueAccessor = "",
      hasResultAssertions = false,
      resultAssertions = Nil,
      hasUpdatedResultAssertions = false,
      updatedResultAssertions = Nil
    )
}

final case class IntegrationTestView(
    schemaDdl: String,
    setupSqlStatements: List[String],
    extraImports: String,
    testImports: String,
    localImportBlock: String,
    insertOperation: TemplateIntegrationTestOperationView,
    selectOneOperation: TemplateIntegrationTestOperationView,
    updateOperation: TemplateIntegrationTestOperationView,
    deleteOperation: TemplateIntegrationTestOperationView,
    hasUpdateOperation: Boolean,
    hasDeleteOperation: Boolean,
    transactionCommitInTxAssertions: List[TemplateAssertionLine],
    transactionCommitAfterAssertions: List[TemplateAssertionLine]
)

final case class TemplateMigrationEntryView(
    version: String,
    versionNumber: Int,
    fileName: String,
    last: Boolean = false
)

final case class MigrationView(
    migrationsDirectory: String,
    stateTableDdl: String,
    stateTableName: String,
    migrations: List[TemplateMigrationEntryView]
)

final case class ServiceTemplateView(
    serviceShapeId: String,
    serviceName: String,
    dialectKey: String,
    packageName: String,
    models: List[TemplateModelView],
    operationResultModels: List[TemplateModelView],
    unions: List[TemplateUnionView],
    operations: List[TemplateOperationView],
    usedJsonTypeNames: Set[String],
    usedJsonTypeNamesCol: Set[String],
    classRowFactories: List[TemplateClassRowFactoryView],
    protocolTableModelImportBlock: String,
    enumImportBlock: String,
    serviceLocalImportBlock: String,
    integrationTest: Option[IntegrationTestView],
    migration: Option[MigrationView],
    uuidTypeNames: List[String]
)

object SqlCodegenTemplateViews {
  def buildServiceView(context: SqlCodegenServiceContext): ServiceTemplateView = {
    val enumTypeNames                        = SqlCodegenPythonImports.enumTypeNameSet(context)
    val operations                           = internal.withLastFlag(
      context.operations.map(internal.operationView(context.bindPlaceholderStyle, context.dialectKey, enumTypeNames, _))
    )((operation, last) => operation.copy(last = last))
    val (tableModels, operationResultModels) =
      context.models.partition(_.namespace != SqlSelectOneDerivedOutputBuilder.DerivedNamespace)
    ServiceTemplateView(
      serviceShapeId = context.shapeId.toString,
      serviceName = context.name,
      dialectKey = context.dialectKey,
      packageName = context.packageName,
      models = tableModels.map(internal.modelView),
      operationResultModels = operationResultModels.map(internal.modelView),
      unions = context.unions.map(internal.unionView),
      operations = operations,
      usedJsonTypeNames = SqlCodegenHelperAttributes.usedJsonTypeNames(operations),
      usedJsonTypeNamesCol = SqlCodegenHelperAttributes.usedJsonTypeNamesCol(operations),
      classRowFactories = if (context.dialectKey == "sqlite") {
        internal.withLastFlag(
          SqlCodegenHelperAttributes
            .classRowFactories(context.operations)
            .map(internal.classRowFactoryView(enumTypeNames, _))
        )((factory, last) => factory.copy(last = last))
      } else {
        Nil
      },
      protocolTableModelImportBlock = SqlCodegenPythonImports.protocolTableModelImportBlock(context),
      enumImportBlock = SqlCodegenPythonImports.enumImportBlock(context),
      serviceLocalImportBlock = SqlCodegenPythonImports.serviceLocalImportBlock(context),
      integrationTest = context.integrationTest.map(internal.integrationTestView),
      migration = context.migration.map(internal.migrationView),
      uuidTypeNames = context.uuidTypeNames.toList.sorted
    )
  }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def migrationView(migration: SqlCodegenMigrationContext): MigrationView =
      MigrationView(
        migrationsDirectory = migration.migrationsDirectory,
        stateTableDdl = migration.stateTableDdl,
        stateTableName = SqlCodegenMigrationBuilder.StateTableName,
        migrations =
          withLastFlag(migration.migrations.map(migrationEntryView))((entry, last) => entry.copy(last = last))
      )

    def migrationEntryView(entry: SqlCodegenMigrationEntry): TemplateMigrationEntryView =
      TemplateMigrationEntryView(
        version = entry.version,
        versionNumber = entry.versionNumber,
        fileName = entry.fileName
      )

    def integrationTestView(
        integrationTest: SqlCodegenIntegrationTestContext
    ): IntegrationTestView =
      IntegrationTestView(
        schemaDdl = integrationTest.schemaDdl,
        setupSqlStatements = integrationTest.setupSqlStatements,
        extraImports = if (integrationTest.extraImports.nonEmpty) {
          integrationTest.extraImports.mkString("", "\n", "\n")
        } else {
          ""
        },
        testImports = integrationTest.testImports,
        localImportBlock = integrationTest.localImportBlock,
        insertOperation = integrationOperationView(integrationTest.insertOperation),
        selectOneOperation = integrationOperationView(integrationTest.selectOneOperation),
        updateOperation = integrationTest.updateOperation
          .map(integrationOperationView)
          .getOrElse(TemplateIntegrationTestOperationView.empty),
        deleteOperation = integrationTest.deleteOperation
          .map(integrationOperationView)
          .getOrElse(TemplateIntegrationTestOperationView.empty),
        hasUpdateOperation = integrationTest.updateOperation.isDefined,
        hasDeleteOperation = integrationTest.deleteOperation.isDefined,
        transactionCommitInTxAssertions = withLastFlag(
          integrationTest.transactionCommitInTxAssertions.map(TemplateAssertionLine(_))
        )((assertion, last) => assertion.copy(last = last)),
        transactionCommitAfterAssertions = withLastFlag(
          integrationTest.transactionCommitAfterAssertions.map(TemplateAssertionLine(_))
        )((assertion, last) => assertion.copy(last = last))
      )

    def integrationOperationView(
        operation: SqlCodegenIntegrationTestOperation
    ): TemplateIntegrationTestOperationView =
      TemplateIntegrationTestOperationView(
        name = operation.name,
        callArguments = operation.callArguments,
        outputShapeName = operation.outputShapeId.map(_.getName).getOrElse(""),
        outputValueAccessor = operation.outputValueAccessor,
        hasResultAssertions = operation.resultAssertions.nonEmpty,
        resultAssertions = withLastFlag(operation.resultAssertions.map(TemplateAssertionLine(_)))((assertion, last) =>
          assertion.copy(last = last)),
        hasUpdatedResultAssertions = operation.updatedResultAssertions.nonEmpty,
        updatedResultAssertions = withLastFlag(operation.updatedResultAssertions.map(TemplateAssertionLine(_)))(
          (assertion, last) => assertion.copy(last = last))
      )

    def modelView(model: SqlStructure): TemplateModelView =
      TemplateModelView(
        name = model.name,
        hasMembers = model.members.nonEmpty,
        members = withLastFlag(model.members.map(memberView))((member, last) => member.copy(last = last))
      )

    def unionView(union: SqlUnion): TemplateUnionView =
      TemplateUnionView(
        name = union.name,
        members = withLastFlag(union.members.map(unionMemberView))((member, last) => member.copy(last = last))
      )

    def unionMemberView(member: SqlUnionMember): TemplateUnionMemberView =
      TemplateUnionMemberView(
        name = member.name,
        typeName = member.typeName
      )

    def memberView(member: SqlStructureMember): TemplateMemberView =
      TemplateMemberView(
        name = member.name,
        typeName = member.typeName,
        optional = member.optional
      )

    def operationView(
        bindPlaceholderStyle: SqlBindPlaceholder,
        dialectKey: String,
        enumTypeNames: Set[String],
        operation: SqlCodegenOperation
    ): TemplateOperationView = {
      val sqlFields                                                                                               = operation.sql match {
        case None      =>
          (
            "",
            "",
            List.empty[TemplateBindParameterView],
            null: java.lang.Integer,
            List.empty[TemplateResultFieldView],
            ""
          )
        case Some(sql) =>
          (
            SqlBindPlaceholder.format(sql.sqlStatement.segments, bindPlaceholderStyle),
            SqlCodegenSqlBodyKind.resolve(sql).getOrElse(""),
            withLastFlag(sql.bindParameters.map(bindParameterView(dialectKey, _)))((bindParameter, last) =>
              bindParameter.copy(last = last)),
            sql.returningColumnIndex.map(Integer.valueOf).orNull,
            withLastFlag(sql.resultFields.map(resultFieldView(enumTypeNames, _)))((resultField, last) =>
              resultField.copy(last = last)),
            sql.booleanResultFieldName.getOrElse("")
          )
      }
      val (sqlStatement, sqlBodyKind, bindParameters, returningColumnIndex, resultFields, booleanResultFieldName) =
        sqlFields
      val selectOneNestedBindings                                                                                 =
        operation.sql.flatMap(_.selectOneOutput).toList.flatMap { output =>
          output.nestedBindings.map { nested =>
            TemplateSelectOneNestedBindingView(
              memberName = nested.memberName,
              shapeName = nested.shapeName,
              isCollection =
                nested.cardinality == com.jacoby6000.smithplates.sql.service.SqlSelectOneNestedCardinality.Collection,
              optional = nested.optional,
              fields = withLastFlag(nested.fields.map(resultFieldView(enumTypeNames, _)))((resultField, last) =>
                resultField.copy(last = last))
            )
          }
        }
      TemplateOperationView(
        name = operation.name,
        hasSql = operation.sql.isDefined,
        parameters =
          withLastFlag(operation.parameters.map(parameterView))((parameter, last) => parameter.copy(last = last)),
        hasOutput = operation.outputTypeName.isDefined,
        outputShapeName = operation.outputShapeId.map(_.getName).getOrElse(""),
        outputTypeName = operation.outputTypeName.getOrElse(""),
        errors = withLastFlag(operation.errors.map(errorView))((error, last) => error.copy(last = last)),
        isSelectOne = operation.isSelectOne,
        sqlBodyKind = sqlBodyKind,
        sqlStatement = sqlStatement,
        bindParameters = bindParameters,
        returningColumnIndex = returningColumnIndex,
        resultFields = resultFields,
        booleanResultFieldName = booleanResultFieldName,
        selectOneNestedBindings = selectOneNestedBindings
      )
    }

    def parameterView(parameter: SqlCodegenParameter): TemplateParameterView =
      TemplateParameterView(
        name = parameter.name,
        typeName = parameter.typeName,
        optional = parameter.optional
      )

    def errorView(error: SqlCodegenErrorType): TemplateErrorView =
      TemplateErrorView(name = error.name)

    def bindParameterView(
        dialectKey: String,
        bindParameter: SqlCodegenBindParameter
    ): TemplateBindParameterView =
      TemplateBindParameterView(
        memberName = bindParameter.memberName,
        typeName = bindParameter.typeName,
        optional = bindParameter.optional,
        isJson = bindParameter.isJson,
        jsonTypeName = bindParameter.jsonTypeName.getOrElse(""),
        timestampFormat = timestampFormatName(bindParameter.timestampFormat),
        dialectKey = dialectKey
      )

    def resultFieldView(
        enumTypeNames: Set[String],
        resultField: SqlCodegenResultField
    ): TemplateResultFieldView =
      TemplateResultFieldView(
        fieldName = resultField.fieldName,
        columnNameLiteral = s"\"${resultField.columnName}\"",
        columnIndex = resultField.columnIndex,
        typeName = resultField.typeName,
        readTypeName = resultField.readTypeName,
        optional = resultField.optional,
        isJson = resultField.isJson,
        timestampFormat = timestampFormatName(resultField.timestampFormat),
        isEnum = enumTypeNames.contains(resultField.typeName)
      )

    def classRowFactoryView(
        enumTypeNames: Set[String],
        factory: SqlCodegenHelperAttributes.ClassRowFactorySpec
    ): TemplateClassRowFactoryView =
      TemplateClassRowFactoryView(
        name = factory.name,
        resultFields = withLastFlag(factory.resultFields.map(resultFieldView(enumTypeNames, _)))((resultField, last) =>
          resultField.copy(last = last))
      )

    def timestampFormatName(format: Option[SqlTimestampFormat]): String =
      format
        .map {
          case SqlTimestampFormat.DateTime     => "DateTime"
          case SqlTimestampFormat.EpochSeconds => "EpochSeconds"
        }
        .getOrElse("")

    def withLastFlag[T](items: List[T])(copyLast: (T, Boolean) => T): List[T] =
      items.zipWithIndex.map { case (item, index) =>
        copyLast(item, index == items.length - 1)
      }
  }
}
