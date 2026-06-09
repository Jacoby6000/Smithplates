package com.jacoby6000.smithplates.sql.shared

import com.jacoby6000.smithplates.sql.SqlSchema
import com.jacoby6000.smithplates.sql.SqlTable

import scala.collection.mutable

/** A table and the other schema tables it must exist after (FK targets). */
final case class SqlTableTreeNode(
    table: SqlTable,
    dependencies: List[SqlTableTreeNode]
)

object SqlTableTree {

  /** One node per @sqlTable structure; dependencies are in-schema FK targets. */
  def forest(schema: SqlSchema): List[SqlTableTreeNode] = {
    val tableByShapeId = schema.tables.map(table => table.shapeId -> table).toMap
    val memo           = mutable.Map.empty[String, SqlTableTreeNode]

    schema.tables
      .map(table => nodeFor(table, tableByShapeId, memo))
      .sortBy(_.table.name)
  }

  /** Post-order depth-first traversal: render dependency subtrees (leaves first), then each table. Shared dependencies
    * are rendered once.
    */
  def tablesInRenderOrder(schema: SqlSchema): List[SqlTable] = {
    val visited = mutable.Set.empty[String]
    val ordered = mutable.ArrayBuffer.empty[SqlTable]

    def visit(node: SqlTableTreeNode): Unit =
      if (!visited.contains(node.table.name)) {
        node.dependencies.foreach(visit)
        visited += node.table.name
        ordered += node.table
      }

    forest(schema).foreach(visit)
    ordered.toList
  }

  private def nodeFor(
      table: SqlTable,
      tableByShapeId: Map[software.amazon.smithy.model.shapes.ShapeId, SqlTable],
      memo: mutable.Map[String, SqlTableTreeNode]
  ): SqlTableTreeNode =
    memo.getOrElse(
      table.name, {
        val dependencies =
          table.foreignKeys
            .flatMap(foreignKey => tableByShapeId.get(foreignKey.referencesShape))
            .distinctBy(_.name)
            .sortBy(_.name)
            .map(dependencyTable => nodeFor(dependencyTable, tableByShapeId, memo))

        val node = SqlTableTreeNode(table, dependencies)
        memo(table.name) = node
        node
      }
    )
}
