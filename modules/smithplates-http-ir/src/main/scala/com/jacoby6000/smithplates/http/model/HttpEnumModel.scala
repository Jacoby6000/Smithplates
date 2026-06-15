package com.jacoby6000.smithplates.http.model

import software.amazon.smithy.model.shapes.ShapeId

final case class HttpStringEnum(
    shapeId: ShapeId,
    name: String,
    members: List[HttpStringEnumMember]
)

final case class HttpStringEnumMember(
    name: String,
    value: String
)

final case class HttpIntEnum(
    shapeId: ShapeId,
    name: String,
    members: List[HttpIntEnumMember]
)

final case class HttpIntEnumMember(
    name: String,
    value: Int
)
