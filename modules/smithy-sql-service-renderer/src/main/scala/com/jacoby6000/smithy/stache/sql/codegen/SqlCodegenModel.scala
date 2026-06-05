package com.jacoby6000.smithy.stache.sql.codegen

import com.jacoby6000.smithy.stache.sql.SqlDialect
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
    pythonTypeName: String,
    optional: Boolean,
    isStructure: Boolean,
    structureShapeId: Option[ShapeId],
    isUnion: Boolean = false
)

final case class SqlCodegenParameter(
    name: String,
    typeName: String,
    pythonTypeName: String,
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
    pythonResponseType: String,
    sql: Option[SqlCodegenSqlBinding] = None
)

final case class SqlCodegenBindParameter(
    memberName: String,
    pythonExpression: String,
    isJson: Boolean = false,
    jsonPythonTypeName: Option[String] = None
)

final case class SqlCodegenResultField(
    fieldName: String,
    columnIndex: Int,
    pythonTypeName: String,
    rowReader: String,
    isJson: Boolean = false,
    jsonReadExpression: Option[String] = None
)

final case class SqlCodegenSqlBinding(
    queryKind: String,
    sqlStatement: com.jacoby6000.smithy.stache.sql.shared.SqlParameterizedStatement,
    tableName: String,
    bindParameters: List[SqlCodegenBindParameter],
    executionMode: String,
    outputKind: String,
    returningColumnIndex: Option[Int],
    resultFields: List[SqlCodegenResultField],
    notFoundErrorClassName: Option[String]
)

final case class SqlCodegenUnionMember(
    name: String,
    variantClassName: String,
    pythonTypeName: String
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
    dialect: SqlDialect,
    bindPlaceholderStyle: com.jacoby6000.smithy.stache.sql.shared.SqlBindPlaceholder,
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
    implementationModuleName: String,
    insertOperation: SqlCodegenIntegrationTestOperation,
    selectOneOperation: SqlCodegenIntegrationTestOperation,
    updateOperation: Option[SqlCodegenIntegrationTestOperation],
    deleteOperation: Option[SqlCodegenIntegrationTestOperation],
    selectOneResultAssertions: String,
    updateLifecycleBlock: Option[String],
    deleteLifecycleBlock: Option[String],
    extraImports: List[String]
)

final case class SqlCodegenIntegrationTestOperation(
    methodName: String,
    callArguments: String,
    updatedCallArguments: Option[String],
    outputClassName: Option[String],
    notFoundErrorClassName: Option[String],
    resultAssertions: List[String],
    updatedResultAssertions: List[String]
)

final case class SqlCodegenArtifact(
    relativePath: String,
    content: String,
    kind: SqlServiceCodegenArtifactKind
)
