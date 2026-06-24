package com.jacoby6000.smithplates.codegen.core.planning

import cats.Eq

opaque type OutputId = String

object OutputId {
  def apply(value: String): OutputId = value

  extension (id: OutputId) {
    def value: String = id
  }

  given Eq[OutputId] =
    Eq.instance((left, right) => (left: String) == (right: String))
}
