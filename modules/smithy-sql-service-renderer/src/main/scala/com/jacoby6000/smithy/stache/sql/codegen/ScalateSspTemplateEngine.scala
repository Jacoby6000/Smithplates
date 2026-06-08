package com.jacoby6000.smithy.stache.sql.codegen

import org.fusesource.scalate.Template
import org.fusesource.scalate.TemplateEngine

import java.io.OutputStream
import java.io.PrintStream
import java.util.concurrent.ConcurrentHashMap

object ScalateSspTemplateEngine {
  private val compiledTemplateCache      = new ConcurrentHashMap[String, Template]()
  private val baseContextBindingPreamble =
    """<% def isTruthy(value: Any): Boolean = com.jacoby6000.smithy.stache.sql.codegen.ScalateTemplateHelpers.isTruthy(value) %>
"""

  def readClasspathResource(resourceClasspath: String): String = {
    val resourcePath = normalizeResourcePath(normalizeClasspathUri(resourceClasspath))
    normalizeRenderedOutput(readClasspathTemplate(resourcePath))
  }

  def classpathResourceExists(resourcePath: String): Boolean = {
    val normalized = normalizeResourcePath(resourcePath)
    Option(getClass.getResource(normalized)).isDefined
  }

  def renderClasspathTemplate(
      templateClasspath: String,
      view: ServiceTemplateView,
      templateRoot: Option[String] = None
  ): String = {
    val normalizedTemplatePath = normalizeClasspathUri(templateClasspath)
    val resolvedTemplateRoot   =
      templateRoot
        .map(normalizeTemplateRoot)
        .getOrElse(inferTemplateRoot(normalizedTemplatePath))
    val templateUri            = templateUriRelativeToRoot(normalizedTemplatePath, resolvedTemplateRoot)
    val engine                 = templateEngine(resolvedTemplateRoot, Some(resolvedTemplateRoot))
    val template               = compiledTemplateCache.computeIfAbsent(
      s"$resolvedTemplateRoot:$templateUri",
      _ =>
        suppressCompilerOutput {
          engine.load(templateUri)
        }
    )
    val renderedBody           =
      normalizeRenderedOutput(
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
    val resolvedTemplateRoot = normalizeTemplateRoot(templateRoot)
    val partialUri           = partialUriRelativeToRoot(partialReference)
    val engine               = templateEngine(resolvedTemplateRoot, Some(resolvedTemplateRoot))
    val template             = compiledTemplateCache.computeIfAbsent(
      s"$resolvedTemplateRoot:$partialUri",
      _ =>
        suppressCompilerOutput {
          engine.load(partialUri)
        }
    )
    normalizeRenderedOutput(
      engine.layout(partialUri, template, toObjectMap(attributes))
    )
  }

  private def templateEngine(normalizedTemplateRoot: String, templateRoot: Option[String]): TemplateEngine = {
    val created = new TemplateEngine
    created.allowCaching = true
    created.allowReload = false
    created.escapeMarkup = false
    created.resourceLoader = new PreambleTemplateRootResourceLoader(
      getClass.getClassLoader,
      normalizedTemplateRoot,
      contextBindingPreamble(normalizedTemplateRoot, templateRoot)
    )
    created
  }

  private def contextBindingPreamble(normalizedTemplateRoot: String, templateRoot: Option[String]): String = {
    val root               = templateRoot.map(normalizeTemplateRoot).getOrElse(normalizedTemplateRoot)
    val namingPreamblePath =
      if (root.isEmpty) {
        "/fragments/naming/preamble.ssp"
      } else {
        s"/$root/fragments/naming/preamble.ssp"
      }
    baseContextBindingPreamble + readClasspathTemplateOptional(namingPreamblePath).getOrElse("")
  }

  private def readClasspathTemplateOptional(resourcePath: String): Option[String] =
    Option(getClass.getResourceAsStream(normalizeResourcePath(resourcePath))).map { stream =>
      try
        scala.io.Source.fromInputStream(stream, "UTF-8").mkString
      finally
        stream.close()
    }

  private def normalizeClasspathUri(templateClasspath: String): String =
    templateClasspath.stripPrefix("classpath:")

  private def normalizeTemplateRoot(templateRoot: String): String =
    templateRoot.stripPrefix("classpath:").stripPrefix("/").stripSuffix("/")

  private def inferTemplateRoot(templateClasspath: String): String = {
    val normalized = normalizeTemplateRoot(templateClasspath)
    val segments   = normalized.split("/").toList
    segments match {
      case "language-templates" :: language :: "src" :: "db" :: _ =>
        s"language-templates/$language/src/db"
      case _ if segments.length >= 2                              =>
        segments.take(2).mkString("/")
      case _                                                      =>
        normalized
    }
  }

  private def templateUriRelativeToRoot(templateClasspath: String, templateRoot: String): String = {
    val normalizedTemplate = normalizeTemplateRoot(templateClasspath)
    val root               = normalizeTemplateRoot(templateRoot)
    if (normalizedTemplate.startsWith(s"$root/")) {
      normalizedTemplate.stripPrefix(s"$root/")
    } else {
      normalizedTemplate
    }
  }

  private def partialUriRelativeToRoot(partialReference: String): String = {
    val normalizedReference =
      if (partialReference.endsWith(".ssp")) {
        partialReference
      } else {
        s"$partialReference.ssp"
      }
    normalizedReference.stripPrefix("/")
  }

  private def normalizeResourcePath(resourcePath: String): String =
    if (resourcePath.startsWith("/")) {
      resourcePath
    } else {
      s"/$resourcePath"
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

  private def suppressCompilerOutput[T](action: => T): T = {
    val previousErr = System.err
    try {
      System.setErr(new PrintStream(OutputStream.nullOutputStream()))
      action
    } finally System.setErr(previousErr)
  }

  private def normalizeRenderedOutput(output: String): String = {
    val trimmed = output.stripTrailing()
    if (trimmed.isEmpty) {
      trimmed
    } else {
      s"$trimmed\n"
    }
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
