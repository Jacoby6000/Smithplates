package com.jacoby6000.smithplates.plugin

import cats.syntax.all.*
import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.ddl.renderer.common.SqlShared
import com.jacoby6000.smithplates.sql.model.InvalidPluginConfig
import software.amazon.smithy.model.node.ObjectNode

object PluginConfigMembers {
  def optionalStringMember(
      node: ObjectNode,
      memberName: String
  ): Option[String] =
    Option(node.getMember(memberName).orElse(null)).flatMap {
      case value if value.isStringNode => SqlShared.trimmedNonEmpty(value.expectStringNode().getValue)
      case _                           => None
    }

  def requiredStringMember(
      node: ObjectNode,
      memberName: String,
      message: String
  ): SqlValidated[String] =
    Option(node.getMember(memberName).orElse(null)) match {
      case Some(value) if value.isStringNode =>
        SqlShared
          .trimmedNonEmpty(value.expectStringNode().getValue)
          .map(SqlValidated.valid)
          .getOrElse(SqlValidated.invalid(InvalidPluginConfig(s"$memberName must be a non-empty string")))
      case Some(_)                           =>
        SqlValidated.invalid(InvalidPluginConfig(s"$memberName must be a string"))
      case None                              =>
        SqlValidated.invalid(InvalidPluginConfig(message))
    }

  def rejectNestedOutputDirectories(
      configPath: String,
      languageId: String,
      node: ObjectNode
  ): SqlValidated[Unit] = {
    val nestedKeys =
      List("sourceOutputDir", "testOutputDir").filter(memberName =>
        Option(node.getMember(memberName).orElse(null)).isDefined)
    if (nestedKeys.isEmpty) {
      ().validNel
    } else {
      SqlValidated.invalid(
        InvalidPluginConfig(
          s"smithplates.$languageId.$configPath must not set ${nestedKeys.mkString(" or ")}; " +
            s"use smithplates.$languageId.sourceOutputDir and smithplates.$languageId.testOutputDir instead"
        )
      )
    }
  }
}
