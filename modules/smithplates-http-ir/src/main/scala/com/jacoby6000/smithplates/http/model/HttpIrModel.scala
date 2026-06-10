package com.jacoby6000.smithplates.http.model

import software.amazon.smithy.model.shapes.ShapeId

final case class HttpServiceIr(
    services: List[HttpService]
)

final case class HttpService(
    shapeId: ShapeId,
    version: String,
    title: Option[String],
    documentation: Option[String],
    serviceErrors: List[ShapeId],
    resources: List[HttpResource],
    routeGroups: List[HttpRouteGroup]
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
    apiModuleName: String,
    protocolClassName: String,
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
