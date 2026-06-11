package com.jacoby6000.smithplates.sql.service.query.renderer.sqlite

import com.jacoby6000.smithplates.sql.*
import com.jacoby6000.smithplates.sql.service.SqlModelExtractor
import com.jacoby6000.smithplates.sql.service.SqlQueries
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlBindPlaceholder
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlQueryRenderer
import software.amazon.smithy.model.shapes.ShapeId

final class SqliteSqlSelectRendererSpec extends munit.FunSuite {
  private lazy val renderer =
    new SqliteSqlQueryRenderer(
      migrationBindPlaceholder = SqlBindPlaceholder("?"),
      codegenBindPlaceholder = SqlBindPlaceholder("?")
    )

  private def queryStatement(queries: SqlQueries, renderer: SqlQueryRenderer): String =
    renderer
      .renderQueryUnits(queries)
      .find(_.shapeId == ShapeId.from("example#ListItemCategories"))
      .map { query =>
        SqlBindPlaceholder.format(
          query.statement.segments,
          renderer.migrationBindPlaceholder
        )
      }
      .getOrElse(fail("query 'example#ListItemCategories' was not rendered"))

  private lazy val queryModel = SqlTestModelBuilder.assemble(
    """
      |use smithplates.codegen.sql#DerivedStruct
      |use smithplates.codegen.sql#sqlDeriveSelect
      |use smithplates.codegen.sql#sqlForeignKey
      |use smithplates.codegen.sql#sqlPrimaryKey
      |use smithplates.codegen.sql#sqlTable
      |use smithplates.codegen.sql#sqlVarchar
      |
      |@sqlTable(name: "categories")
      |structure Category {
      |    @sqlPrimaryKey
      |    id: String
      |    name: String
      |}
      |
      |@sqlTable(name: "items")
      |structure Item {
      |    @sqlPrimaryKey
      |    id: String
      |    @sqlForeignKey(references: "example#Category")
      |    category_id: String
      |    @sqlVarchar(maxLength: 64)
      |    name: String
      |}
      |
      |structure ListItemCategoriesInput {
      |    category_id: String
      |    minCount: Integer
      |}
      |
      |@sqlDeriveSelect(
      |    from: { table: "example#Item", alias: "i" },
      |    joins: [{ table: "example#Category", type: "inner", tableAlias: "c" }],
      |    projections: [
      |        { alias: "itemId", source: "i.id" },
      |        { alias: "categoryName", source: "c.name" },
      |        { alias: "itemCount", aggregate: "count", source: "i.id" }
      |    ],
      |    where: [{ left: "i.category_id", operator: "=", right: "input.category_id" }],
      |    groupBy: ["i.id", "c.name"],
      |    having: [{ left: "itemCount", operator: "=", right: "input.minCount" }]
      |)
      |operation ListItemCategories {
      |    input: ListItemCategoriesInput
      |    output: DerivedStruct
      |}
      |""".stripMargin
  )

  private lazy val schema = SqlModelExtractor.extractOrThrow(queryModel)

  test("DeriveSelect - renders join, filters, and HAVING placeholders") {
    assertEquals(
      queryStatement(schema.queries, renderer),
      """SELECT i.id AS itemId, c.name AS categoryName, COUNT(i.id) AS itemCount
        |FROM items AS i
        |INNER JOIN categories AS c ON i.category_id = c.id
        |WHERE i.category_id = ?
        |GROUP BY i.id, c.name
        |HAVING COUNT(i.id) = ?;""".stripMargin
    )
  }
}
