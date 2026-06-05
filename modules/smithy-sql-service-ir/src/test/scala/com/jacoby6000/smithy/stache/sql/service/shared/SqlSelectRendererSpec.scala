package com.jacoby6000.smithy.stache.sql.service.shared

import com.jacoby6000.smithy.stache.sql.*
import com.jacoby6000.smithy.stache.sql.service.SqlModelExtractor
import com.jacoby6000.smithy.stache.sql.service.SqlQueries
import com.jacoby6000.smithy.stache.sql.shared.SqlBindPlaceholder
import software.amazon.smithy.model.shapes.ShapeId

class SqlSelectRendererSpec extends munit.FunSuite {
  private def queryStatement(queries: SqlQueries, dialect: SqlDialect): String =
    SqlQueryRenderer
      .renderQueryUnits(queries, dialect)
      .find(_.shapeId == ShapeId.from("example#ListItemCategories"))
      .map { query =>
        SqlBindPlaceholder.format(
          query.statement.segments,
          SqlBindPlaceholder.forDialect(dialect)
        )
      }
      .getOrElse(fail("query 'example#ListItemCategories' was not rendered"))

  private lazy val starQueryModel = SqlTestModelBuilder.assemble(
    """
      |use stache.codegen.sql#DerivedStruct
      |use stache.codegen.sql#sqlDeriveSelect
      |use stache.codegen.sql#sqlForeignKey
      |use stache.codegen.sql#sqlPrimaryKey
      |use stache.codegen.sql#sqlTable
      |use stache.codegen.sql#sqlVarchar
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
      |structure ListItemsInput {
      |    category_id: String
      |}
      |
      |@sqlDeriveSelect(
      |    from: { table: "example#Item", alias: "i" },
      |    joins: [{ table: "example#Category", type: "inner", tableAlias: "c" }],
      |    where: [{ left: "i.category_id", operator: "=", right: "input.category_id" }]
      |)
      |operation ListItems {
      |    input: ListItemsInput
      |    output: DerivedStruct
      |}
      |""".stripMargin
  )

  private lazy val starSchema = SqlModelExtractor.extractOrThrow(starQueryModel)

  test("Postgres - renders default star projections as explicit table columns") {
    val statement =
      SqlQueryRenderer
        .renderQueryUnits(starSchema.queries, PostgresDialect)
        .find(_.shapeId == ShapeId.from("example#ListItems"))
        .map { query =>
          SqlBindPlaceholder.format(
            query.statement.segments,
            SqlBindPlaceholder.forDialect(PostgresDialect)
          )
        }
        .getOrElse(fail("query 'example#ListItems' was not rendered"))

    assertEquals(
      statement,
      """SELECT i.id AS i_id, i.category_id AS i_category_id, i.name AS i_name, c.id AS c_id, c.name AS c_name
        |FROM items AS i
        |INNER JOIN categories AS c ON i.category_id = c.id
        |WHERE i.category_id = $1;""".stripMargin
    )
  }

  private lazy val queryModel = SqlTestModelBuilder.assemble(
    """
      |use stache.codegen.sql#DerivedStruct
      |use stache.codegen.sql#sqlDeriveSelect
      |use stache.codegen.sql#sqlForeignKey
      |use stache.codegen.sql#sqlPrimaryKey
      |use stache.codegen.sql#sqlTable
      |use stache.codegen.sql#sqlVarchar
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

  test("Postgres - renders derive select join, filters, and HAVING placeholders") {
    assertEquals(
      queryStatement(schema.queries, PostgresDialect),
      """SELECT i.id AS itemId, c.name AS categoryName, COUNT(i.id) AS itemCount
        |FROM items AS i
        |INNER JOIN categories AS c ON i.category_id = c.id
        |WHERE i.category_id = $1
        |GROUP BY i.id, c.name
        |HAVING COUNT(i.id) = $2;""".stripMargin
    )
  }

  test("SQLite - renders derive select join, filters, and HAVING placeholders") {
    assertEquals(
      queryStatement(schema.queries, SqliteDialect),
      """SELECT i.id AS itemId, c.name AS categoryName, COUNT(i.id) AS itemCount
        |FROM items AS i
        |INNER JOIN categories AS c ON i.category_id = c.id
        |WHERE i.category_id = ?
        |GROUP BY i.id, c.name
        |HAVING COUNT(i.id) = ?;""".stripMargin
    )
  }
}
