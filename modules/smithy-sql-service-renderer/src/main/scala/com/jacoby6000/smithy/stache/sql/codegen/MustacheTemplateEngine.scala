package com.jacoby6000.smithy.stache.sql.codegen

import org.fusesource.scalate.Template
import org.fusesource.scalate.TemplateEngine

import java.io.OutputStream
import java.io.PrintStream
import java.util.concurrent.ConcurrentHashMap
import scala.util.matching.Regex

object MustacheTemplateEngine {
  private val engine = {
    val created = new TemplateEngine
    created.allowCaching = true
    created.allowReload = false
    created
  }

  private val compiledTemplateCache          = new ConcurrentHashMap[String, Template]()
  private val partialReferencePattern: Regex = """\{\{>\s*([^#^/][^}]*)\s*\}\}""".r

  def classpathResourceExists(resourcePath: String): Boolean = {
    val normalized = normalizeResourcePath(resourcePath)
    Option(getClass.getResource(normalized)).isDefined
  }

  def renderClasspathTemplate(
      templateClasspath: String,
      attributes: Map[String, Any],
      templateRoot: Option[String] = None
  ): String = {
    val normalizedTemplatePath = normalizeClasspathUri(templateClasspath)
    val resolvedTemplateRoot   =
      templateRoot
        .map(normalizeTemplateRoot)
        .getOrElse(inferTemplateRoot(normalizedTemplatePath))
    val resourcePath           = normalizeResourcePath(normalizedTemplatePath)
    val templateText           =
      expandPartials(readClasspathTemplate(resourcePath), resolvedTemplateRoot, Set.empty)
    val template               = compiledTemplateCache.computeIfAbsent(
      s"$resolvedTemplateRoot:$resourcePath:$templateText",
      _ => compileMoustacheTemplate(templateText)
    )
    normalizeRenderedOutput(
      engine.layout("inline", template, toObjectMap(withDefaultAttributes(attributes)))
    )
  }

  def renderString(
      template: String,
      attributes: Map[String, Any],
      templateRoot: Option[String] = None
  ): String = {
    val resolvedTemplateRoot =
      templateRoot
        .map(normalizeTemplateRoot)
        .getOrElse("")
    val expandedTemplate     =
      if (resolvedTemplateRoot.isEmpty) {
        template
      } else {
        expandPartials(template, resolvedTemplateRoot, Set.empty)
      }
    val compiled             =
      compiledTemplateCache.computeIfAbsent(
        s"$resolvedTemplateRoot:inline:$expandedTemplate",
        _ => compileMoustacheTemplate(expandedTemplate)
      )
    normalizeRenderedOutput(
      engine.layout("inline", compiled, toObjectMap(withDefaultAttributes(attributes)))
    )
  }

  def renderClasspathPartial(
      templateRoot: String,
      partialReference: String,
      attributes: Map[String, Any]
  ): String = {
    val resolvedTemplateRoot = normalizeTemplateRoot(templateRoot)
    val partialPath          = partialResourcePath(resolvedTemplateRoot, partialReference)
    val templateText         =
      expandPartials(readClasspathTemplate(partialPath), resolvedTemplateRoot, Set(partialReference))
    renderString(templateText, attributes, Some(resolvedTemplateRoot))
  }

  private def normalizeClasspathUri(templateClasspath: String): String =
    templateClasspath.stripPrefix("classpath:")

  private def normalizeTemplateRoot(templateRoot: String): String =
    templateRoot.stripPrefix("classpath:").stripPrefix("/").stripSuffix("/")

  private def inferTemplateRoot(templateClasspath: String): String = {
    val normalized = normalizeTemplateRoot(templateClasspath)
    val segments   = normalized.split("/").toList
    segments match {
      case _ if segments.length >= 2 => segments.take(2).mkString("/")
      case _                         => normalized
    }
  }

  private def normalizeResourcePath(resourcePath: String): String =
    if (resourcePath.startsWith("/")) {
      resourcePath
    } else {
      s"/$resourcePath"
    }

  private def partialResourcePath(templateRoot: String, partialReference: String): String = {
    val normalizedReference =
      if (partialReference.endsWith(".mustache")) {
        partialReference
      } else {
        s"$partialReference.mustache"
      }
    normalizeResourcePath(s"$templateRoot/$normalizedReference")
  }

  private def expandPartials(
      templateText: String,
      templateRoot: String,
      stack: Set[String]
  ): String =
    partialReferencePattern.replaceAllIn(
      templateText,
      { matchResult =>
        val partialReference = matchResult.group(1).trim
        if (stack.contains(partialReference)) {
          throw new IllegalArgumentException(s"Circular Mustache partial reference: $partialReference")
        }
        val partialPath      = partialResourcePath(templateRoot, partialReference)
        expandPartials(
          readClasspathTemplate(partialPath),
          templateRoot,
          stack + partialReference
        )
      }
    )

  private def readClasspathTemplate(resourcePath: String): String = {
    val stream =
      Option(getClass.getResourceAsStream(resourcePath)).getOrElse {
        throw new IllegalArgumentException(s"Mustache template not found on classpath: $resourcePath")
      }
    try
      scala.io.Source.fromInputStream(stream, "UTF-8").mkString.stripTrailing()
    finally
      stream.close()
  }

  private def compileMoustacheTemplate(templateText: String): Template =
    suppressCompilerOutput {
      engine.compileMoustache(templateText)
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

  private def withDefaultAttributes(attributes: Map[String, Any]): Map[String, Any] =
    if (attributes.contains("newline")) {
      attributes
    } else {
      attributes + ("newline" -> "\n")
    }

  private def toObjectMap(attributes: Map[String, Any]): Map[String, Object] =
    attributes.map { case (key, value) =>
      key -> (value match {
        case nested: Map[?, ?] @unchecked =>
          nested
            .asInstanceOf[Map[String, Any]]
            .map { case (nestedKey, nestedValue) => nestedKey -> nestedValue.asInstanceOf[Object] }
            .asInstanceOf[Object]
        case items: List[?] @unchecked    =>
          items
            .map {
              case item: Map[?, ?] @unchecked =>
                item
                  .asInstanceOf[Map[String, Any]]
                  .map { case (itemKey, itemValue) => itemKey -> itemValue.asInstanceOf[Object] }
                  .asInstanceOf[Object]
              case other                      =>
                other.asInstanceOf[Object]
            }
            .asInstanceOf[Object]
        case other                        =>
          other.asInstanceOf[Object]
      })
    }
}
