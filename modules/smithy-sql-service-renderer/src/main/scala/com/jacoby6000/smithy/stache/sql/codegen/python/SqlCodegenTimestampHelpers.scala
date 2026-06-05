package com.jacoby6000.smithy.stache.sql.codegen.python

import com.jacoby6000.smithy.stache.sql.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.MemberShape

object SqlCodegenTimestampHelpers {
  def resolveTimestampFormat(model: Model, member: MemberShape): SqlTimestampFormat =
    SmithyTimestampFormatResolver.resolve(model, member) match {
      case Right(format) => format
      case Left(_)       => SqlTimestampFormat.Default
    }

  def rowReaderName(pythonTypeName: String, format: SqlTimestampFormat): String =
    if (pythonTypeName == "datetime") {
      format match {
        case SqlTimestampFormat.EpochSeconds => "_read_epoch_seconds"
        case SqlTimestampFormat.DateTime     => "_read_datetime"
      }
    } else {
      SqlCodegenRowReaders.scalarReaderName(pythonTypeName)
    }

  def bindExpression(
      dialectKey: String,
      pythonTypeName: String,
      format: SqlTimestampFormat,
      memberName: String
  ): String =
    if (pythonTypeName != "datetime") {
      memberName
    } else {
      (dialectKey, format) match {
        case ("sqlite", SqlTimestampFormat.DateTime)       => s"_timestamp_bind_datetime($memberName)"
        case ("sqlite", SqlTimestampFormat.EpochSeconds)   => s"_timestamp_bind_epoch_seconds($memberName)"
        case ("postgres", SqlTimestampFormat.EpochSeconds) => s"_timestamp_bind_epoch_seconds($memberName)"
        case ("postgres", SqlTimestampFormat.DateTime)     => memberName
        case (other, _)                                    =>
          throw new IllegalArgumentException(s"unsupported dialect key for timestamp bind: $other")
      }
    }
}
