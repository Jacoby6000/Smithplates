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
            SqlForeignKey(column = "parent_id", referencesShape = parentShape, referencesColumn = "id")
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

  test("forest - dependency nodes point at parent tables") {
    val childShape  = ShapeId.from("example#Child")
    val parentShape = ShapeId.from("example#Parent")

    val parent = SqlTable("parent", parentShape, Nil, List("id"), Nil, Nil)
    val child  = SqlTable(
      "child",
      childShape,
      Nil,
      List("id"),
      List(SqlForeignKey("parent_id", parentShape, "id")),
      Nil
    )

    val forest    = SqlTableTree.forest(SqlSchema(List(child, parent)))
    val childNode = forest.find(_.table.name == "child").get
    assertEquals(childNode.dependencies.map(_.table.name), List("parent"))
    assertEquals(childNode.dependencies.head.dependencies, Nil)
  }

  test("forest - self-referential foreign keys do not create dependency cycles") {
    val nodeShape = ShapeId.from("example#TreeNode")

    val treeNode = SqlTable(
      "tree_nodes",
      nodeShape,
      Nil,
      List("id"),
      List(SqlForeignKey("parent_node_id", nodeShape, "id")),
      Nil
    )

    val forest = SqlTableTree.forest(SqlSchema(List(treeNode)))
    val node   = forest.find(_.table.name == "tree_nodes").get
    assertEquals(node.dependencies, Nil)
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
            SqlForeignKey(column = "parent_node_id", referencesShape = nodeShape, referencesColumn = "id")
          ),
          indexes = Nil
        )
      )
    )

    val ordered = SqlTableTree.tablesInRenderOrder(schema).map(_.name)
    assertEquals(ordered, List("tree_nodes"))
  }
}
