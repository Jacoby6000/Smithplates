package com.jacoby6000.smithplates.codegen.core.planning

import com.jacoby6000.smithplates.codegen.core.CodegenValidated
import com.jacoby6000.smithplates.codegen.core.CodegenValidated.*
import com.jacoby6000.smithplates.codegen.core.ModelId
import com.jacoby6000.smithplates.codegen.core.UnresolvedPathPlaceholder
import com.jacoby6000.smithplates.codegen.core.strategy.Conventions

final case class PathBindings(bindings: Map[String, String]) {
  def withBinding(key: String, value: String): PathBindings =
    copy(bindings = bindings.updated(key, value))

  def merge(other: PathBindings): PathBindings =
    copy(bindings = bindings ++ other.bindings)
}

object PathBindings {
  val empty: PathBindings = PathBindings(Map.empty)
}

object PathTemplate {
  private val PlaceholderPattern = """\{\{(\w+)\}\}""".r

  def placeholders(pattern: String): Set[String] =
    PlaceholderPattern.findAllMatchIn(pattern).map(_.group(1)).toSet

  def expand(pattern: String, bindings: PathBindings): CodegenValidated[String] = {
    val expanded = PlaceholderPattern.replaceAllIn(
      pattern,
      matched =>
        bindings.bindings.getOrElse(
          matched.group(1),
          matched.matched
        )
    )
    PlaceholderPattern.findFirstIn(expanded) match {
      case Some(unresolved) =>
        UnresolvedPathPlaceholder(unresolved).invalidNel
      case None             =>
        CodegenValidated.valid(expanded)
    }
  }

  def serviceBindings(
      conventions: Conventions,
      serviceId: ModelId,
      serviceVersion: String = ""
  ): PathBindings =
    namespaceBindings(conventions, serviceId.namespace).merge(
      PathBindings(
        Map(
          "serviceName"      -> serviceId.name,
          "serviceClassName" -> conventions.className(serviceId),
          "serviceFileName"  -> conventions.fileName(serviceId),
          "serviceNamespace" -> serviceId.namespace,
          "serviceShapeId"   -> s"${serviceId.namespace}#${serviceId.name}",
          "serviceVersion"   -> serviceVersion
        )
      )
    )

  def namespaceBindings(conventions: Conventions, namespace: String): PathBindings =
    PathBindings(
      Map(
        "packageName"        -> conventions.packageName(namespace),
        "rootNamespaceDir"   -> conventions.rootNamespaceDir,
        "smithyNamespaceDir" -> namespace.split('.').filter(_.nonEmpty).mkString("/")
      )
    )

  def modelBindings(conventions: Conventions, modelId: ModelId): PathBindings =
    namespaceBindings(conventions, modelId.namespace).merge(
      PathBindings(
        Map(
          "modelName"      -> modelId.name,
          "modelClassName" -> conventions.className(modelId),
          "modelFileName"  -> conventions.fileName(modelId),
          "modelNamespace" -> modelId.namespace,
          "modelShapeId"   -> s"${modelId.namespace}#${modelId.name}"
        )
      )
    )

  def operationBindings(conventions: Conventions, operationId: ModelId): PathBindings =
    PathBindings(
      Map(
        "operationName"      -> operationId.name,
        "operationClassName" -> conventions.className(operationId),
        "operationFileName"  -> conventions.fileName(operationId),
        "operationNamespace" -> operationId.namespace,
        "operationShapeId"   -> s"${operationId.namespace}#${operationId.name}"
      )
    )

  def tagBinding(tag: String, conventions: Conventions): PathBindings =
    PathBindings(Map("tagName" -> conventions.memberName(tag)))

}
