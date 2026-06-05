package com.jacoby6000.smithy.stache.sql.codegen

import org.fusesource.scalate.Template
import org.fusesource.scalate.TemplateEngine

import java.io.OutputStream
import java.io.PrintStream
import java.util.concurrent.ConcurrentHashMap

object MustacheTemplateEngine {
  private val engine = {
    val created = new TemplateEngine
    created.allowCaching = true
    created.allowReload = false
    created
  }

  private val compiledTemplateCache = new ConcurrentHashMap[String, Template]()

  def classpathResourceExists(resourcePath: String): Boolean = {
    val normalized =
      if (resourcePath.startsWith("/")) {
        resourcePath
      } else {
        s"/$resourcePath"
      }
    Option(getClass.getResource(normalized)).isDefined
  }

  def renderClasspathTemplate(templateClasspath: String, attributes: Map[String, Any]): String = {
    val normalized   = templateClasspath.stripPrefix("classpath:")
    val resourcePath =
      if (normalized.startsWith("/")) {
        normalized
      } else {
        s"/$normalized"
      }
    val template     = compiledTemplateCache.computeIfAbsent(
      resourcePath,
      _ => compileMoustacheTemplate(readClasspathTemplate(resourcePath))
    )
    engine.layout("inline", template, toObjectMap(attributes))
  }

  def renderString(template: String, attributes: Map[String, Any]): String = {
    val compiled =
      compiledTemplateCache.computeIfAbsent(
        s"inline:$template",
        _ => compileMoustacheTemplate(template)
      )
    engine.layout("inline", compiled, toObjectMap(attributes))
  }

  private def readClasspathTemplate(resourcePath: String): String = {
    val stream =
      Option(getClass.getResourceAsStream(resourcePath)).getOrElse {
        throw new IllegalArgumentException(s"Mustache template not found on classpath: $resourcePath")
      }
    try
      scala.io.Source.fromInputStream(stream, "UTF-8").mkString
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
