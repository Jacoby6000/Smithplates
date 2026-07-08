package com.jacoby6000.smithplates.scalate

import org.fusesource.scalate.util.Resource
import org.fusesource.scalate.util.ResourceLoader
import org.fusesource.scalate.util.ResourceNotFoundException

/** Loads SSP templates from the classpath relative to a fixed template root. */
final class TemplateRootResourceLoader(classLoader: ClassLoader, templateRoot: String) extends ResourceLoader {
  val normalizedRoot: String =
    templateRoot.stripPrefix("/").stripSuffix("/")

  override def resource(uri: String): Option[Resource] = {
    val resourcePath =
      TemplateRootResourceLoader.internal.classpathPath(uri, normalizedRoot, classLoader)
    Option(classLoader.getResource(resourcePath)).map(Resource.fromURL)
  }

  override def resolve(base: String, path: String): String =
    TemplateRootResourceLoader.internal.normalizeUri(path, normalizedRoot)

  override protected def createNotFoundException(uri: String): ResourceNotFoundException =
    new ResourceNotFoundException(uri, normalizedRoot, s"Template not found under classpath root '$normalizedRoot'")
}

object TemplateRootResourceLoader {

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def normalizeUri(uri: String, normalizedRoot: String): String = {
      val stripped      = uri.stripPrefix("/")
      val withExtension =
        if (stripped.endsWith(".ssp")) {
          stripped
        } else {
          s"$stripped.ssp"
        }
      if (normalizedRoot.isEmpty) {
        withExtension
      } else if (withExtension.startsWith(s"$normalizedRoot/")) {
        withExtension
      } else {
        s"$normalizedRoot/$withExtension"
      }
    }

    def classpathPath(uri: String, normalizedRoot: String, classLoader: ClassLoader): String = {
      val relativePath = normalizeUri(uri, normalizedRoot)
      val stripped     = uri.stripPrefix("/")
      if (stripped.contains("/") && !stripped.startsWith(s"$normalizedRoot/")) {
        val absolutePath = ScalateTemplatePaths.normalizeResourcePath(stripped).stripPrefix("/")
        if (classLoader.getResource(absolutePath) != null &&
          classLoader.getResource(relativePath) == null) {
          absolutePath
        } else {
          relativePath
        }
      } else {
        relativePath
      }
    }
  }
}
