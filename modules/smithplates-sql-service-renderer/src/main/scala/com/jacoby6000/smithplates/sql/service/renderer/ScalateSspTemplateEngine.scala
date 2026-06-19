package com.jacoby6000.smithplates.sql.service.renderer

import com.jacoby6000.smithplates.scalate.PreambleTemplateRootResourceLoader
import com.jacoby6000.smithplates.scalate.ScalateTemplatePaths
import com.jacoby6000.smithplates.scalate.precompiler.ConfiguredTemplateEngine
import com.jacoby6000.smithplates.scalate.precompiler.ScalateTemplatePrecompiler
import org.fusesource.scalate.Template
import org.fusesource.scalate.TemplateEngine

import java.util.concurrent.ConcurrentHashMap

object ScalateSspTemplateEngine {
  private val compiledTemplateCache      = new ConcurrentHashMap[String, Template]()
  private val CommonPreambleClasspath    = "python/src/common/fragments/preamble.ssp"
  private val baseContextBindingPreamble =
    """<% def isTruthy(value: Any): Boolean = com.jacoby6000.smithplates.sql.service.renderer.ScalateTemplateHelpers.isTruthy(value) %>
"""

  def readClasspathResource(resourceClasspath: String): String = {
    val resourcePath = ScalateTemplatePaths.normalizeResourcePath(
      ScalateTemplatePaths.normalizeClasspathUri(resourceClasspath)
    )
    ScalateTemplatePaths.normalizeRenderedOutput(readClasspathTemplate(resourcePath))
  }

  def classpathResourceExists(resourcePath: String): Boolean = {
    val normalized = ScalateTemplatePaths.normalizeResourcePath(resourcePath)
    Option(getClass.getResource(normalized)).isDefined
  }

  def renderClasspathTemplate(
      templateClasspath: String,
      view: ServiceTemplateView,
      templateRoot: Option[String] = None
  ): String = {
    val normalizedTemplatePath = ScalateTemplatePaths.normalizeClasspathUri(templateClasspath)
    val resolvedTemplateRoot   =
      templateRoot
        .map(ScalateTemplatePaths.normalizeTemplateRoot)
        .getOrElse(inferTemplateRoot(normalizedTemplatePath))
    val templateUri            =
      ScalateTemplatePaths.templateUriRelativeToRoot(normalizedTemplatePath, resolvedTemplateRoot)
    val engine                 = templateEngine(resolvedTemplateRoot, Some(resolvedTemplateRoot))
    val template               = compiledTemplateCache.computeIfAbsent(
      s"$resolvedTemplateRoot:$templateUri",
      _ => engine.load(templateUri)
    )
    val renderedBody           =
      ScalateTemplatePaths.normalizeRenderedOutput(
        engine.layout(templateUri, template, toObjectMap(Map("ctx" -> view)))
      )
    prependGeneratedFileHeader(view, resolvedTemplateRoot, renderedBody)
  }

  private def prependGeneratedFileHeader(
      view: ServiceTemplateView,
      templateRoot: String,
      renderedBody: String
  ): String = {
    val trimmedBody = renderedBody.stripTrailing()
    if (trimmedBody.isEmpty) {
      trimmedBody
    } else {
      val header =
        ScalateTemplateHelpers.generatedFileHeader(
          view.serviceShapeId,
          ScalateTemplateHelpers.languageFromTemplateRoot(templateRoot)
        )
      s"$header\n$trimmedBody\n"
    }
  }

  def renderServiceModuleBaseName(templateRoot: String, serviceName: String): String =
    renderClasspathPartial(
      templateRoot,
      "fragments/naming/service_module_base_name",
      Map("serviceName" -> serviceName)
    ).strip()

  def renderClasspathPartial(
      templateRoot: String,
      partialReference: String,
      attributes: Map[String, Any]
  ): String = {
    val resolvedTemplateRoot = ScalateTemplatePaths.normalizeTemplateRoot(templateRoot)
    val partialUri           = ScalateTemplatePaths.partialUriRelativeToRoot(partialReference)
    val engine               = templateEngine(resolvedTemplateRoot, Some(resolvedTemplateRoot))
    val template             = compiledTemplateCache.computeIfAbsent(
      s"$resolvedTemplateRoot:$partialUri",
      _ => engine.load(partialUri)
    )
    ScalateTemplatePaths.normalizeRenderedOutput(
      engine.layout(partialUri, template, toObjectMap(attributes))
    )
  }

  /** Builds a template engine configured exactly like the runtime engine for ahead-of-time precompilation. Reusing this
    * factory guarantees the precompiled class names (driven by `packagePrefix`, bindings and template URI) match what
    * the runtime [[org.fusesource.scalate.TemplateEngine]] resolves.
    */
  def precompilationEngine(templateRoot: String): TemplateEngine = {
    val normalized = ScalateTemplatePaths.normalizeTemplateRoot(templateRoot)
    templateEngine(normalized, Some(normalized))
  }

  private def templateEngine(normalizedTemplateRoot: String, templateRoot: Option[String]): TemplateEngine = {
    val created = new ConfiguredTemplateEngine
    created.allowCaching = true
    created.allowReload = false
    created.escapeMarkup = false
    created.packagePrefix = ScalateTemplatePrecompiler.packagePrefix(normalizedTemplateRoot)
    created.resourceLoader = new PreambleTemplateRootResourceLoader(
      getClass.getClassLoader,
      normalizedTemplateRoot,
      contextBindingPreamble(normalizedTemplateRoot, templateRoot)
    )
    created
  }

  private def contextBindingPreamble(normalizedTemplateRoot: String, templateRoot: Option[String]): String = {
    val _ = templateRoot
    baseContextBindingPreamble +
      readOptionalClasspathTemplate(CommonPreambleClasspath).getOrElse("") +
      readOptionalClasspathTemplate(namingPreambleClasspath(normalizedTemplateRoot)).getOrElse("")
  }

  private def namingPreambleClasspath(templateRoot: String): String = {
    val normalized = templateRoot.stripPrefix("/").stripSuffix("/")
    s"$normalized/fragments/naming/preamble.ssp"
  }

  private def readOptionalClasspathTemplate(resourcePath: String): Option[String] =
    readClasspathTemplateOptional(ScalateTemplatePaths.normalizeResourcePath(resourcePath))

  private def readClasspathTemplateOptional(resourcePath: String): Option[String] =
    Option(getClass.getResourceAsStream(ScalateTemplatePaths.normalizeResourcePath(resourcePath))).map { stream =>
      try
        scala.io.Source.fromInputStream(stream, "UTF-8").mkString
      finally
        stream.close()
    }

  private def inferTemplateRoot(templateClasspath: String): String = {
    val normalized = ScalateTemplatePaths.normalizeTemplateRoot(templateClasspath)
    val segments   = normalized.split("/").toList
    segments match {
      case "python" :: "src" :: feature :: _             =>
        s"python/src/$feature"
      case "templates" :: language :: "src" :: "db" :: _ =>
        s"templates/$language/src/db"
      case _ if segments.length >= 2                     =>
        segments.take(2).mkString("/")
      case _                                             =>
        normalized
    }
  }

  private def readClasspathTemplate(resourcePath: String): String = {
    val stream =
      Option(getClass.getResourceAsStream(resourcePath)).getOrElse {
        throw new IllegalArgumentException(s"SSP template not found on classpath: $resourcePath")
      }
    try
      scala.io.Source.fromInputStream(stream, "UTF-8").mkString.stripTrailing()
    finally
      stream.close()
  }

  private def toObjectMap(attributes: Map[String, Any]): Map[String, Object] =
    attributes.map { case (key, value) =>
      key -> (value match {
        case view: ServiceTemplateView                => view.asInstanceOf[Object]
        case operation: TemplateOperationView         => operation.asInstanceOf[Object]
        case parameter: TemplateParameterView         => parameter.asInstanceOf[Object]
        case member: TemplateMemberView               => member.asInstanceOf[Object]
        case resultField: TemplateResultFieldView     => resultField.asInstanceOf[Object]
        case bindParameter: TemplateBindParameterView => bindParameter.asInstanceOf[Object]
        case factory: TemplateClassRowFactoryView     => factory.asInstanceOf[Object]
        case integrationTest: IntegrationTestView     => integrationTest.asInstanceOf[Object]
        case requirements: ImportRequirements         => requirements.asInstanceOf[Object]
        case nested: Map[?, ?] @unchecked             =>
          nested
            .asInstanceOf[Map[String, Any]]
            .map { case (nestedKey, nestedValue) => nestedKey -> nestedValue.asInstanceOf[Object] }
            .asInstanceOf[Object]
        case items: List[?] @unchecked                =>
          items.map(_.asInstanceOf[Object]).asInstanceOf[Object]
        case other                                    =>
          other.asInstanceOf[Object]
      })
    }
}
