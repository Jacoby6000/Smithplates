package com.jacoby6000.smithplates.sql.service.renderer

import com.jacoby6000.smithplates.sql.*
import com.jacoby6000.smithplates.sql.model.*
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlBindPlaceholder
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlParameterizedStatement
import software.amazon.smithy.model.shapes.ShapeId

final case class SqlCodegenParameter(
    name: String,
    typeName: String,
    optional: Boolean,
    isStructure: Boolean,
    structureShapeId: Option[ShapeId]
)

final case class SqlCodegenErrorType(
    name: String
)

final case class SqlCodegenOperation(
    shapeId: ShapeId,
    name: String,
    parameters: List[SqlCodegenParameter],
    outputShapeId: Option[ShapeId],
    outputTypeName: Option[String],
    errors: List[SqlCodegenErrorType],
    isSelectOne: Boolean = false,
    sql: Option[SqlCodegenSqlBinding] = None
)

final case class SqlCodegenBindParameter(
    memberName: String,
    typeName: String,
    optional: Boolean = false,
    isJson: Boolean = false,
    jsonTypeName: Option[String] = None,
    timestampFormat: Option[SqlTimestampFormat] = None
)

final case class SqlCodegenResultField(
    fieldName: String,
    columnName: String,
    columnIndex: Int,
    typeName: String,
    readTypeName: String,
    optional: Boolean = false,
    isJson: Boolean = false,
    timestampFormat: Option[SqlTimestampFormat] = None
)

final case class SqlCodegenSelectOneNestedBinding(
    memberName: String,
    shapeName: String,
    cardinality: com.jacoby6000.smithplates.sql.service.SqlSelectOneNestedCardinality,
    optional: Boolean,
    fields: List[SqlCodegenResultField]
)

final case class SqlCodegenSelectOneOutputBinding(
    primaryFields: List[SqlCodegenResultField],
    nestedBindings: List[SqlCodegenSelectOneNestedBinding],
    hasCollectionJoin: Boolean
)

final case class SqlCodegenSqlBinding(
    queryKind: String,
    sqlStatement: SqlParameterizedStatement,
    tableName: String,
    bindParameters: List[SqlCodegenBindParameter],
    executionMode: String,
    outputKind: String,
    returningColumnIndex: Option[Int],
    resultFields: List[SqlCodegenResultField],
    booleanResultFieldName: Option[String] = None,
    selectOneOutput: Option[SqlCodegenSelectOneOutputBinding] = None
)

final case class SqlCodegenServiceContext(
    shapeId: ShapeId,
    name: String,
    moduleName: String,
    namespace: String,
    version: String,
    dialectKey: String,
    packageName: String,
    bindPlaceholderStyle: SqlBindPlaceholder,
    hasSqlOperations: Boolean,
    models: List[SqlStructure],
    unions: List[SqlUnion],
    stringEnums: List[SqlStringEnum] = Nil,
    intEnums: List[SqlIntEnum] = Nil,
    operations: List[SqlCodegenOperation],
    uuidTypeNames: Set[String] = Set.empty,
    integrationTest: Option[SqlCodegenIntegrationTestContext] = None,
    migration: Option[SqlCodegenMigrationContext] = None
)

final case class SqlCodegenMigrationEntry(
    version: String,
    versionNumber: Int,
    fileName: String
)

final case class SqlCodegenMigrationContext(
    migrationsDirectory: String,
    stateTableDdl: String,
    migrations: List[SqlCodegenMigrationEntry]
)

final case class SqlCodegenIntegrationTestContext(
    schemaDdl: String,
    setupSqlStatements: List[String],
    insertOperation: SqlCodegenIntegrationTestOperation,
    selectOneOperation: SqlCodegenIntegrationTestOperation,
    updateOperation: Option[SqlCodegenIntegrationTestOperation],
    deleteOperation: Option[SqlCodegenIntegrationTestOperation],
    transactionCommitInTxAssertions: List[String],
    transactionCommitAfterAssertions: List[String],
    extraImports: List[String],
    testImports: String
)

final case class SqlCodegenIntegrationTestOperation(
    name: String,
    callArguments: String,
    outputShapeId: Option[ShapeId],
    outputValueAccessor: String,
    resultAssertions: List[String],
    updatedResultAssertions: List[String]
)

final case class SqlCodegenArtifact(
    relativePath: String,
    content: String,
    kind: SqlServiceCodegenArtifactKind
)
