package com.jacoby6000.smithplates.smithy.neutral

import com.jacoby6000.smithplates.codegen.core.NeutralType
import com.jacoby6000.smithplates.codegen.core.NeutralType.*
import com.jacoby6000.smithplates.codegen.core.TimestampFormat
import munit.Assertions

/** Compares core [[NeutralType]] members to legacy string-IR type names and separate optionality flags. */
object LegacyNeutralTypeEquivalence extends Assertions {
  def isOptional(tpe: NeutralType): Boolean =
    tpe match {
      case OptionalT(_) => true
      case _            => false
    }

  def unwrapOptional(tpe: NeutralType): NeutralType =
    tpe match {
      case OptionalT(inner) => unwrapOptional(inner)
      case other            => other
    }

  def legacyTypeName(tpe: NeutralType): String =
    unwrapOptional(tpe) match {
      case BooleanT               => "Boolean"
      case IntegerT               => "Integer"
      case LongT                  => "Long"
      case FloatT                 => "Float"
      case DoubleT                => "Double"
      case BigDecimalT            => "BigDecimal"
      case BigIntegerT            => "BigInteger"
      case StringT                => "String"
      case BytesT                 => "Blob"
      case DocumentT              => "Document"
      case TimestampT(_)          => "Timestamp"
      case ListT(OptionalT(elem)) => s"List[${legacyTypeName(elem)}]"
      case ListT(elem)            => s"List[${legacyTypeName(elem)}]"
      case MapT(StringT, value)   => s"Map[String, ${legacyTypeName(value)}]"
      case MapT(_, value)         => s"Map[String, ${legacyTypeName(value)}]"
      case ModelRef(id)           => id.name
      case OptionalT(_)           => fail("unexpected nested OptionalT after unwrap")
    }

  def timestampFormat(tpe: NeutralType): Option[TimestampFormat] =
    unwrapOptional(tpe) match {
      case TimestampT(format) => Some(format)
      case _                  => None
    }

  def assertEquivalent(
      legacyTypeName: String,
      legacyOptional: Boolean,
      coreType: NeutralType,
      legacyTimestampFormat: Option[TimestampFormat] = None
  ): Unit =
    assertEquivalentWithAliases(legacyTypeName, legacyOptional, coreType, Nil, legacyTimestampFormat)

  def assertEquivalentWithAliases(
      legacyTypeName: String,
      legacyOptional: Boolean,
      coreType: NeutralType,
      aliases: List[com.jacoby6000.smithplates.codegen.core.Model.Alias[?]],
      legacyTimestampFormat: Option[TimestampFormat] = None
  ): Unit = {
    assertEquals(isOptional(coreType), legacyOptional, s"type name mismatch for optional flag on $legacyTypeName")
    unwrapOptional(coreType) match {
      case ModelRef(id) if legacyTypeName == "String" =>
        aliases.find(_.id == id).getOrElse(fail(s"expected string alias for ${id.name}")) match {
          case alias if alias.underlying == StringT => ()
          case alias                                => fail(s"expected String alias underlying, got ${alias.underlying}")
        }
      case inner                                      =>
        assertEquals(LegacyNeutralTypeEquivalence.legacyTypeName(inner), legacyTypeName)
    }
    legacyTimestampFormat.foreach { expectedFormat =>
      assertEquals(timestampFormat(coreType), Some(expectedFormat))
    }
  }
}
