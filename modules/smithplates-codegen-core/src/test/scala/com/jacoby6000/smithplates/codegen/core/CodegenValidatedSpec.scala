package com.jacoby6000.smithplates.codegen.core

import cats.data.NonEmptyList
import cats.data.Validated
import com.jacoby6000.smithplates.codegen.core.CodegenValidated.*
import com.jacoby6000.smithplates.codegen.core.planning.OutputId
import munit.FunSuite

class CodegenValidatedSpec extends FunSuite {
  private val first  = UnknownOutputOverride(OutputId("first"))
  private val second = UnknownOutputOverride(OutputId("second"))

  test("toCodegenEither preserves all validation errors") {
    val errors = NonEmptyList.of(first, second)
    assertEquals(Validated.Invalid(errors).toCodegenEither, Left(errors))
  }

  test("fromEither round-trips toCodegenEither") {
    val errors = NonEmptyList.of(first, second)
    assertEquals(
      CodegenValidated.fromEither(Left(errors)).toCodegenEither,
      Left(errors)
    )
  }

  test("fromEither round-trips successful values") {
    assertEquals(
      CodegenValidated.fromEither(Right("ok")).toCodegenEither,
      Right("ok")
    )
  }
}
