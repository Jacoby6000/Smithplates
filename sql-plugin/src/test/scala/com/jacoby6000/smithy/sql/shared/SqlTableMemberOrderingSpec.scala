package com.jacoby6000.smithy.sql.shared

import com.jacoby6000.smithy.sql.{SqlTestModelBuilder, SqlTestModelLoader}
import munit.FunSuite

class SqlTableMemberOrderingSpec extends FunSuite {
  test("SqlTableMemberOrdering - keeps definition order for unindexed members") {
    val model = SqlTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use jacoby6000.codegen.sql#sqlTable
          |
          |@sqlTable(name: "items")
          |structure Item {
          |    alpha: String
          |    beta: String
          |    gamma: String
          |}
          |""".stripMargin
    )

    val structure = model.expectShape(software.amazon.smithy.model.shapes.ShapeId.from("example#Item")).asStructureShape.get()
    assertEquals(
      SqlTableMemberOrdering.orderedMembers(structure).map(_._1),
      List("alpha", "beta", "gamma")
    )
  }

  test("SqlTableMemberOrdering - places timestamp columns last with created before updated") {
    val model = SqlTestModelBuilder.assemble(
      """use jacoby6000.codegen.sql#sqlAutoUuid
        |use jacoby6000.codegen.sql#sqlCreatedTimestamp
        |use jacoby6000.codegen.sql#sqlPrimaryKey
        |use jacoby6000.codegen.sql#sqlTable
        |use jacoby6000.codegen.sql#sqlUpdatedTimestamp
        |
        |@sqlTable(name: "widgets")
        |structure Widget {
        |    @sqlPrimaryKey
        |    @sqlAutoUuid
        |    id: String
        |    foo: String
        |    bar: Long
        |    @sqlCreatedTimestamp
        |    created_at: Timestamp
        |    @sqlUpdatedTimestamp
        |    updated_at: Timestamp
        |}
        |""".stripMargin
    )

    val structure =
      model.expectShape(software.amazon.smithy.model.shapes.ShapeId.from("example#Widget")).asStructureShape.get()
    assertEquals(
      SqlTableMemberOrdering.orderedMembers(structure).map(_._1),
      List("id", "foo", "bar", "created_at", "updated_at")
    )
  }

  test("SqlTableMemberOrdering - honors explicit @sqlColumnIndex values") {
    val model = SqlTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use jacoby6000.codegen.sql#sqlColumnIndex
          |use jacoby6000.codegen.sql#sqlTable
          |
          |@sqlTable(name: "items")
          |structure Item {
          |    @sqlColumnIndex(index: 20)
          |    last: String
          |    middle: String
          |    @sqlColumnIndex(index: 10)
          |    first: String
          |}
          |""".stripMargin
    )

    val structure = model.expectShape(software.amazon.smithy.model.shapes.ShapeId.from("example#Item")).asStructureShape.get()
    assertEquals(
      SqlTableMemberOrdering.orderedMembers(structure).map(_._1),
      List("middle", "first", "last")
    )
  }
}
