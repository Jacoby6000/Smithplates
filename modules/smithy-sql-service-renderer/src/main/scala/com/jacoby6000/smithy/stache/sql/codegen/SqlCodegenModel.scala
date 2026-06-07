package com.jacoby6000.smithy.stache.sql.codegen

import com.jacoby6000.smithy.stache.sql.SqlTimestampFormat
import com.jacoby6000.smithy.stache.sql.query.SqlBindPlaceholder
import com.jacoby6000.smithy.stache.sql.query.SqlParameterizedStatement
import com.jacoby6000.smithy.stache.sql.query.SqlQueryRenderer
import software.amazon.smithy.model.shapes.ShapeId

final case class SqlCodegenStructure(
    shapeId: ShapeId,
    name: String,
    namespace: String,
    members: List[SqlCodegenMember]
)

final case class SqlCodegenMember(
    name: String,
    typeName: String,
    languageTypeName: String,
    optional: Boolean,
    isStructure: Boolean,
    structureShapeId: Option[ShapeId],
    isUnion: Boolean = false
)

final case class SqlCodegenParameter(
    name: String,
    typeName: String,
    languageTypeName: String,
    optional: Boolean,
    isStructure: Boolean,
    structureShapeId: Option[ShapeId]
)

final case class SqlCodegenErrorType(
    shapeId: ShapeId,
    name: String,
    className: String
)

final case class SqlCodegenOperation(
    shapeId: ShapeId,
    name: String,
    methodName: String,
    parameters: List[SqlCodegenParameter],
    outputShapeId: Option[ShapeId],
    outputClassName: Option[String],
    errors: List[SqlCodegenErrorType],
    responseUnion: String,
    responseType: String,
    sql: Option[SqlCodegenSqlBinding] = None
)

final case class SqlCodegenBindParameter(
    memberName: String,
    languageTypeName: String,
    isJson: Boolean = false,
    jsonTypeName: Option[String] = None,
    timestampFormat: Option[SqlTimestampFormat] = None,
    bindExpression: Option[String] = None
)

final case class SqlCodegenResultField(
    fieldName: String,
    columnName: String,
    columnIndex: Int,
    languageTypeName: String,
    isJson: Boolean = false,
    timestampFormat: Option[SqlTimestampFormat] = None,
    rowReader: Option[String] = None,
    jsonReadExpression: Option[String] = None
)

final case class SqlCodegenSqlBinding(
    queryKind: String,
    sqlStatement: SqlParameterizedStatement,
    tableName: String,
    bindParameters: List[SqlCodegenBindParameter],
    executionMode: String,
    outputKind: String,
    returningColumnIndex: Option[Int],
    resultFields: List[SqlCodegenResultField]
)

final case class SqlCodegenUnionMember(
    name: String,
    variantClassName: String,
    languageTypeName: String
)

final case class SqlCodegenUnion(
    shapeId: ShapeId,
    name: String,
    namespace: String,
    members: List[SqlCodegenUnionMember]
)

final case class SqlCodegenServiceContext(
    shapeId: ShapeId,
    name: String,
    namespace: String,
    fileName: String,
    version: String,
    dialectKey: String,
    queryRenderer: SqlQueryRenderer,
    bindPlaceholderStyle: SqlBindPlaceholder,
    implementationClassName: String,
    implementationModuleName: String,
    hasSqlOperations: Boolean,
    models: List[SqlCodegenStructure],
    unions: List[SqlCodegenUnion],
    operations: List[SqlCodegenOperation],
    integrationTest: Option[SqlCodegenIntegrationTestContext] = None
)

final case class SqlCodegenIntegrationTestContext(
    schemaDdl: String,
    serviceFixtureName: String,
    implementationClassName: String,
    implementationModuleName: String,
    insertOperation: SqlCodegenIntegrationTestOperation,
    selectOneOperation: SqlCodegenIntegrationTestOperation,
    updateOperation: Option[SqlCodegenIntegrationTestOperation],
    deleteOperation: Option[SqlCodegenIntegrationTestOperation],
    transactionCommitInTxAssertions: List[String],
    transactionCommitAfterAssertions: List[String],
    extraImports: List[String]
)

final case class SqlCodegenIntegrationTestOperation(
    methodName: String,
    callArguments: String,
    updatedCallArguments: Option[String],
    outputClassName: Option[String],
    resultAssertions: List[String],
    updatedResultAssertions: List[String]
)

final case class SqlCodegenArtifact(
    relativePath: String,
    content: String,
    kind: SqlServiceCodegenArtifactKind
)
