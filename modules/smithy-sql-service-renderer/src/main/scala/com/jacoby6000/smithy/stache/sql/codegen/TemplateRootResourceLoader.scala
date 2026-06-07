package com.jacoby6000.smithy.stache.sql.codegen

import org.fusesource.scalate.util.Resource
import org.fusesource.scalate.util.ResourceLoader
import org.fusesource.scalate.util.ResourceNotFoundException

/** Loads SSP templates from the classpath relative to a fixed template root. */
final class TemplateRootResourceLoader(classLoader: ClassLoader, templateRoot: String) extends ResourceLoader {
  val normalizedRoot: String =
    templateRoot.stripPrefix("/").stripSuffix("/")

  override def resource(uri: String): Option[Resource] = {
    val resourcePath = classpathPath(uri)
    Option(classLoader.getResource(resourcePath)).map(Resource.fromURL)
  }

  override def resolve(base: String, path: String): String =
    if (path.startsWith("/")) {
      normalizeUri(path)
    } else {
      normalizeUri(path)
    }

  override protected def createNotFoundException(uri: String): ResourceNotFoundException =
    new ResourceNotFoundException(uri, normalizedRoot, s"Template not found under classpath root '$normalizedRoot'")

  private def normalizeUri(uri: String): String = {
    val stripped      = uri.stripPrefix("/")
    val withExtension =
      if (stripped.endsWith(".ssp")) {
        stripped
      } else {
        s"$stripped.ssp"
      }
    if (withExtension.startsWith(s"$normalizedRoot/")) {
      withExtension
    } else {
      s"$normalizedRoot/$withExtension"
    }
  }

  private def classpathPath(uri: String): String =
    normalizeUri(uri)
}
