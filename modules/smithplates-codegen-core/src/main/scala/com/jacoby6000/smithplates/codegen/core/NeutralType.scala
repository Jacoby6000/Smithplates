package com.jacoby6000.smithplates.codegen.core

import cats.Eq
import cats.Order
import cats.derived.semiauto

enum TimestampFormat {
  case DateTime, EpochSeconds
}

object TimestampFormat {
  given Eq[TimestampFormat] = semiauto.eq
}

sealed trait NeutralType

object NeutralType {
  case object BooleanT    extends NeutralType
  case object IntegerT    extends NeutralType
  case object LongT       extends NeutralType
  case object BigIntegerT extends NeutralType
  case object FloatT      extends NeutralType
  case object DoubleT     extends NeutralType
  case object BigDecimalT extends NeutralType
  case object StringT     extends NeutralType
  case object BytesT      extends NeutralType
  case object DocumentT   extends NeutralType

  final case class TimestampT(format: TimestampFormat) extends NeutralType

  final case class ListT(element: NeutralType)                extends NeutralType
  final case class MapT(key: NeutralType, value: NeutralType) extends NeutralType
  final case class OptionalT(inner: NeutralType)              extends NeutralType

  final case class ModelRef(id: ModelId) extends NeutralType

  /** Smart constructor for optional types. `OptionalT` is the only optionality mechanism, and nested optionals carry no
    * additional meaning, so this collapses any leading `OptionalT` layers: `optional(OptionalT(x)) == OptionalT(x)`.
    */
  def optional(inner: NeutralType): NeutralType =
    inner match {
      case OptionalT(deeper) => optional(deeper)
      case other             => OptionalT(other)
    }

  given Eq[NeutralType] = semiauto.eq

  given Order[ModelRef] =
    Order.by(_.id)
}
