package com.jacoby6000.smithplates.sql.model

import software.amazon.smithy.model.shapes.ShapeId

final case class SqlShapeIr(
    structures: List[SqlStructure],
    unions: List[SqlUnion]
) {
  def structure(shapeId: ShapeId): Option[SqlStructure] =
    structures.find(_.shapeId == shapeId)
}

final case class SqlStructure(
    shapeId: ShapeId,
    name: String,
    namespace: String,
    members: List[SqlStructureMember]
)

final case class SqlStructureMember(
    name: String,
    typeName: String,
    optional: Boolean,
    isStructure: Boolean,
    structureShapeId: Option[ShapeId]
)

final case class SqlUnion(
    shapeId: ShapeId,
    name: String,
    namespace: String,
    members: List[SqlUnionMember]
)

final case class SqlUnionMember(
    name: String,
    typeName: String
)
