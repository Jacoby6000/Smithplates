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
}
