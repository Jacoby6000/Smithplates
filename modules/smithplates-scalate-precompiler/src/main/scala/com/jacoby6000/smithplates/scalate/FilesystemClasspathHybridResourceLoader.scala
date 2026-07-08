package com.jacoby6000.smithplates.scalate

import org.fusesource.scalate.util.Resource
import org.fusesource.scalate.util.ResourceLoader
import org.fusesource.scalate.util.ResourceNotFoundException
import org.fusesource.scalate.util.StringResource

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** Loads a consumer SSP template from the filesystem while resolving fragments from a classpath template root. */
final class FilesystemClasspathHybridResourceLoader(
    classLoader: ClassLoader,
    classpathTemplateRoot: String,
    preamble: String,
    filesystemTemplate: Path
) extends ResourceLoader {
  private val classpathLoader =
    new PreambleTemplateRootResourceLoader(classLoader, classpathTemplateRoot, preamble)
  private val templateUri     = filesystemTemplate.getFileName.toString

  override def resource(uri: String): Option[Resource] = {
    val normalizedUri = uri.stripPrefix("/")
    if (normalizedUri == templateUri || normalizedUri.endsWith(s"/$templateUri")) {
      if (Files.isRegularFile(filesystemTemplate)) {
        Some(
          StringResource(
            normalizedUri,
            preamble + Files.readString(filesystemTemplate, StandardCharsets.UTF_8)
          )
        )
      } else {
        None
      }
    } else {
      classpathLoader.resource(uri)
    }
  }

  override def resolve(base: String, path: String): String =
    classpathLoader.resolve(base, path)

  override protected def createNotFoundException(uri: String): ResourceNotFoundException =
    new ResourceNotFoundException(uri, classpathLoader.delegate.normalizedRoot)
}
