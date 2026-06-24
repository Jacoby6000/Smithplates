package com.jacoby6000.smithplates.codegen.core

import cats.data.NonEmptyList
import com.jacoby6000.smithplates.codegen.core.NeutralType.ModelRef

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
