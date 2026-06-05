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
      dialect: SqlDialect,
      pythonTypeName: String,
      format: SqlTimestampFormat,
      memberName: String
  ): String =
    if (pythonTypeName != "datetime") {
      memberName
    } else {
      (dialect, format) match {
        case (SqliteDialect, SqlTimestampFormat.DateTime)       => s"_timestamp_bind_datetime($memberName)"
        case (SqliteDialect, SqlTimestampFormat.EpochSeconds)   => s"_timestamp_bind_epoch_seconds($memberName)"
        case (PostgresDialect, SqlTimestampFormat.EpochSeconds) => s"_timestamp_bind_epoch_seconds($memberName)"
        case (PostgresDialect, SqlTimestampFormat.DateTime)     => memberName
      }
    }
}
