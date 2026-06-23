package com.jacoby6000.smithplates.codegen.core

import cats.Eq
import cats.Order
import cats.derived.semiauto

final case class ModelId(namespace: String, name: String)

object ModelId {
  given Eq[ModelId] = semiauto.eq

  given Order[ModelId] =
    Order.by(modelId => (modelId.namespace, modelId.name))
}

enum ModelKind {
  case Structure, Union, Enum, Alias
}

object ModelKind {
  given Eq[ModelKind] = semiauto.eq

  given Order[ModelKind] =
    Order.by(_.ordinal)
}
