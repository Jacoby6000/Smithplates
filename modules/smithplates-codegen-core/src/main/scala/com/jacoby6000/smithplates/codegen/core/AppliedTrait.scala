package com.jacoby6000.smithplates.codegen.core

import cats.Eq
import cats.derived.semiauto

sealed trait SmithyNodeValue

object SmithyNodeValue {
  case object NullValue                                                  extends SmithyNodeValue
  final case class BooleanValue(value: Boolean)                          extends SmithyNodeValue
  final case class StringValue(value: String)                            extends SmithyNodeValue
  final case class NumberValue(canonicalValue: String)                   extends SmithyNodeValue
  final case class ArrayValue(values: List[SmithyNodeValue])             extends SmithyNodeValue
  final case class ObjectValue(members: List[(String, SmithyNodeValue)]) extends SmithyNodeValue

  given Eq[SmithyNodeValue] = semiauto.eq
}

final case class AppliedTrait(id: ModelId, value: SmithyNodeValue, synthetic: Boolean)

object AppliedTrait {
  given Eq[AppliedTrait] = semiauto.eq
}
