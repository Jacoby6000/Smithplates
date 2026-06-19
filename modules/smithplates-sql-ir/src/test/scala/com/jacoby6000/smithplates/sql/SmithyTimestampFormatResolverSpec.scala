package com.jacoby6000.smithplates.sql

import com.jacoby6000.smithplates.sql.model.*
import munit.FunSuite
import software.amazon.smithy.model.shapes.ShapeId

final class SmithyTimestampFormatResolverSpec extends FunSuite {
  test("member @timestampFormat overrides target shape format") {
    val model  = SmithyTimestampFormatResolverSpec.internal.builder.assemble(
      """use smithy.api#timestampFormat

        |@timestampFormat("epoch-seconds")
        |timestamp TargetEpochSeconds

        |structure MemberOverridesTarget {
        |    @timestampFormat("date-time")
        |    value: TargetEpochSeconds
        |}""".stripMargin
    )
    val member = model
      .expectShape(
        ShapeId.from(SmithyTimestampFormatResolverSpec.internal.builder.structureId("MemberOverridesTarget")))
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
object SmithyTimestampFormatResolverSpec {

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    val builder = SqlTestModelBuilder
  }
}
