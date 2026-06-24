package com.jacoby6000.smithplates.codegen.core.strategy.config

import cats.syntax.all.*
import com.jacoby6000.smithplates.codegen.core.CodegenValidated
import com.jacoby6000.smithplates.codegen.core.InvalidLanguageBaseConfig
import com.jacoby6000.smithplates.codegen.core.json.JsonDecoding

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import scala.util.control.NonFatal

object LanguageBaseConfigLoader {
  val BaseConfigFileName: String = "base_config.json"

  def loadPath(path: Path): CodegenValidated[LanguageBaseConfig] =
    try
      loadJson(new String(Files.readAllBytes(path), StandardCharsets.UTF_8))
    catch {
      case NonFatal(error) =>
        InvalidLanguageBaseConfig(s"failed to read $path: ${error.getMessage}").invalidNel
    }

  def loadLanguageTemplateRoot(templateRoot: Path): CodegenValidated[LanguageBaseConfig] =
    loadPath(templateRoot.resolve(BaseConfigFileName))

  def loadJson(text: String): CodegenValidated[LanguageBaseConfig] = {
    import LanguageBaseConfigDecoders.given
    JsonDecoding
      .decodeJsonValidated[LanguageBaseConfig, InvalidLanguageBaseConfig](text, InvalidLanguageBaseConfig.apply)
  }
}
