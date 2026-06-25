package com.jacoby6000.smithplates.codegen.core.planning

import cats.Eq
import cats.derived.semiauto

sealed trait CodegenOutput {
  def id: OutputId
  def kind: ArtifactKind
  def overrides: Option[OutputId]
}

object CodegenOutput {
  final case class CodegenTemplateBindingOutput(
      id: OutputId,
      kind: ArtifactKind,
      templatePath: String,
      outputPath: String,
      binding: SmithyBinding,
      overrides: Option[OutputId] = None
  ) extends CodegenOutput

  final case class CodegenStaticOutput(
      id: OutputId,
      kind: ArtifactKind,
      filePath: String,
      copyToPath: String,
      overrides: Option[OutputId] = None
  ) extends CodegenOutput

  given Eq[CodegenOutput] = semiauto.eq
}

final case class ResolvedArtifact(relativePath: String, content: String, kind: ArtifactKind)

object ResolvedArtifact {
  given Eq[ResolvedArtifact] = semiauto.eq
}
