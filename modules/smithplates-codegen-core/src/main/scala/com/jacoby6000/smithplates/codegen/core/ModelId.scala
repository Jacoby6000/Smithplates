package com.jacoby6000.smithplates.codegen.core

final case class ModelId(namespace: String, name: String)

enum ModelKind {
  case Structure, Union, Enum, Alias
}
