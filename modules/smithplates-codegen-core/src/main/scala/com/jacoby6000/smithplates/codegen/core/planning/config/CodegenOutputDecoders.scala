package com.jacoby6000.smithplates.codegen.core.planning.config

import com.jacoby6000.smithplates.codegen.core.ModelKind
import com.jacoby6000.smithplates.codegen.core.planning.ArtifactKind
import com.jacoby6000.smithplates.codegen.core.planning.BindingFilterAtom
import com.jacoby6000.smithplates.codegen.core.planning.BindingGroup
import com.jacoby6000.smithplates.codegen.core.planning.CodegenOutput
import com.jacoby6000.smithplates.codegen.core.planning.OutputId
import com.jacoby6000.smithplates.codegen.core.planning.SmithyBinding
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.HCursor
import io.circe.JsonObject

/** Circe decoders that build language-neutral [[CodegenOutput]] decks from JSON resource files.
  *
  * The full `outputs.json` contract (schema, bindings, path placeholders, verbatim-copy rule) is documented in
  * `.ai-doc-reference/codegen-output-deck.md`. Keep that doc in sync when changing these decoders.
  */
object CodegenOutputDecoders {
  given artifactKindDecoder: Decoder[ArtifactKind] =
    Decoder.decodeString.emap {
      case "src"  => Right(ArtifactKind.Src)
      case "test" => Right(ArtifactKind.Test)
      case other  => Left(s"unsupported artifactKind '$other' (expected 'src' or 'test')")
    }

  given bindingGroupDecoder: Decoder[BindingGroup] =
    Decoder.decodeString.emap {
      case "all"  => Right(BindingGroup.All)
      case "none" => Right(BindingGroup.None)
      case "tag"  => Right(BindingGroup.Tag)
      case other  => Left(s"unsupported binding groupBy '$other' (expected 'all', 'none' or 'tag')")
    }

  given modelKindDecoder: Decoder[ModelKind] =
    Decoder.decodeString.emap {
      case "structure" => Right(ModelKind.Structure)
      case "union"     => Right(ModelKind.Union)
      case "enum"      => Right(ModelKind.Enum)
      case "alias"     => Right(ModelKind.Alias)
      case other       => Left(s"unsupported model kind '$other'")
    }

  given bindingFilterAtomDecoder: Decoder[BindingFilterAtom] =
    Decoder.decodeString
      .emap {
        case "all"      => Right(BindingFilterAtom.All)
        case "tagged"   => Right(BindingFilterAtom.Tagged)
        case "untagged" => Right(BindingFilterAtom.Untagged)
        case other      => Left(s"unsupported binding filter '$other'")
      }
      .or(kindFilterDecoder)

  given smithyBindingDecoder: Decoder[SmithyBinding] =
    Decoder.instance { cursor =>
      cursor.get[String]("type").flatMap {
        case "service"   => rejectExtraKeys(cursor, Set("type")).map(_ => SmithyBinding.Service)
        case "once"      => rejectExtraKeys(cursor, Set("type")).map(_ => SmithyBinding.Once)
        case "operation" =>
          decodeBindingSelector(cursor).map((filters, groupBy) => SmithyBinding.Operation(filters, groupBy))
        case "model"     =>
          decodeBindingSelector(cursor).map((filters, groupBy) => SmithyBinding.Model(filters, groupBy))
        case other       =>
          Left(DecodingFailure(s"unsupported binding type '$other'", cursor.history))
      }
    }

  given codegenOutputDecoder: Decoder[CodegenOutput] =
    Decoder.instance { cursor =>
      for {
        id           <- cursor.get[String]("id")
        artifactKind <- cursor.get[ArtifactKind]("artifactKind")
        outputType   <- cursor.getOrElse[String]("type")("template")
        overrides    <- cursor.get[Option[String]]("overrides")
        output       <- outputType match {
                          case "template" => decodeTemplateOutput(cursor, id, artifactKind, overrides)
                          case "static"   => decodeStaticOutput(cursor, id, artifactKind, overrides)
                          case other      =>
                            Left(DecodingFailure(s"unsupported output type '$other'", cursor.history))
                        }
      } yield output
    }

  private val kindFilterDecoder: Decoder[BindingFilterAtom] =
    Decoder.instance { cursor =>
      rejectExtraKeys(cursor, Set("kind"))
        .flatMap(_ => cursor.get[ModelKind]("kind"))
        .map(BindingFilterAtom.Kind(_))
    }

  private def decodeBindingSelector(cursor: HCursor): Decoder.Result[(List[BindingFilterAtom], BindingGroup)] =
    for {
      _       <- rejectExtraKeys(cursor, Set("type", "filters", "groupBy"))
      filters <- cursor.getOrElse[List[BindingFilterAtom]]("filters")(Nil)
      groupBy <- cursor.getOrElse[BindingGroup]("groupBy")(BindingGroup.None)
    } yield (filters, groupBy)

  private val CommonOutputKeys: Set[String]   = Set("id", "artifactKind", "type", "overrides")
  private val TemplateOutputKeys: Set[String] = CommonOutputKeys ++ Set("template", "outputPath", "binding")
  private val StaticOutputKeys: Set[String]   = CommonOutputKeys ++ Set("filePath", "copyToPath")

  private def decodeTemplateOutput(
      cursor: HCursor,
      id: String,
      artifactKind: ArtifactKind,
      overrides: Option[String]
  ): Decoder.Result[CodegenOutput] =
    for {
      _          <- rejectExtraKeys(cursor, TemplateOutputKeys)
      template   <- cursor.get[String]("template")
      outputPath <- cursor.get[String]("outputPath")
      binding    <- cursor.get[SmithyBinding]("binding")
    } yield CodegenOutput.CodegenTemplateBindingOutput(
      id = OutputId(id),
      kind = artifactKind,
      templatePath = template,
      outputPath = outputPath,
      binding = binding,
      overrides = overrides.map(OutputId(_))
    )

  private def decodeStaticOutput(
      cursor: HCursor,
      id: String,
      artifactKind: ArtifactKind,
      overrides: Option[String]
  ): Decoder.Result[CodegenOutput] =
    for {
      _          <- rejectExtraKeys(cursor, StaticOutputKeys)
      filePath   <- cursor.get[String]("filePath")
      copyToPath <- cursor.get[String]("copyToPath")
    } yield CodegenOutput.CodegenStaticOutput(
      id = OutputId(id),
      kind = artifactKind,
      filePath = filePath,
      copyToPath = copyToPath,
      overrides = overrides.map(OutputId(_))
    )

  private def rejectExtraKeys(cursor: HCursor, allowedKeys: Set[String]): Decoder.Result[Unit] =
    cursor.as[JsonObject].flatMap { jsonObject =>
      val extraKeys = jsonObject.keys.filterNot(allowedKeys.contains).toList.sorted
      if (extraKeys.isEmpty) {
        Right(())
      } else {
        Left(
          DecodingFailure(
            s"unexpected keys: ${extraKeys.mkString(", ")} (allowed: ${allowedKeys.toList.sorted.mkString(", ")})",
            cursor.history
          )
        )
      }
    }
}
