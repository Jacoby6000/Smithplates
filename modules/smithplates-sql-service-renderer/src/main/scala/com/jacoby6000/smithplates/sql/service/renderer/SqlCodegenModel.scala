package com.jacoby6000.smithplates.sql.service.renderer

import com.jacoby6000.smithplates.sql.*
import com.jacoby6000.smithplates.sql.model.*
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlBindPlaceholder
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlParameterizedStatement
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlQueryRenderer
import software.amazon.smithy.model.shapes.ShapeId

final case class SqlCodegenParameter(
    name: String,
    typeName: String,
    optional: Boolean,
    isStructure: Boolean,
    structureShapeId: Option[ShapeId]
)

final case class SqlCodegenErrorType(
    shapeId: ShapeId,
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
    namespace: String,
    version: String,
    dialectKey: String,
    packageName: String,
    queryRenderer: Option[SqlQueryRenderer],
    bindPlaceholderStyle: SqlBindPlaceholder,
    hasSqlOperations: Boolean,
    models: List[SqlStructure],
    unions: List[SqlUnion],
    operations: List[SqlCodegenOperation],
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
    insertOperation: SqlCodegenIntegrationTestOperation,
    selectOneOperation: SqlCodegenIntegrationTestOperation,
    updateOperation: Option[SqlCodegenIntegrationTestOperation],
    deleteOperation: Option[SqlCodegenIntegrationTestOperation],
    transactionCommitInTxAssertions: List[String],
    transactionCommitAfterAssertions: List[String],
    extraImports: List[String],
    testImports: String,
    localImportBlock: String
)

final case class SqlCodegenIntegrationTestOperation(
    name: String,
    callArguments: String,
    updatedCallArguments: Option[String],
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
