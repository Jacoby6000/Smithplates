package com.jacoby6000.smithplates.sql

import com.jacoby6000.smithplates.sql.model.SqlSchema
import com.jacoby6000.smithplates.sql.model.SqlTable
import software.amazon.smithy.model.shapes.ShapeId

import scala.collection.immutable.SortedMap

/** A table graph node. */
final case class SqlTableTreeNode(
    table: SqlTable
)

/** A foreign-key edge from a source table member to a target table shape. */
final case class SqlTableGraphEdge(
    memberShapeId: ShapeId,
    targetShapeId: ShapeId,
    required: Boolean
)

final case class SqlTableGraph(
    edges: SortedMap[SqlTableTreeNode, SortedMap[ShapeId, SqlTableGraphEdge]]
)

object SqlTableTree {
  private given Ordering[ShapeId]          = Ordering.by(_.toString)
  private given Ordering[SqlTableTreeNode] =
    Ordering.by(node => (node.table.name, node.table.shapeId.toString))

  /** One node per @sqlTable structure; edge values are in-schema FK targets keyed by source member id. */
  def graph(schema: SqlSchema): SqlTableGraph = {
    val tableByShapeId = schema.tables.map(table => table.shapeId -> table).toMap

    val edges =
      SortedMap.from(
        schema.tables.map { table =>
          val node       = SqlTableTreeNode(table)
          val columnById = table.columns.map(column => column.name -> column).toMap
          val tableEdges =
            SortedMap.from(
              table.foreignKeys
                .flatMap { foreignKey =>
                  tableByShapeId.get(foreignKey.referencesShape).map { _ =>
                    foreignKey.sourceMember -> SqlTableGraphEdge(
                      memberShapeId = foreignKey.sourceMember,
                      targetShapeId = foreignKey.referencesShape,
                      required = columnById.get(foreignKey.column).exists(column => !column.nullable)
                    )
                  }
                }
            )
          node -> tableEdges
        }
      )

    SqlTableGraph(edges)
  }

  /** One node per @sqlTable structure; retained for callers that only need the table nodes. */
  def forest(schema: SqlSchema): List[SqlTableTreeNode] =
    graph(schema).edges.keys.toList

  /** Deterministic topological ordering. If foreign keys form a cycle, every acyclic table is still ordered by
    * dependencies and the cyclic remainder is appended by table name.
    */
  def tablesInRenderOrder(schema: SqlSchema): List[SqlTable] = {
    val tableGraph  = graph(schema)
    val nodeByShape = tableGraph.edges.keys.map(node => node.table.shapeId -> node).toMap
    val tableNames  = tableGraph.edges.keys.toList.map(_.table.shapeId)

    val prerequisitesByTable: Map[ShapeId, Set[ShapeId]] =
      tableGraph.edges.toList.map { case (node, edges) =>
        val dependencies =
          edges.values
            .flatMap(edge => nodeByShape.get(edge.targetShapeId).map(_.table.shapeId))
            .filter(_ != node.table.shapeId)
            .toSet
        node.table.shapeId -> dependencies
      }.toMap

    def loop(remaining: List[ShapeId], emitted: Set[ShapeId], ordered: List[ShapeId]): List[ShapeId] = {
      val ready =
        remaining.filter(shapeId => prerequisitesByTable.getOrElse(shapeId, Set.empty).subsetOf(emitted))

      ready match {
        case Nil =>
          ordered ++ remaining.sortBy(shapeId => nodeByShape(shapeId).table.name)
        case _   =>
          val nextEmitted = emitted ++ ready
          loop(remaining.filterNot(nextEmitted.contains), nextEmitted, ordered ++ ready)
      }
    }

    loop(tableNames, Set.empty, Nil).flatMap(shapeId => nodeByShape.get(shapeId).map(_.table))
  }

  def hasRequiredCycleContaining(schema: SqlSchema, tableShapeId: ShapeId): Boolean = {
    val requiredEdgesBySource =
      graph(schema).edges.toList.map { case (node, edges) =>
        node.table.shapeId -> edges.values.filter(_.required).map(_.targetShapeId).toSet
      }.toMap

    def visits(start: ShapeId, current: ShapeId, visited: Set[ShapeId]): Boolean =
      requiredEdgesBySource.getOrElse(current, Set.empty).exists { target =>
        target == start || (!visited.contains(target) && visits(start, target, visited + target))
      }

    visits(tableShapeId, tableShapeId, Set(tableShapeId))
  }
}
