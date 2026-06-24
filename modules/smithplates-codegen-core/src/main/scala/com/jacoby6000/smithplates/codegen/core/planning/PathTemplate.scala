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
  ): PathBindings = {
    val namespaceDir = serviceId.namespace.split('.').filter(_.nonEmpty).mkString("/")
    PathBindings(
      Map(
        "serviceName"        -> serviceId.name,
        "serviceClassName"   -> conventions.className(serviceId),
        "serviceFileName"    -> conventions.memberName(serviceId.name),
        "serviceNamespace"   -> serviceId.namespace,
        "serviceShapeId"     -> s"${serviceId.namespace}#${serviceId.name}",
        "serviceVersion"     -> serviceVersion,
        "packageName"        -> conventions.packageName(serviceId.namespace),
        "rootNamespaceDir"   -> conventions.rootNamespaceDir,
        "smithyNamespaceDir" -> namespaceDir
      )
    )
  }

  def modelBindings(conventions: Conventions, modelId: ModelId): PathBindings =
    PathBindings(
      Map(
        "modelName"          -> modelId.name,
        "modelClassName"     -> conventions.className(modelId),
        "modelFileName"      -> conventions.memberName(modelId.name),
        "modelNamespace"     -> modelId.namespace,
        "modelShapeId"       -> s"${modelId.namespace}#${modelId.name}",
        "rootNamespaceDir"   -> conventions.rootNamespaceDir,
        "smithyNamespaceDir" -> modelId.namespace.split('.').filter(_.nonEmpty).mkString("/")
      )
    )

  def operationBindings(conventions: Conventions, operationId: ModelId): PathBindings =
    PathBindings(
      Map(
        "operationName"      -> operationId.name,
        "operationClassName" -> conventions.className(operationId),
        "operationFileName"  -> conventions.memberName(operationId.name),
        "operationNamespace" -> operationId.namespace,
        "operationShapeId"   -> s"${operationId.namespace}#${operationId.name}"
      )
    )

  def tagBinding(tag: String, conventions: Conventions): PathBindings =
    PathBindings(Map("tagName" -> conventions.memberName(tag)))

}
