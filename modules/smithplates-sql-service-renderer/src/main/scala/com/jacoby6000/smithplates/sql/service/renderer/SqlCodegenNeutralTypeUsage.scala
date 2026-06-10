package com.jacoby6000.smithplates.sql.service.renderer

import com.jacoby6000.smithplates.sql.model.SqlTimestampFormat

object SqlCodegenNeutralTypeUsage {
  def rowReaderForType(typeName: String, timestampFormat: Option[SqlTimestampFormat]): Option[String] =
    typeName match {
      case "String"                          => Some("_read_str")
      case "Integer" | "Long" | "BigInteger" => Some("_read_int")
      case "Float" | "Double"                => Some("_read_float")
      case "Boolean"                         => Some("_read_bool")
      case "Blob"                            => Some("_read_bytes")
      case "BigDecimal"                      => Some("_read_decimal")
      case "Timestamp"                       =>
        timestampFormat match {
          case Some(SqlTimestampFormat.EpochSeconds) => Some("_read_epoch_seconds")
          case _                                     => Some("_read_datetime")
        }
      case _                                 => None
    }

  def timestampBindHelper(
      typeName: String,
      timestampFormat: Option[SqlTimestampFormat],
      dialectKey: String
  ): Option[String] =
    if (typeName == "Timestamp") {
      (dialectKey, timestampFormat) match {
        case ("sqlite", Some(SqlTimestampFormat.DateTime))       => Some("_timestamp_bind_datetime")
        case ("sqlite", Some(SqlTimestampFormat.EpochSeconds))   => Some("_timestamp_bind_epoch_seconds")
        case ("postgres", Some(SqlTimestampFormat.EpochSeconds)) =>
          Some("_timestamp_bind_epoch_seconds")
        case _                                                   => None
      }
    } else {
      None
    }
}
