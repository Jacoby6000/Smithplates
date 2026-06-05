package com.jacoby6000.smithy.stache

import cats.syntax.all.*
import com.jacoby6000.smithy.stache.sql.InvalidPluginConfig
import com.jacoby6000.smithy.stache.sql.SqlValidated
import software.amazon.smithy.model.node.ObjectNode

final case class SmithyStacheSettings(
    sql: SmithyStacheSqlSettings
)

object SmithyStacheSettings {
  def fromNode(node: ObjectNode): SqlValidated[SmithyStacheSettings] =
    requiredObjectMember(node, "sql", "smithy-stache plugin requires `sql` object").andThen { sqlNode =>
      SmithyStacheSqlSettings.fromNode(sqlNode).map(sql => SmithyStacheSettings(sql = sql))
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
