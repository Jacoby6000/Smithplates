package com.jacoby6000.smithplates.http.service.renderer

final case class HttpCodegenArtifact(
    relativePath: String,
    content: String,
    kind: HttpServiceCodegenArtifactKind
)

enum HttpServiceCodegenArtifactKind {
  case Src
  case Test
}
