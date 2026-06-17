package com.jacoby6000.smithplates.sql

import com.jacoby6000.smithplates.sql.model.*
import munit.FunSuite
import software.amazon.smithy.model.shapes.ShapeId

final class SqlTableTreeSpec extends FunSuite {
  test("tablesInRenderOrder - referenced tables precede dependents") {
    val childShape  = ShapeId.from("example#Child")
    val parentShape = ShapeId.from("example#Parent")

    val schema = SqlSchema(
      tables = List(
        SqlTable(
          name = "child",
          shapeId = childShape,
          columns = Nil,
          primaryKeys = List("id"),
          foreignKeys = List(
            SqlForeignKey(
              column = "parent_id",
              sourceMember = ShapeId.from("example#Child$parent"),
              referencesShape = parentShape,
              referencesColumn = "id"
            )
          ),
          indexes = Nil
        ),
        SqlTable(
          name = "parent",
          shapeId = parentShape,
          columns = Nil,
          primaryKeys = List("id"),
          foreignKeys = Nil,
          indexes = Nil
        )
      )
    )

    val ordered = SqlTableTree.tablesInRenderOrder(schema).map(_.name)
    assertEquals(ordered, List("parent", "child"))
  }

  test("graph - foreign key edges point at parent tables") {
    val childShape  = ShapeId.from("example#Child")
    val parentShape = ShapeId.from("example#Parent")
    val memberShape = ShapeId.from("example#Child$parent")

    val parent = SqlTable("parent", parentShape, Nil, List("id"), Nil, Nil)
    val child  = SqlTable(
      "child",
      childShape,
      Nil,
      List("id"),
      List(SqlForeignKey("parent_id", memberShape, parentShape, "id")),
      Nil
    )

    val graph     = SqlTableTree.graph(SqlSchema(List(child, parent)))
    val childNode = graph.edges.keys.find(_.table.name == "child").get
    assertEquals(graph.edges(childNode).keys.toList, List(memberShape))
    assertEquals(graph.edges(childNode)(memberShape).targetShapeId, parentShape)
  }

  test("forest - self-referential foreign keys do not create dependency cycles") {
    val nodeShape   = ShapeId.from("example#TreeNode")
    val memberShape = ShapeId.from("example#TreeNode$parent")

    val treeNode = SqlTable(
      "tree_nodes",
      nodeShape,
      Nil,
      List("id"),
      List(SqlForeignKey("parent_node_id", memberShape, nodeShape, "id")),
      Nil
    )

    val graph = SqlTableTree.graph(SqlSchema(List(treeNode)))
    val node  = graph.edges.keys.find(_.table.name == "tree_nodes").get
    assertEquals(graph.edges(node)(memberShape).targetShapeId, nodeShape)
  }

  test("tablesInRenderOrder - self-referential table appears once") {
    val nodeShape = ShapeId.from("example#TreeNode")

    val schema = SqlSchema(
      tables = List(
        SqlTable(
          name = "tree_nodes",
          shapeId = nodeShape,
          columns = Nil,
          primaryKeys = List("id"),
          foreignKeys = List(
            SqlForeignKey(
              column = "parent_node_id",
              sourceMember = ShapeId.from("example#TreeNode$parent"),
              referencesShape = nodeShape,
              referencesColumn = "id"
            )
          ),
          indexes = Nil
        )
      )
    )

    val ordered = SqlTableTree.tablesInRenderOrder(schema).map(_.name)
    assertEquals(ordered, List("tree_nodes"))
  }

  test("tablesInRenderOrder - cyclic tables are emitted deterministically") {
    val aShape = ShapeId.from("example#A")
    val bShape = ShapeId.from("example#B")

    val schema = SqlSchema(
      tables = List(
        SqlTable(
          name = "b_table",
          shapeId = bShape,
          columns = Nil,
          primaryKeys = List("id"),
          foreignKeys = List(SqlForeignKey("a_id", ShapeId.from("example#B$a"), aShape, "id")),
          indexes = Nil
        ),
        SqlTable(
          name = "a_table",
          shapeId = aShape,
          columns = Nil,
          primaryKeys = List("id"),
          foreignKeys = List(SqlForeignKey("b_id", ShapeId.from("example#A$b"), bShape, "id")),
          indexes = Nil
        )
      )
    )

    val ordered = SqlTableTree.tablesInRenderOrder(schema).map(_.name)
    assertEquals(ordered, List("a_table", "b_table"))
  }
}
