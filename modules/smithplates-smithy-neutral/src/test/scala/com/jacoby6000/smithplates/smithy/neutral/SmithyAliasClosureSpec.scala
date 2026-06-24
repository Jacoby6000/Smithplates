package com.jacoby6000.smithplates.smithy.neutral

import munit.FunSuite
import software.amazon.smithy.model.shapes.ShapeId

class SmithyAliasClosureSpec extends FunSuite {
  test("aliasShapeIds discovers string aliases only reachable through list members") {
    val model = SmithyTestModels.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |string Tag
          |
          |list Tags {
          |    member: Tag
          |}
          |
          |structure Post {
          |    @required
          |    tags: Tags
          |}
          |""".stripMargin
    )

    val aliasIds = SmithyAliasClosure.aliasShapeIds(model, List(ShapeId.from("example#Post")))
    assertEquals(aliasIds, List(ShapeId.from("example#Tag")))
  }

  test("aliasShapeIds discovers primitive aliases reachable through map values") {
    val model = SmithyTestModels.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |integer Count
          |
          |map Counts {
          |    key: String
          |    value: Count
          |}
          |
          |structure Metrics {
          |    @required
          |    totals: Counts
          |}
          |""".stripMargin
    )

    val aliasIds = SmithyAliasClosure.aliasShapeIds(model, List(ShapeId.from("example#Metrics")))
    assertEquals(aliasIds, List(ShapeId.from("example#Count")))
  }

  test("aliasShapeIds ignores string enums") {
    val model = SmithyTestModels.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |enum Status {
          |    OPEN = "open"
          |}
          |
          |structure Payload {
          |    status: Status
          |}
          |""".stripMargin
    )

    val aliasIds = SmithyAliasClosure.aliasShapeIds(model, List(ShapeId.from("example#Payload")))
    assertEquals(aliasIds, Nil)
  }

  test("aliasShapeIds ignores int enums") {
    val model = SmithyTestModels.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |intEnum Priority {
          |    LOW = 1
          |}
          |
          |structure Payload {
          |    priority: Priority
          |}
          |""".stripMargin
    )

    val aliasIds = SmithyAliasClosure.aliasShapeIds(model, List(ShapeId.from("example#Payload")))
    assertEquals(aliasIds, Nil)
  }
}
