package com.jacoby6000.smithplates.http.service.renderer

import com.jacoby6000.smithplates.codegen.core.Model
import com.jacoby6000.smithplates.codegen.core.ServiceModel
import com.jacoby6000.smithplates.codegen.core.planning.CodegenPlanner
import com.jacoby6000.smithplates.codegen.core.planning.TemplateView
import com.jacoby6000.smithplates.scalate.FilesystemClasspathHybridResourceLoader
import com.jacoby6000.smithplates.scalate.PreambleTemplateRootResourceLoader
import com.jacoby6000.smithplates.scalate.ScalateTemplatePaths
import com.jacoby6000.smithplates.scalate.precompiler.ConfiguredTemplateEngine
import com.jacoby6000.smithplates.scalate.precompiler.ScalateTemplatePrecompiler
import org.fusesource.scalate.Template
import org.fusesource.scalate.TemplateEngine

import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

object ScalateSspTemplateEngine {
  def renderClasspathTemplateAttributes(
      templateClasspath: String,
      attributes: Map[String, Any],
      templateRoot: Option[String]
  ): String = {
    val normalizedTemplatePath = ScalateTemplatePaths.normalizeClasspathUri(templateClasspath)
    val resolvedTemplateRoot   =
      templateRoot
        .map(ScalateTemplatePaths.normalizeTemplateRoot)
        .getOrElse(internal.inferTemplateRoot(normalizedTemplatePath))
    val templateUri            =
      ScalateTemplatePaths.templateUriRelativeToRoot(normalizedTemplatePath, resolvedTemplateRoot)
    val engine                 = internal.templateEngine(resolvedTemplateRoot)
    val template               = internal.compiledTemplateCache.computeIfAbsent(
      s"$resolvedTemplateRoot:$templateUri",
      _ => engine.load(templateUri)
    )
    val renderedBody           =
      ScalateTemplatePaths.normalizeRenderedOutput(
        engine.layout(templateUri, template, internal.toObjectMap(attributes))
      )
    internal.prependGeneratedFileHeader(attributes, resolvedTemplateRoot, renderedBody)
  }

  def renderFilesystemTemplate(
      filesystemTemplatePath: String,
      bundledTemplateRoot: String,
      attributes: Map[String, Any]
  ): String = {
    val normalizedBundledRoot = ScalateTemplatePaths.normalizeTemplateRoot(bundledTemplateRoot)
    val filesystemTemplate    = Paths.get(filesystemTemplatePath)
    val templateUri           = filesystemTemplate.getFileName.toString
    val engine                = internal.filesystemTemplateEngine(
      bundledTemplateRoot = normalizedBundledRoot,
      filesystemTemplate = filesystemTemplate
    )
    val template              = internal.compiledTemplateCache.computeIfAbsent(
      s"file:$filesystemTemplatePath:$normalizedBundledRoot:$templateUri",
      _ => engine.load(templateUri)
    )
    val renderedBody          =
      ScalateTemplatePaths.normalizeRenderedOutput(
        engine.layout(templateUri, template, internal.toObjectMap(attributes))
      )
    internal.prependGeneratedFileHeader(attributes, normalizedBundledRoot, renderedBody)
  }

  def classpathResourceExists(resourcePath: String): Boolean = {
    val normalized = ScalateTemplatePaths.normalizeResourcePath(resourcePath)
    Option(getClass.getResource(normalized)).isDefined
  }

  /** Builds a template engine configured exactly like the runtime engine for ahead-of-time precompilation. Reusing this
    * factory guarantees the precompiled class names (driven by `packagePrefix`, bindings and template URI) match what
    * the runtime [[org.fusesource.scalate.TemplateEngine]] resolves.
    */
  def precompilationEngine(templateRoot: String): TemplateEngine =
    internal.templateEngine(ScalateTemplatePaths.normalizeTemplateRoot(templateRoot))

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    val compiledTemplateCache             = new ConcurrentHashMap[String, Template]()
    val SharedHttpNamingPreambleClasspath = "fragments/naming/preamble.ssp"

    def contextBindingPreamble(normalizedTemplateRoot: String, classLoader: ClassLoader): String = {
      val languageId         = normalizedTemplateRoot.stripPrefix("/").split("/").headOption.getOrElse("")
      val commonPreamble     =
        readClasspathTemplate(classLoader, s"$languageId/src/common/fragments/preamble.ssp").getOrElse("")
      val sharedHttpPreamble =
        if (isHttpCodegenTemplateRoot(normalizedTemplateRoot)) {
          readClasspathTemplate(classLoader, httpNamingPreambleClasspath(normalizedTemplateRoot, languageId)).getOrElse(
            "")
        } else {
          ""
        }
      val featurePreamble    =
        readClasspathTemplate(classLoader, s"$normalizedTemplateRoot/fragments/naming/preamble.ssp")
          .getOrElse("")
      commonPreamble + sharedHttpPreamble + featurePreamble
    }

    def httpNamingPreambleClasspath(normalizedTemplateRoot: String, languageId: String): String = {
      val segments = normalizedTemplateRoot.split("/").toList.filter(_.nonEmpty)
      segments match {
        case _ :: "src" :: "http" :: ("server" | "client" | "models") :: _                =>
          s"$languageId/src/http/fragments/naming/preamble.ssp"
        case "templates" :: _ :: "src" :: "http" :: ("server" | "client" | "models") :: _ =>
          s"templates/$languageId/src/http/fragments/naming/preamble.ssp"
        case _                                                                            =>
          s"$languageId/src/http/fragments/naming/preamble.ssp"
      }
    }

    def isHttpCodegenTemplateRoot(normalizedTemplateRoot: String): Boolean = {
      val segments = normalizedTemplateRoot.split("/").toList.filter(_.nonEmpty)
      segments match {
        case _ :: "src" :: "http" :: ("server" | "client" | "models") :: _ => true
        case _                                                             => false
      }
    }

    def readClasspathTemplate(classLoader: ClassLoader, resourcePath: String): Option[String] =
      readClasspathTemplateOptional(classLoader, ScalateTemplatePaths.normalizeResourcePath(resourcePath))

    def prependGeneratedFileHeader(
        attributes: Map[String, Any],
        templateRoot: String,
        renderedBody: String
    ): String = {
      val _                               = templateRoot
      val trimmedBody                     = renderedBody.stripTrailing()
      val (serviceShapeId, commentPrefix) =
        attributes
          .get("ctx")
          .collect { case view: TemplateView[?, ?] =>
            (templateViewShapeId(view), view.commentPrefix)
          }
          .getOrElse {
            (software.amazon.smithy.model.shapes.ShapeId.from("smithy.api#Unit"), "#")
          }
      if (trimmedBody.contains("Generated by smithplates HTTP codegen")) {
        if (trimmedBody.isEmpty) {
          trimmedBody
        } else {
          s"$trimmedBody\n"
        }
      } else if (trimmedBody.isEmpty) {
        trimmedBody
      } else {
        val header =
          ScalateTemplateHelpers.generatedFileHeader(
            serviceShapeId,
            commentPrefix
          )
        s"$header\n$trimmedBody\n"
      }
    }

    def templateEngine(normalizedTemplateRoot: String): TemplateEngine = {
      val created = new ConfiguredTemplateEngine
      created.allowCaching = true
      created.allowReload = false
      created.escapeMarkup = false
      created.packagePrefix = ScalateTemplatePrecompiler.packagePrefix(normalizedTemplateRoot)
      created.resourceLoader = new PreambleTemplateRootResourceLoader(
        getClass.getClassLoader,
        normalizedTemplateRoot,
        contextBindingPreamble(normalizedTemplateRoot, getClass.getClassLoader)
      )
      created
    }

    def filesystemTemplateEngine(
        bundledTemplateRoot: String,
        filesystemTemplate: java.nio.file.Path
    ): TemplateEngine = {
      val created = new ConfiguredTemplateEngine
      created.allowCaching = true
      created.allowReload = false
      created.escapeMarkup = false
      created.packagePrefix = ScalateTemplatePrecompiler.packagePrefix(bundledTemplateRoot)
      created.resourceLoader = new FilesystemClasspathHybridResourceLoader(
        getClass.getClassLoader,
        bundledTemplateRoot,
        contextBindingPreamble(bundledTemplateRoot, getClass.getClassLoader),
        filesystemTemplate
      )
      created
    }

    def readClasspathTemplateOptional(classLoader: ClassLoader, resourcePath: String): Option[String] =
      Option(classLoader.getResourceAsStream(resourcePath.stripPrefix("/"))).map { stream =>
        try readStream(stream)
        finally stream.close()
      }

    def readStream(stream: java.io.InputStream): String =
      scala.io.Source.fromInputStream(stream, "UTF-8").mkString

    def inferTemplateRoot(templateClasspath: String): String = {
      val normalized = ScalateTemplatePaths.normalizeTemplateRoot(templateClasspath)
      val segments   = normalized.split("/").toList
      segments match {
        case "templates" :: language :: "src" :: "http" :: "server" :: _ =>
          s"templates/$language/src/http/server"
        case "templates" :: language :: "src" :: "http" :: "client" :: _ =>
          s"templates/$language/src/http/client"
        case "templates" :: language :: "src" :: "http" :: "models" :: _ =>
          s"templates/$language/src/http/models"
        case language :: "src" :: "http" :: "server" :: _                =>
          s"$language/src/http/server"
        case language :: "src" :: "http" :: "client" :: _                =>
          s"$language/src/http/client"
        case language :: "src" :: "http" :: "models" :: _                =>
          s"$language/src/http/models"
        case language :: "src" :: feature :: _                           =>
          s"$language/src/$feature"
        case _ if segments.length >= 2                                   =>
          segments.take(2).mkString("/")
        case _                                                           =>
          normalized
      }
    }

    def templateViewShapeId(view: TemplateView[?, ?]): software.amazon.smithy.model.shapes.ShapeId =
      view.subject match {
        case service: ServiceModel[?, ?]                                =>
          software.amazon.smithy.model.shapes.ShapeId.from(s"${service.id.namespace}#${service.id.name}")
        case group: CodegenPlanner.internal.OperationGroupSubject[?, ?] =>
          software.amazon.smithy.model.shapes.ShapeId.from(
            s"${group.service.id.namespace}#${group.service.id.name}"
          )
        case model: Model[?]                                            =>
          software.amazon.smithy.model.shapes.ShapeId.from(s"${model.id.namespace}#${model.id.name}")
        case _                                                          =>
          software.amazon.smithy.model.shapes.ShapeId.from("smithy.api#Unit")
      }

    def toObjectMap(attributes: Map[String, Any]): Map[String, Object] =
      attributes.map { case (key, value) =>
        key -> value.asInstanceOf[Object]
      }
  }
}
