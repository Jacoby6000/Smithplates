package com.jacoby6000.smithplates.codegen.core

import com.jacoby6000.smithplates.codegen.core.NeutralType.*
import munit.FunSuite

class NeutralTypeSpec extends FunSuite {
  test("optional - wraps a non-optional type") {
    assertEquals(NeutralType.optional(StringT), OptionalT(StringT))
  }

  test("optional - collapses a single nested optional") {
    assertEquals(NeutralType.optional(OptionalT(StringT)), OptionalT(StringT))
  }

  test("optional - collapses arbitrarily deep nested optionals") {
    assertEquals(NeutralType.optional(OptionalT(OptionalT(OptionalT(IntegerT)))), OptionalT(IntegerT))
  }

  test("optional - preserves inner composite structure") {
    assertEquals(NeutralType.optional(ListT(OptionalT(StringT))), OptionalT(ListT(OptionalT(StringT))))
  }

  test("optional - wraps map and timestamp types") {
    val map       = MapT(StringT, IntegerT)
    assertEquals(NeutralType.optional(map), OptionalT(map))
    val timestamp = TimestampT(TimestampFormat.EpochSeconds)
    assertEquals(NeutralType.optional(timestamp), OptionalT(timestamp))
  }

  test("optional - wraps model refs") {
    val ref = ModelRef(ModelId("ns", "Thing"))
    assertEquals(NeutralType.optional(ref), OptionalT(ref))
  }
}
