package com.jacoby6000.smithy.stache.sql

import munit.FunSuite
import software.amazon.smithy.model.shapes.ShapeId

final class SmithyTimestampFormatResolverSpec extends FunSuite {
  private val builder = SqlTestModelBuilder

  test("member @timestampFormat overrides target shape format") {
    val model  = builder.assemble(
      """use smithy.api#timestampFormat

        |@timestampFormat("epoch-seconds")
        |timestamp TargetEpochSeconds

        |structure MemberOverridesTarget {
        |    @timestampFormat("date-time")
        |    value: TargetEpochSeconds
        |}""".stripMargin
    )
    val member = model
      .expectShape(ShapeId.from(builder.structureId("MemberOverridesTarget")))
      .asStructureShape
      .get()
      .getMember("value")
      .get()

    assertEquals(
      SmithyTimestampFormatResolver.resolve(model, member),
      Right(SqlTimestampFormat.DateTime)
    )
  }
}
