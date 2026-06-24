package com.jacoby6000.smithplates.plugin.config

import com.jacoby6000.smithplates.codegen.core.json.JsonDecoding
import com.jacoby6000.smithplates.plugin.SmithplatesSettings
import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.model.InvalidPluginConfig
import io.circe.Json
import software.amazon.smithy.model.node.ObjectNode

object PluginConfigLoader {
  def parseJson(text: String): SqlValidated[SmithplatesSettings] =
    JsonDecoding
      .decodeJsonValidated[Map[String, Json], InvalidPluginConfig](text, InvalidPluginConfig.apply)
      .andThen(SmithplatesSettings.fromLanguageMap)

  def fromSmithyNode(node: ObjectNode): SqlValidated[SmithplatesSettings] =
    SmithyNodeJson.toJson(node).asObject match {
      case None                  =>
        SqlValidated.invalid(
          InvalidPluginConfig("smithplates plugin settings must be a JSON object")
        )
      case Some(languageConfigs) =>
        SmithplatesSettings.fromLanguageMap(languageConfigs.toMap)
    }
}
