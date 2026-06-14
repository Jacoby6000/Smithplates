package com.jacoby6000.smithplates.http.service.renderer

import org.fusesource.scalate.util.Resource
import org.fusesource.scalate.util.ResourceLoader
import org.fusesource.scalate.util.ResourceNotFoundException
import org.fusesource.scalate.util.StringResource

/** Prepends a fixed SSP preamble to every template loaded from a template root. */
final class PreambleTemplateRootResourceLoader(
    classLoader: ClassLoader,
    templateRoot: String,
    preamble: String
) extends ResourceLoader {
  private val delegate = new TemplateRootResourceLoader(classLoader, templateRoot)

  override def resource(uri: String): Option[Resource] =
    delegate.resource(uri).map { loaded =>
      StringResource(uri, preamble + loaded.text)
    }

  override def resolve(base: String, path: String): String =
    delegate.resolve(base, path)

  override protected def createNotFoundException(uri: String): ResourceNotFoundException =
    new ResourceNotFoundException(uri, delegate.normalizedRoot)
}
