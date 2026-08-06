package com.jacoby6000.smithplates.smithy.neutral

import com.jacoby6000.smithplates.codegen.core.ModelId
import com.jacoby6000.smithplates.codegen.core.SmithyNodeValue
import munit.FunSuite
import software.amazon.smithy.model.shapes.ShapeId

class SmithyAppliedTraitsSpec extends FunSuite {
  test("fromShape preserves arbitrary effective trait values deterministically") {
    val model  = SmithyTestModels.assemble(
      "traits.smithy" ->
        """$version: "2.0"
          |namespace example.traits
          |
          |use smithy.api#trait
          |
          |@trait(selector: "*")
          |structure data {
          |    value: Document
          |}
          |
          |@trait(selector: "*")
          |structure marker {}
          |""".stripMargin,
      "model.smithy"  ->
        """$version: "2.0"
          |namespace example
          |
          |use example.traits#data
          |use example.traits#marker
          |use smithy.api#mixin
          |
          |@mixin
          |structure Common {
          |    @marker
          |    inherited: String
          |}
          |
          |structure Target with [Common] {}
          |
          |apply Target @data(value: {
          |    z: null,
          |    enabled: true,
          |    label: "example",
          |    ratio: 123.450,
          |    items: [1, false, "last"],
          |    nested: {b: 2, a: 1}
          |})
          |""".stripMargin
    )
    val target = model.expectShape(ShapeId.from("example#Target"))

    val data = SmithyAppliedTraits.fromShape(target).find(_.id == ModelId("example.traits", "data"))
    assertEquals(
      data.map(_.value),
      Some(
        SmithyNodeValue.ObjectValue(
          List(
            "value" -> SmithyNodeValue.ObjectValue(
              List(
                "enabled" -> SmithyNodeValue.BooleanValue(true),
                "items"   -> SmithyNodeValue.ArrayValue(
                  List(
                    SmithyNodeValue.NumberValue("1"),
                    SmithyNodeValue.BooleanValue(false),
                    SmithyNodeValue.StringValue("last")
                  )
                ),
                "label"   -> SmithyNodeValue.StringValue("example"),
                "nested"  -> SmithyNodeValue.ObjectValue(
                  List(
                    "a" -> SmithyNodeValue.NumberValue("1"),
                    "b" -> SmithyNodeValue.NumberValue("2")
                  )
                ),
                "ratio"   -> SmithyNodeValue.NumberValue("123.45"),
                "z"       -> SmithyNodeValue.NullValue
              )
            )
          )
        )
      )
    )
    assertEquals(data.map(_.synthetic), Some(false))
    assertEquals(
      SmithyAppliedTraits.fromShape(target).map(_.id),
      SmithyAppliedTraits.fromShape(target).map(_.id).sortBy(id => (id.namespace, id.name))
    )

    val inherited = target.asStructureShape().get().getMember("inherited").get()
    assert(
      SmithyAppliedTraits.fromShape(inherited).exists(_.id == ModelId("example.traits", "marker")),
      "member traits inherited through a mixin must be visible"
    )
  }

  test("fromShape preserves annotation traits as empty objects") {
    val model  = SmithyTestModels.assemble(
      "annotation.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithy.api#trait
          |
          |@trait(selector: "*")
          |structure marker {}
          |
          |@marker
          |structure Target {}
          |""".stripMargin
    )
    val target = model.expectShape(ShapeId.from("example#Target"))

    val marker = SmithyAppliedTraits.fromShape(target).find(_.id == ModelId("example", "marker"))

    assertEquals(marker.map(_.value), Some(SmithyNodeValue.ObjectValue(Nil)))
  }
}
