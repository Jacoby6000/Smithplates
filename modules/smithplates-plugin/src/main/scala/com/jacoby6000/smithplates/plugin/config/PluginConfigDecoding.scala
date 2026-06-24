package com.jacoby6000.smithplates.plugin.config

import cats.data.NonEmptyList
import cats.syntax.all.*
import com.jacoby6000.smithplates.codegen.core.json.JsonDecoding
import com.jacoby6000.smithplates.codegen.core.json.UnexpectedKeys
import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.model.InvalidPluginConfig
import io.circe.CursorOp
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Error
import io.circe.Json

object PluginConfigDecoding {
  def decode[A: Decoder](languageId: String, rootLabel: String, json: Json): SqlValidated[A] =
    json.as[A].leftMap(toInvalidPluginConfig(languageId, rootLabel)).toValidatedNel

  def toInvalidPluginConfig(languageId: String, rootLabel: String)(error: Error): InvalidPluginConfig =
    error match {
      case failure: DecodingFailure =>
        UnexpectedKeys.partition(failure) match {
          case (Some(unexpected), _) =>
            InvalidPluginConfig(unexpectedKeysMessage(languageId, rootLabel, unexpected))
          case (None, _)             =>
            InvalidPluginConfig(JsonDecoding.errorMessage(error))
        }
      case _                        =>
        InvalidPluginConfig(JsonDecoding.errorMessage(error))
    }

  private def configPathFrom(rootLabel: String, history: List[CursorOp]): String = {
    val nestedPath =
      history.collect { case CursorOp.DownField(name) => name }.mkString(".")
    if (nestedPath.isEmpty) {
      rootLabel
    } else {
      s"$rootLabel.$nestedPath"
    }
  }

  private def unexpectedKeysMessage(
      languageId: String,
      rootLabel: String,
      unexpected: NonEmptyList[UnexpectedKeys]
  ): String =
    unexpected.toList.distinct
      .map(formatUnexpectedKeys(languageId, rootLabel, _))
      .mkString("; ")

  private def formatUnexpectedKeys(
      languageId: String,
      rootLabel: String,
      unexpected: UnexpectedKeys
  ): String = {
    val configPath = configPathFrom(rootLabel, unexpected.history)
    s"smithplates.$languageId.$configPath contains unknown key(s) '${unexpected.extraKeys.toList.sorted.mkString("', '")}'; " +
      s"expected ${unexpected.allowedKeys.toList.sorted.mkString(", ")}"
  }
}
