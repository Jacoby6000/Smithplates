package com.jacoby6000.smithplates.plugin

import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.ddl.renderer.common.SqlShared
import com.jacoby6000.smithplates.sql.model.InvalidPluginConfig
import io.circe.Json

object PluginConfigMembers {
  def optionalStringMember(json: Json, memberName: String): Option[String] =
    json.hcursor.downField(memberName).focus.flatMap {
      case value if value.isString => value.asString.flatMap(SqlShared.trimmedNonEmpty)
      case _                       => None
    }

  def optionalBooleanMember(json: Json, memberName: String): Option[Boolean] =
    json.hcursor.downField(memberName).focus.flatMap {
      case value if value.isBoolean => value.asBoolean
      case _                        => None
    }

  def requiredStringMember(
      json: Json,
      memberName: String,
      message: String
  ): SqlValidated[String] =
    json.hcursor.downField(memberName).focus match {
      case Some(value) if value.isString =>
        SqlShared
          .trimmedNonEmpty(value.asString.getOrElse(""))
          .map(SqlValidated.valid)
          .getOrElse(SqlValidated.invalid(InvalidPluginConfig(s"$memberName must be a non-empty string")))
      case Some(_)                       =>
        SqlValidated.invalid(InvalidPluginConfig(s"$memberName must be a string"))
      case None                          =>
        SqlValidated.invalid(InvalidPluginConfig(message))
    }
}
