package com.jacoby6000.smithplates.codegen.core.planning.config

import cats.syntax.all.*
import com.jacoby6000.smithplates.codegen.core.CodegenValidated
import com.jacoby6000.smithplates.codegen.core.InvalidCodegenOutputConfig
import com.jacoby6000.smithplates.codegen.core.json.JsonDecoding
import com.jacoby6000.smithplates.codegen.core.json.StrictJsonDecoding
import com.jacoby6000.smithplates.codegen.core.planning.CodegenOutput
import io.circe.Decoder

/** A language-neutral bundle of [[CodegenOutput]]s loaded from a JSON resource. `shared` outputs are always emitted;
  * each `variants` entry is composed in only when its key (e.g. a framework or library) is enabled.
  */
final case class CodegenOutputDeck(
    shared: List[CodegenOutput],
    variants: Map[String, List[CodegenOutput]]
) {
  def variant(key: String): Option[List[CodegenOutput]] =
    variants.get(key)

  def forEnabled(keys: List[String]): CodegenValidated[List[CodegenOutput]] =
    keys
      .traverse(key =>
        variant(key) match {
          case Some(outputs) => outputs.validNel
          case None          =>
            InvalidCodegenOutputConfig(
              s"unknown deck variant '$key' (available: ${variants.keys.toList.sorted.mkString(", ")})"
            ).invalidNel
        })
      .map(perVariant => shared ++ perVariant.flatten)

  /** Like [[forEnabled]] but tolerant of missing variants: enabled keys with no matching variant contribute nothing
    * rather than failing. Consumer `additionalTemplatesDirectory` decks are not required to redeclare every bundled
    * dialect/framework variant, so a missing key is not an error for them.
    */
  def forAvailable(keys: List[String]): List[CodegenOutput] =
    shared ++ keys.flatMap(key => variant(key).getOrElse(Nil))
}

object CodegenOutputDeck {
  val empty: CodegenOutputDeck = CodegenOutputDeck(Nil, Map.empty)

  given decoder: Decoder[CodegenOutputDeck] = {
    import CodegenOutputDecoders.given
    Decoder.instance { cursor =>
      for {
        _        <- StrictJsonDecoding.rejectExtraKeys(cursor, Set("shared", "variants"))
        shared   <- cursor.getOrElse[List[CodegenOutput]]("shared")(Nil)
        variants <- cursor.getOrElse[Map[String, List[CodegenOutput]]]("variants")(Map.empty)
      } yield CodegenOutputDeck(shared, variants)
    }
  }

  def loadJson(text: String): CodegenValidated[CodegenOutputDeck] =
    JsonDecoding.decodeJsonValidated[CodegenOutputDeck, InvalidCodegenOutputConfig](
      text,
      InvalidCodegenOutputConfig.apply)
}
