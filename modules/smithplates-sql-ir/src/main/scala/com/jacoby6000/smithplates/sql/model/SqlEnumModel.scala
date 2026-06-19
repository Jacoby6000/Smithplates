package com.jacoby6000.smithplates.sql.model

import software.amazon.smithy.model.shapes.ShapeId

final case class SqlStringEnum(
    shapeId: ShapeId,
    name: String,
    members: List[SqlStringEnumMember]
)

final case class SqlStringEnumMember(
    name: String,
    value: String
)

final case class SqlIntEnum(
    shapeId: ShapeId,
    name: String,
    members: List[SqlIntEnumMember]
)

final case class SqlIntEnumMember(
    name: String,
    value: Int
)
