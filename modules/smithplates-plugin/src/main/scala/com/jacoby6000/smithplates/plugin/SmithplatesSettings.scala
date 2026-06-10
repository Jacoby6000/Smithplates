package com.jacoby6000.smithplates.plugin

import cats.syntax.all.*
import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.model.InvalidPluginConfig
import software.amazon.smithy.model.node.ObjectNode

final case class SmithplatesSettings(
    sql: Option[SmithplatesSqlSettings],
    http: Option[SmithplatesHttpSettings]
)

object SmithplatesSettings {
  def fromNode(node: ObjectNode): SqlValidated[SmithplatesSettings] = {
    val sql  = optionalSettingsSection(node, "sql", SmithplatesSqlSettings.fromNode)
    val http = optionalSettingsSection(node, "http", SmithplatesHttpSettings.fromNode)
    (sql, http)
      .mapN { (sqlSettings, httpSettings) =>
        if (sqlSettings.isEmpty && httpSettings.isEmpty) {
          InvalidPluginConfig("smithplates plugin requires at least one of `sql` or `http`").invalidNel
        } else {
          SmithplatesSettings(sql = sqlSettings, http = httpSettings).validNel
        }
      }
      .andThen(identity)
  }

  private def optionalObjectMember(
      node: ObjectNode,
      memberName: String
  ): SqlValidated[Option[ObjectNode]] =
    Option(node.getMember(memberName).orElse(null)) match {
      case None                         => SqlValidated.valid(None)
      case Some(objectNode: ObjectNode) => SqlValidated.valid(Some(objectNode))
      case Some(_)                      =>
        SqlValidated.invalid(InvalidPluginConfig(s"smithplates $memberName must be an object"))
    }

  private def optionalSettingsSection[A](
      node: ObjectNode,
      memberName: String,
      parse: ObjectNode => SqlValidated[A]
  ): SqlValidated[Option[A]] =
    optionalObjectMember(node, memberName).andThen {
      case Some(sectionNode) => parse(sectionNode).map(Some(_))
      case None              => SqlValidated.valid(None)
    }
}
