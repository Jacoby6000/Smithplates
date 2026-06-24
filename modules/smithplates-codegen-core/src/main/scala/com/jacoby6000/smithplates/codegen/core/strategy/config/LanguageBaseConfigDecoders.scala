package com.jacoby6000.smithplates.codegen.core.strategy.config

import com.jacoby6000.smithplates.codegen.core.json.StrictJsonDecoding.makeStrict
import com.jacoby6000.smithplates.codegen.core.strategy.NamingConvention
import com.jacoby6000.smithplates.codegen.core.strategy.NamingConventionStyle
import com.jacoby6000.smithplates.codegen.core.strategy.NamingStrategy
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

/** Circe decoders for language template `base_config.json` files. */
object LanguageBaseConfigDecoders {
  implicit val languageBaseConfigDecoder: Decoder[LanguageBaseConfig] =
    deriveDecoder[LanguageBaseConfig].makeStrict

  implicit val typeSyntaxConfigDecoder: Decoder[TypeSyntaxConfig] =
    deriveDecoder[TypeSyntaxConfig].makeStrict

  implicit val namingStrategyDecoder: Decoder[NamingStrategy] =
    deriveDecoder[internal.NamingStrategyJson].makeStrict.map(_.toDomain)

  implicit val namingConventionDecoder: Decoder[NamingConvention] =
    Decoder.decodeString
      .emap(decodeConventionFromStyle)
      .or(deriveDecoder[internal.NamingConventionJson].makeStrict.emap(_.toDomain))

  private def decodeConventionFromStyle(style: String): Either[String, NamingConvention] =
    decodeStyle(style, "style").map(NamingConvention(_, ""))

  private def decodeStyle(value: String, path: String): Either[String, NamingConventionStyle] =
    value.toLowerCase match {
      case "snake_case"           => Right(NamingConventionStyle.SnakeCase)
      case "pascal_case"          => Right(NamingConventionStyle.PascalCase)
      case "camel_case"           => Right(NamingConventionStyle.CamelCase)
      case "screaming_snake_case" => Right(NamingConventionStyle.ScreamingSnakeCase)
      case "unchanged"            => Right(NamingConventionStyle.Unchanged)
      case other                  => Left(s"$path has unsupported style '$other'")
    }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    final case class NamingStrategyJson(
        fileNames: NamingConvention,
        packageSeparator: String,
        classNames: NamingConvention,
        packageNames: NamingConvention,
        valueNames: NamingConvention,
        constantNames: NamingConvention,
        functionNames: NamingConvention,
        illegalCharRemaps: Option[Map[String, String]] = None,
        reservedKeywordRemaps: Option[Map[String, String]] = None
    ) {
      def toDomain: NamingStrategy =
        NamingStrategy(
          fileNames = fileNames,
          packageSeparator = packageSeparator,
          classNames = classNames,
          packageNames = packageNames,
          valueNames = valueNames,
          constantNames = constantNames,
          functionNames = functionNames,
          illegalCharRemaps = illegalCharRemaps.getOrElse(Map.empty),
          reservedKeywordRemaps = reservedKeywordRemaps.getOrElse(Map.empty)
        )
    }

    final case class NamingConventionJson(
        style: String,
        suffix: Option[String] = None
    ) {
      def toDomain: Either[String, NamingConvention] =
        LanguageBaseConfigDecoders
          .decodeStyle(style, "style")
          .map(styleEnum => NamingConvention(styleEnum, suffix.getOrElse("")))
    }
  }
}
