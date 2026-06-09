package com.jacoby6000.smithplates.sql.service

import com.jacoby6000.smithplates.sql.*

class SqlDeriveSelectExtractorSpec extends munit.FunSuite {
  private def baseTableModel: String =
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
      |""".stripMargin

  private def columnProjection(projection: SqlSelectProjection): SqlQualifiedColumn =
    projection match {
      case SqlSelectColumnProjection(_, column)    => column
      case aggregate: SqlSelectAggregateProjection =>
        fail(s"expected column projection, got aggregate ${aggregate.resultAlias}")
    }

  test("DeriveSelect - expands default star projections for from and joins") {
    val schema = SqlModelExtractor.extractOrThrow(
      SqlTestModelBuilder.assemble(
        baseTableModel +
          """
            |structure ItemOnlySelectInput {
            |    category_id: String
            |}
            |
            |@sqlDeriveSelect(
            |    from: { table: "example#Item", alias: "i" },
            |    joins: [{ table: "example#Category", tableAlias: "c" }],
            |    where: [{ left: "i.category_id", operator: "=", right: "input.category_id" }]
            |)
            |operation ItemOnlySelect {
            |    input: ItemOnlySelectInput
            |    output: DerivedStruct
            |}
            |""".stripMargin
      )
    )

    val select = schema.queries.selects.head
    assertEquals(
      select.selectColumns.collect { case projection: SqlSelectColumnProjection => projection.resultAlias },
      List("i_id", "i_category_id", "i_name", "c_id", "c_name")
    )
  }

  test("DeriveSelect - rejects star projections with groupBy") {
    val result = SqlModelExtractor.extract(
      SqlTestModelBuilder.assemble(
        baseTableModel +
          """
            |structure ItemCategorySelectInput {
            |    category_id: String
            |}
            |
            |@sqlDeriveSelect(
            |    from: { table: "example#Item", alias: "i" },
            |    joins: [{ table: "example#Category", tableAlias: "c" }],
            |    groupBy: ["i.id"]
            |)
            |operation ItemCategorySelect {
            |    input: ItemCategorySelectInput
            |    output: DerivedStruct
            |}
            |""".stripMargin
      )
    )

    assert(result.isInvalid)
  }

  test("DeriveSelect - infers bare column reference when table is unique") {
    val schema = SqlModelExtractor.extractOrThrow(
      SqlTestModelBuilder.assemble(
        baseTableModel +
          """
            |structure ItemOnlySelectInput {
            |    category_id: String
            |}
            |
            |@sqlDeriveSelect(
            |    from: { table: "example#Item" },
            |    projections: [{ alias: "category_id", source: "category_id" }],
            |    where: [{ left: "category_id", operator: "=", right: "input.category_id" }]
            |)
            |operation ItemOnlySelect {
            |    input: ItemOnlySelectInput
            |    output: DerivedStruct
            |}
            |""".stripMargin
      )
    )

    val select = schema.queries.selects.head
    assertEquals(columnProjection(select.selectColumns.head), SqlQualifiedColumn("items", "category_id"))
  }

  test("DeriveSelect - fails when output is not DerivedStruct") {
    val result = SqlModelExtractor.extract(
      SqlTestModelBuilder.assemble(
        baseTableModel +
          """
            |structure ItemOnlySelectInput {
            |    category_id: String
            |}
            |
            |@sqlDeriveSelect(
            |    from: { table: "example#Item" },
            |    projections: [{ alias: "category_id", source: "category_id" }]
            |)
            |operation ItemOnlySelect {
            |    input: ItemOnlySelectInput
            |    output: String
            |}
            |""".stripMargin
      )
    )

    assert(result.isInvalid)
    assert(
      result.swap.toOption.get.exists {
        case InvalidDeriveSelect(_, reason) if reason.contains("DerivedStruct") => true
        case _                                                                  => false
      }
    )
  }

  test("DeriveSelect - fails when input is DerivedStruct") {
    val result = SqlModelExtractor.extract(
      SqlTestModelBuilder.assemble(
        baseTableModel +
          """
            |@sqlDeriveSelect(
            |    from: { table: "example#Item" },
            |    projections: [{ alias: "category_id", source: "category_id" }]
            |)
            |operation ItemOnlySelect {
            |    input: DerivedStruct
            |    output: DerivedStruct
            |}
            |""".stripMargin
      )
    )

    assert(result.isInvalid)
  }

  test("DeriveSelect - fails when WHERE references aggregate projection") {
    val result = SqlModelExtractor.extract(
      SqlTestModelBuilder.assemble(
        baseTableModel +
          """
            |structure ItemCategorySelectInput {
            |    minCount: Integer
            |}
            |
            |@sqlDeriveSelect(
            |    from: { table: "example#Item", alias: "i" },
            |    joins: [{ table: "example#Category", tableAlias: "c" }],
            |    projections: [
            |        { alias: "itemId", source: "i.id" },
            |        { alias: "itemCount", aggregate: "count", source: "i.id" }
            |    ],
            |    where: [{ left: "itemCount", operator: "=", right: "input.minCount" }],
            |    groupBy: ["i.id"]
            |)
            |operation ItemCategorySelect {
            |    input: ItemCategorySelectInput
            |    output: DerivedStruct
            |}
            |""".stripMargin
      )
    )

    assert(result.isInvalid)
  }

  test("DeriveSelect - allows HAVING on aggregate projection") {
    val schema = SqlModelExtractor.extractOrThrow(
      SqlTestModelBuilder.assemble(
        baseTableModel +
          """
            |structure ItemCategorySelectInput {
            |    category_id: String
            |    minCount: Integer
            |}
            |
            |@sqlDeriveSelect(
            |    from: { table: "example#Item", alias: "i" },
            |    joins: [{ table: "example#Category", tableAlias: "c" }],
            |    projections: [
            |        { alias: "itemId", source: "i.id" },
            |        { alias: "itemCount", aggregate: "count", source: "i.id" }
            |    ],
            |    where: [{ left: "i.category_id", operator: "=", right: "input.category_id" }],
            |    groupBy: ["i.id"],
            |    having: [{ left: "itemCount", operator: "=", right: "input.minCount" }]
            |)
            |operation ItemCategorySelect {
            |    input: ItemCategorySelectInput
            |    output: DerivedStruct
            |}
            |""".stripMargin
      )
    )

    val select = schema.queries.selects.head
    assertEquals(
      select.havingPredicates.collect { case SqlSelectPredicate(_, _, SqlPredicateOperand.InputMember(name)) =>
        name
      },
      List("minCount")
    )
  }
}
