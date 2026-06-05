package com.jacoby6000.smithy.stache.sql

import software.amazon.smithy.model.shapes.ShapeId

sealed trait SqlColumnType

object SqlColumnType {
  case object Text                                       extends SqlColumnType
  case object Integer                                    extends SqlColumnType
  case object BigInt                                     extends SqlColumnType
  final case class Timestamp(format: SqlTimestampFormat) extends SqlColumnType
  case object Boolean                                    extends SqlColumnType
  case object Uuid                                       extends SqlColumnType
  case object Json                                       extends SqlColumnType
  case object Blob                                       extends SqlColumnType
  final case class Varchar(maxLength: Int)               extends SqlColumnType

  /** Smithy `enum`; values are the serialized enum labels stored in the column. */
  final case class StringEnum(shapeId: ShapeId, typeName: String, values: List[String]) extends SqlColumnType

  /** Smithy `intEnum`; values are the allowed integer constants. */
  final case class IntEnum(typeName: String, values: List[Int]) extends SqlColumnType
}
