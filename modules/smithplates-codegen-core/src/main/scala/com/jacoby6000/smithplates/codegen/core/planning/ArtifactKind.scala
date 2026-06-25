package com.jacoby6000.smithplates.codegen.core.planning

import cats.Eq
import cats.Order

enum ArtifactKind {
  case Src, Test
}

object ArtifactKind {
  given Eq[ArtifactKind]    = Eq.fromUniversalEquals
  given Order[ArtifactKind] = Order.by(_.ordinal)
}
