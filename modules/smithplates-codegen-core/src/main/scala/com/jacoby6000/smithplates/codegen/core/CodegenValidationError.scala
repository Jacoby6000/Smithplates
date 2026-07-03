package com.jacoby6000.smithplates.codegen.core

import cats.data.NonEmptyList
import com.jacoby6000.smithplates.codegen.core.NeutralType.ModelRef
import com.jacoby6000.smithplates.codegen.core.planning.BindingFilterAtom
import com.jacoby6000.smithplates.codegen.core.planning.OutputId

sealed trait CodegenValidationError {
  def message: String
}

final case class DuplicateId(id: ModelId, models: Int, services: Int, operations: Int) extends CodegenValidationError {
  override def message: String = {
    val parts = List(
      Option.when(models > 0)(s"$models model${if (models == 1) "" else "s"}"),
      Option.when(services > 0)(s"$services service${if (services == 1) "" else "s"}"),
      Option.when(operations > 0)(s"$operations operation${if (operations == 1) "" else "s"}")
    ).flatten
    s"Duplicate id ${id.namespace}#${id.name} (${parts.mkString(", ")})"
  }
}

final case class CyclicAliasDefinition(cycle: NonEmptyList[ModelId]) extends CodegenValidationError {
  override def message: String = {
    val path = cycle.toList.map(id => s"${id.namespace}#${id.name}").mkString(" -> ")
    s"Cyclic alias definition: $path"
  }
}

final case class UnresolvedModelRef(ref: ModelRef, role: String) extends CodegenValidationError {
  override def message: String =
    s"Unresolved model reference ${ref.id.namespace}#${ref.id.name} in $role"
}

final case class InvalidModelMeta(id: ModelId, reason: String) extends CodegenValidationError {
  override def message: String =
    s"Invalid metadata for model ${id.namespace}#${id.name}: $reason"
}

final case class InvalidOperationMeta(id: ModelId, reason: String) extends CodegenValidationError {
  override def message: String =
    s"Invalid metadata for operation ${id.namespace}#${id.name}: $reason"
}

final case class InvalidServiceMeta(id: ModelId, reason: String) extends CodegenValidationError {
  override def message: String =
    s"Invalid metadata for service ${id.namespace}#${id.name}: $reason"
}

final case class InvalidSmithyShape(id: ModelId, reason: String) extends CodegenValidationError {
  override def message: String =
    s"Invalid Smithy shape ${id.namespace}#${id.name}: $reason"
}

final case class InvalidLanguageBaseConfig(reason: String) extends CodegenValidationError {
  override def message: String =
    s"Invalid language base config: $reason"
}

final case class InvalidCodegenOutputConfig(reason: String) extends CodegenValidationError {
  override def message: String =
    s"Invalid codegen output config: $reason"
}

final case class UnknownOutputOverride(overrideId: OutputId) extends CodegenValidationError {
  override def message: String =
    s"Unknown codegen output override id: ${overrideId.value}"
}

final case class SelfOutputOverride(outputId: OutputId) extends CodegenValidationError {
  override def message: String =
    s"Codegen output ${outputId.value} cannot override itself"
}

final case class DuplicateOutputId(id: OutputId) extends CodegenValidationError {
  override def message: String =
    s"Duplicate codegen output id: ${id.value}"
}

final case class InvalidOperationBindingFilter(filter: BindingFilterAtom) extends CodegenValidationError {
  override def message: String =
    filter match {
      case BindingFilterAtom.Kind(kind) =>
        s"Operation bindings do not support kind filter: $kind"
      case other                        =>
        s"Operation bindings do not support filter: $other"
    }
}

final case class InconsistentGroupedModelNamespaces(outputId: OutputId, namespaces: NonEmptyList[String])
    extends CodegenValidationError {
  override def message: String = {
    val ns = namespaces.toList.mkString(", ")
    s"Grouped model output ${outputId.value} spans multiple namespaces: $ns"
  }
}

final case class DuplicateResolvedOutputPath(path: String, outputIds: NonEmptyList[OutputId])
    extends CodegenValidationError {
  override def message: String = {
    val ids = outputIds.toList.map(_.value).mkString(", ")
    s"Duplicate resolved output path '$path' from outputs: $ids"
  }
}

final case class UnresolvedPathPlaceholder(placeholder: String) extends CodegenValidationError {
  override def message: String =
    s"Unresolved path template placeholder: $placeholder"
}

final case class MissingStaticResource(resourcePath: String, outputId: OutputId) extends CodegenValidationError {
  override def message: String =
    s"Missing static resource '$resourcePath' for output ${outputId.value}"
}

final case class TemplateRenderFailed(templatePath: String, reason: String) extends CodegenValidationError {
  override def message: String =
    s"Failed to render template '$templatePath': $reason"
}
