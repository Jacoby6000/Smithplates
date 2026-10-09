package com.jacoby6000.smithplates.sql

import munit.FunSuite
import software.amazon.smithy.model.shapes.ShapeId

class SqlShapeGraphSpec extends FunSuite {
  test("SqlShapeIrExtractor closes nested collections and transitively referenced unions") {
    val model = SqlTestModelBuilder.assemble(
      """
        |structure Root { @required values: OuterList }
        |list OuterList { member: ValueMap }
        |map ValueMap { key: String, value: OuterChoice }
        |union OuterChoice { text: String, nested: InnerChoice }
        |union InnerChoice { text: String, value: Leaf }
        |structure Leaf { @required text: String }
        |""".stripMargin
    )
    val (structures, unions) = SqlShapeGraph.referencedShapes(model, List(ShapeId.from("example#Root")))
    assertEquals(structures.map(_.getName).toSet, Set("Root", "Leaf"))
    assertEquals(unions.map(_.getName).toSet, Set("OuterChoice", "InnerChoice"))
    val extracted = SqlShapeIrExtractor.extract(model, List(ShapeId.from("example#Root"))).toOption.get
    assertEquals(extracted.structures.map(_.name).toSet, Set("Root", "Leaf"))
    assertEquals(extracted.unions.map(_.name).toSet, Set("OuterChoice", "InnerChoice"))
  }

  test("SqlShapeGraph discovers union member structures through referencedShapes") {
    val model = SqlTestModelBuilder.assemble(
      """
        |structure ShipmentOutput {
        |    @required
        |    state: DeliveryState
        |}
        |
        |union DeliveryState {
        |    pending: String
        |    delivered: PostalAddress
        |}
        |
        |structure PostalAddress {
        |    @required
        |    street: String
        |
        |    @required
        |    city: String
        |}
        |""".stripMargin
    )

    val (structures, unions) =
      SqlShapeGraph.referencedShapes(model, List(ShapeId.from("example#ShipmentOutput")))

    assertEquals(unions, List(ShapeId.from("example#DeliveryState")))
    assertEquals(
      structures.toSet,
      Set(
        ShapeId.from("example#ShipmentOutput"),
        ShapeId.from("example#PostalAddress")
      )
    )
  }

  test("SqlShapeGraph referencedStructureIds does not walk unions referenced only through list members") {
    val model = SqlTestModelBuilder.assemble(
      """
        |structure ShipmentOutput {
        |    @required
        |    items: DeliveryStates
        |}
        |
        |list DeliveryStates {
        |    member: DeliveryState
        |}
        |
        |union DeliveryState {
        |    pending: String
        |    delivered: PostalAddress
        |}
        |
        |structure PostalAddress {
        |    @required
        |    street: String
        |}
        |""".stripMargin
    )

    val legacyStructureIds   =
      SqlShapeGraph.referencedStructureIds(model, ShapeId.from("example#ShipmentOutput"))
    val (structures, unions) =
      SqlShapeGraph.referencedShapes(model, List(ShapeId.from("example#ShipmentOutput")))

    assertEquals(legacyStructureIds, List(ShapeId.from("example#ShipmentOutput")))
    assertEquals(unions, List(ShapeId.from("example#DeliveryState")))
    assertEquals(
      structures.toSet,
      Set(
        ShapeId.from("example#ShipmentOutput"),
        ShapeId.from("example#PostalAddress")
      )
    )
  }
}
