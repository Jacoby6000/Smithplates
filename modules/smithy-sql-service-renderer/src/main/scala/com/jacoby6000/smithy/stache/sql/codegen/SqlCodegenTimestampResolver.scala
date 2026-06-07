package com.jacoby6000.smithy.stache.sql.codegen

import com.jacoby6000.smithy.stache.sql.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.ShapeId

object SqlCodegenTimestampResolver {
  def resolveTimestampFormat(model: Model, member: MemberShape): Option[SqlTimestampFormat] =
    if (member.getTarget == ShapeId.from("smithy.api#Timestamp")) {
      SmithyTimestampFormatResolver.resolve(model, member) match {
        case Right(format) => Some(format)
        case Left(_)       => Some(SqlTimestampFormat.Default)
      }
    } else {
      None
    }
}
