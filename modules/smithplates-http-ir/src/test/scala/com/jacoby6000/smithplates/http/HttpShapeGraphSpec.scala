package com.jacoby6000.smithplates.http

import munit.FunSuite
import software.amazon.smithy.model.shapes.ShapeId

class HttpShapeGraphSpec extends FunSuite {
  test("HttpShapeGraph discovers unions referenced only through list members") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |structure EventListOutput {
          |    @required
          |    items: EventListItems
          |}
          |
          |list EventListItems {
          |    member: Event
          |}
          |
          |union Event {
          |    created: EventCreated
          |    deleted: EventDeleted
          |}
          |
          |structure EventCreated {
          |    @required
          |    eventId: String
          |}
          |
          |structure EventDeleted {
          |    @required
          |    eventId: String
          |}
          |""".stripMargin
    )

    val (structures, unions) =
      HttpShapeGraph.referencedShapes(model, List(ShapeId.from("example#EventListOutput")))

    assertEquals(unions, List(ShapeId.from("example#Event")))
    assertEquals(
      structures.toSet,
      Set(
        ShapeId.from("example#EventListOutput"),
        ShapeId.from("example#EventCreated"),
        ShapeId.from("example#EventDeleted")
      )
    )
  }

  test("HttpShapeGraph discovers unions referenced only through map values") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |structure EventMapOutput {
          |    @required
          |    items: EventMap
          |}
          |
          |map EventMap {
          |    key: String
          |    value: Event
          |}
          |
          |union Event {
          |    created: EventCreated
          |}
          |
          |structure EventCreated {
          |    @required
          |    eventId: String
          |}
          |""".stripMargin
    )

    val (_, unions) =
      HttpShapeGraph.referencedShapes(model, List(ShapeId.from("example#EventMapOutput")))

    assertEquals(unions, List(ShapeId.from("example#Event")))
  }
}
