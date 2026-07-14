package com.jacoby6000.smithplates.sql.service.core

import com.jacoby6000.smithplates.sql.SqlBindPlaceholder
import com.jacoby6000.smithplates.sql.SqlParameterizedStatement
import com.jacoby6000.smithplates.sql.model.*
import com.jacoby6000.smithplates.sql.service.SqlSelectOneNestedCardinality

/** Model-level SQL feature metadata carried on [[com.jacoby6000.smithplates.codegen.core.ModelMeta]]. */
sealed trait SqlMeta

object SqlMeta {
  final case class SqlTableMeta(tableName: String) extends SqlMeta

  case object SqlNestedField extends SqlMeta
}

/** Per-member column metadata for SQL table structures. */
final case class SqlColumnMeta(
    columnName: String,
    columnType: Option[SqlColumnType],
    nullable: Boolean,
    autoGeneration: Option[SqlAutoGeneration]
)

/** Service-level SQL feature metadata. */
final case class SqlServiceMeta(
    serviceName: String,
    serviceShapeId: String,
    moduleName: String,
    namespace: String,
    packageName: String,
    dialectKey: String,
    bindPlaceholderStyle: SqlBindPlaceholder,
    hasSqlOperations: Boolean,
    uuidTypeNames: Set[String] = Set.empty,
    enumTypeNames: Set[String] = Set.empty,
    integrationTest: Option[SqlIntegrationTestContext] = None,
    migration: Option[SqlMigrationContext] = None
)

/** Operation-level SQL feature metadata. */
final case class SqlOperationMeta(
    name: String,
    hasSql: Boolean,
    parameters: List[SqlOperationParameter],
    hasOutput: Boolean,
    outputShapeName: String,
    outputTypeName: String,
    errors: List[String],
    isSelectOne: Boolean = false,
    sqlBodyKind: String = "",
    sqlStatement: String = "",
    sql: Option[SqlOperationSqlBinding] = None
)

final case class SqlOperationParameter(
    name: String,
    typeName: String,
    optional: Boolean,
    isStructure: Boolean,
    structureShapeId: Option[String]
)

final case class SqlOperationBindParameter(
    memberName: String,
    typeName: String,
    optional: Boolean = false,
    isJson: Boolean = false,
    jsonTypeName: Option[String] = None,
    timestampFormat: Option[SqlTimestampFormat] = None
)

final case class SqlOperationResultField(
    fieldName: String,
    columnName: String,
    columnIndex: Int,
    typeName: String,
    readTypeName: String,
    optional: Boolean = false,
    isJson: Boolean = false,
    timestampFormat: Option[SqlTimestampFormat] = None
)

final case class SqlSelectOneNestedBinding(
    memberName: String,
    shapeName: String,
    cardinality: SqlSelectOneNestedCardinality,
    optional: Boolean,
    fields: List[SqlOperationResultField]
)

final case class SqlSelectOneOutputBinding(
    primaryFields: List[SqlOperationResultField],
    nestedBindings: List[SqlSelectOneNestedBinding],
    hasCollectionJoin: Boolean
)

final case class SqlOperationSqlBinding(
    queryKind: String,
    sqlStatement: SqlParameterizedStatement,
    tableName: String,
    bindParameters: List[SqlOperationBindParameter],
    executionMode: String,
    outputKind: String,
    returningColumnIndex: Option[Int],
    resultFields: List[SqlOperationResultField],
    booleanResultFieldName: Option[String] = None,
    selectOneOutput: Option[SqlSelectOneOutputBinding] = None
)

final case class SqlMigrationEntry(
    version: String,
    versionNumber: Int,
    fileName: String
)

final case class SqlMigrationContext(
    migrationsDirectory: String,
    stateTableDdl: String,
    stateTableName: String,
    migrations: List[SqlMigrationEntry]
)

final case class SqlIntegrationTestOperation(
    name: String,
    callArguments: String,
    outputShapeName: String,
    outputValueAccessor: String,
    resultAssertions: List[String],
    updatedResultAssertions: List[String]
)

final case class SqlIntegrationTestContext(
    schemaDdl: String,
    setupSqlStatements: List[String],
    insertOperation: SqlIntegrationTestOperation,
    selectOneOperation: SqlIntegrationTestOperation,
    updateOperation: Option[SqlIntegrationTestOperation],
    deleteOperation: Option[SqlIntegrationTestOperation],
    transactionCommitInTxAssertions: List[String],
    transactionCommitAfterAssertions: List[String],
    extraImports: List[String],
    testImports: String
)
