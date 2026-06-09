package com.jacoby6000.smithplates

import cats.syntax.all.*
import com.jacoby6000.smithplates.sql.InvalidPluginConfig
import com.jacoby6000.smithplates.sql.SqlValidated
import software.amazon.smithy.model.node.ObjectNode

final case class SmithplatesSettings(
    sql: SmithplatesSqlSettings
)

object SmithplatesSettings {
  def fromNode(node: ObjectNode): SqlValidated[SmithplatesSettings] =
    requiredObjectMember(node, "sql", "smithplates plugin requires `sql` object").andThen { sqlNode =>
      SmithplatesSqlSettings.fromNode(sqlNode).map(sql => SmithplatesSettings(sql = sql))
    }

  private def requiredObjectMember(
      node: ObjectNode,
      memberName: String,
      message: String
  ): SqlValidated[ObjectNode] =
    Option(node.getMember(memberName).orElse(null)) match {
      case Some(objectNode: ObjectNode) => SqlValidated.valid(objectNode)
      case Some(_)                      => SqlValidated.invalid(InvalidPluginConfig(s"$memberName must be an object"))
      case None                         => SqlValidated.invalid(InvalidPluginConfig(message))
    }
}
