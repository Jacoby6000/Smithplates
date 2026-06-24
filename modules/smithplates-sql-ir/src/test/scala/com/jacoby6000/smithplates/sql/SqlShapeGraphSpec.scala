package com.jacoby6000.smithplates.sql

import munit.FunSuite
import software.amazon.smithy.model.shapes.ShapeId

class SqlShapeGraphSpec extends FunSuite {
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
