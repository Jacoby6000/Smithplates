package com.jacoby6000.smithplates.http.model

import software.amazon.smithy.model.shapes.ShapeId

final case class HttpServiceIr(
    services: List[HttpService]
)

final case class HttpServiceError(
    shapeId: ShapeId,
    name: String,
    statusCode: Int
)

final case class HttpStructureMember(
    name: String,
    typeName: String,
    required: Boolean,
    timestampFormat: Option[HttpTimestampFormat]
)

final case class HttpStructure(
    shapeId: ShapeId,
    name: String,
    members: List[HttpStructureMember]
)

final case class HttpService(
    shapeId: ShapeId,
    version: String,
    serialization: HttpSerialization,
    title: Option[String],
    documentation: Option[String],
    serviceErrors: List[HttpServiceError],
    resources: List[HttpResource],
    routeGroups: List[HttpRouteGroup],
    structures: List[HttpStructure]
)

final case class HttpResource(
    shapeId: ShapeId,
    name: String,
    identifiers: List[String],
    propertyNames: List[String],
    createOperation: Option[ShapeId],
    readOperation: Option[ShapeId],
    listOperation: Option[ShapeId],
    updateOperation: Option[ShapeId],
    deleteOperation: Option[ShapeId],
    childResourceIds: List[ShapeId]
)

final case class HttpRouteGroup(
    tag: String,
    operations: List[HttpOperation]
)

final case class HttpOperation(
    shapeId: ShapeId,
    name: String,
    method: String,
    uri: String,
    successStatusCode: Int,
    readonly: Boolean,
    documentation: Option[String],
    inputShape: ShapeId,
    inputBoundResource: Option[ShapeId],
    inputMembers: List[HttpOperationInputMember],
    outputShape: Option[ShapeId],
    errorShapes: List[ShapeId],
    tags: List[String]
)
