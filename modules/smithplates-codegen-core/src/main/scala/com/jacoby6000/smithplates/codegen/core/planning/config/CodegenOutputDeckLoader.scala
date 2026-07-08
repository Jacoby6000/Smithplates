package com.jacoby6000.smithplates.codegen.core.planning.config

import cats.syntax.all.*
import com.jacoby6000.smithplates.codegen.core.CodegenValidated
import com.jacoby6000.smithplates.codegen.core.InvalidCodegenOutputConfig
import com.jacoby6000.smithplates.codegen.core.planning.CodegenTemplatePaths

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

/** Loads a [[CodegenOutputDeck]] from the `outputs.json` resource that sits next to a language's templates. The
  * resource path is derived from the (language-encoding) template directory, so no per-language paths are baked into
  * Scala: the same composition logic serves every language and only the JSON data differs.
  */
object CodegenOutputDeckLoader {
  val OutputsFileName: String = "outputs.json"

  def resourcePath(templateDirectory: String): String =
    s"${templateDirectory.stripPrefix("classpath:").stripSuffix("/")}/$OutputsFileName"

  def load(templateDirectory: String, classLoader: ClassLoader): CodegenValidated[CodegenOutputDeck] =
    if (CodegenTemplatePaths.isFileQualified(templateDirectory)) {
      internal.loadFromFilesystem(CodegenTemplatePaths.filePath(templateDirectory))
    } else {
      val path     = resourcePath(templateDirectory)
      val cacheKey = internal.cacheKey(path, classLoader)
      internal.cache.get(cacheKey) match {
        case cached: CodegenOutputDeck => cached.validNel
        case null                      =>
          internal.readResource(path, classLoader) match {
            case Some(text) =>
              CodegenOutputDeck.loadJson(text).map { deck =>
                val _ = internal.cache.putIfAbsent(cacheKey, deck)
                deck
              }
            case None       =>
              InvalidCodegenOutputConfig(s"missing codegen output deck: $path").invalidNel
          }
      }
    }

  def loadOrThrow(templateDirectory: String, classLoader: ClassLoader): CodegenOutputDeck =
    load(templateDirectory, classLoader).fold(
      errors => throw new IllegalStateException(errors.map(_.message).toList.mkString("; ")),
      identity
    )

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    // DESNOTE(jbarber, 2026-07-04): Deck JSON is immutable classpath data; memoize parsed
    // decks per (classLoader, resource path) so repeated planner passes avoid re-parsing.
    // Keys include classLoader identity so two loaders exposing different `outputs.json`
    // at the same path cannot cross-pollinate.
    val cache = new ConcurrentHashMap[String, CodegenOutputDeck]()

    def cacheKey(resourcePath: String, classLoader: ClassLoader): String =
      s"${System.identityHashCode(classLoader)}:$resourcePath"

    def readResource(resourcePath: String, classLoader: ClassLoader): Option[String] =
      Option(classLoader.getResourceAsStream(resourcePath)).map { stream =>
        try scala.io.Source.fromInputStream(stream, "UTF-8").mkString
        finally stream.close()
      }

    def loadFromFilesystem(rootDirectory: String): CodegenValidated[CodegenOutputDeck] = {
      val deckPath = Paths.get(rootDirectory, OutputsFileName)
      if (!Files.isRegularFile(deckPath)) {
        InvalidCodegenOutputConfig(s"missing codegen output deck: $deckPath").invalidNel
      } else {
        CodegenOutputDeck.loadJson(Files.readString(deckPath, StandardCharsets.UTF_8))
      }
    }

    def filesystemDeckPath(rootDirectory: Path): Path =
      rootDirectory.resolve(OutputsFileName)
  }
}
