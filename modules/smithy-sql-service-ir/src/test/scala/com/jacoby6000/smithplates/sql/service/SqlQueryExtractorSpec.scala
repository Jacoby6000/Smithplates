package com.jacoby6000.smithplates.sql.service

import com.jacoby6000.smithplates.sql.*

class SqlQueryExtractorSpec extends munit.FunSuite {
  test("DeriveInsert - derives input columns from table and returns id when output matches primary key type") {
    val model = SqlTestModelBuilder.assemble(
      """
        |use smithplates.codegen.sql#DerivedStruct
        |use smithplates.codegen.sql#sqlAutoUuid
        |use smithplates.codegen.sql#sqlCreatedTimestamp
        |use smithplates.codegen.sql#sqlDeriveInsert
        |use smithplates.codegen.sql#sqlPrimaryKey
        |use smithplates.codegen.sql#sqlTable
        |use smithplates.codegen.sql#sqlUpdatedTimestamp
        |
        |@sqlTable(name: "widgets")
        |structure Widget {
        |    @sqlPrimaryKey
        |    @sqlAutoUuid
        |    id: String
        |    foo: String
        |    bar: Long
        |    optional_note: String
        |    @sqlCreatedTimestamp
        |    created_at: Timestamp
        |    @sqlUpdatedTimestamp
        |    updated_at: Timestamp
        |}
        |
        |@sqlDeriveInsert(targetTable: "example#Widget")
        |operation CreateWidget {
        |    input: DerivedStruct
        |    output: String
        |}
        |""".stripMargin
    )

    val schema = SqlModelExtractor.extractOrThrow(model)
    assertEquals(schema.queries.inserts.size, 1)
    val insert = schema.queries.inserts.head
    assertEquals(insert.shapeId.toString, "example#CreateWidget")
    assertEquals(insert.table.name, "widgets")
    assertEquals(insert.columns.map(_.memberName), List("foo", "bar", "optional_note"))
    assertEquals(insert.returningColumns.map(_.columnName), List("id"))
  }

  test("DeriveInsert - maps output structure members to RETURNING columns") {
    val model = SqlTestModelBuilder.assemble(
      """
        |use smithplates.codegen.sql#DerivedStruct
        |use smithplates.codegen.sql#sqlAutoUuid
        |use smithplates.codegen.sql#sqlCreatedTimestamp
        |use smithplates.codegen.sql#sqlDeriveInsert
        |use smithplates.codegen.sql#sqlPrimaryKey
        |use smithplates.codegen.sql#sqlTable
        |use smithplates.codegen.sql#sqlUpdatedTimestamp
        |
        |@sqlTable(name: "widgets")
        |structure Widget {
        |    @sqlPrimaryKey
        |    @sqlAutoUuid
        |    id: String
        |    foo: String
        |    @sqlCreatedTimestamp
        |    created_at: Timestamp
        |    @sqlUpdatedTimestamp
        |    updated_at: Timestamp
        |}
        |
        |structure CreateWidgetOutput {
        |    id: String
        |    created_at: Timestamp
        |}
        |
        |@sqlDeriveInsert(targetTable: "example#Widget")
        |operation CreateWidget {
        |    input: DerivedStruct
        |    output: CreateWidgetOutput
        |}
        |""".stripMargin
    )

    val schema = SqlModelExtractor.extractOrThrow(model)
    val insert = schema.queries.inserts.head
    assertEquals(insert.returningColumns.map(_.columnName), List("id", "created_at"))
  }

  test("DeriveInsert - includes non-auto-generated primary keys in derived input columns") {
    val model = SqlTestModelBuilder.assemble(
      """
        |use smithplates.codegen.sql#DerivedStruct
        |use smithplates.codegen.sql#sqlDeriveInsert
        |use smithplates.codegen.sql#sqlPrimaryKey
        |use smithplates.codegen.sql#sqlTable
        |
        |@sqlTable(name: "widgets")
        |structure Widget {
        |    @required
        |    @sqlPrimaryKey
        |    id: String
        |    @required
        |    foo: String
        |}
        |
        |@sqlDeriveInsert(targetTable: "example#Widget")
        |operation CreateWidget {
        |    input: DerivedStruct
        |    output: String
        |}
        |""".stripMargin
    )

    val schema = SqlModelExtractor.extractOrThrow(model)
    val insert = schema.queries.inserts.head
    assertEquals(insert.columns.map(_.memberName), List("id", "foo"))
    assertEquals(insert.returningColumns.map(_.columnName), List("id"))
  }

  test("DeriveInsert - fails when input is Unit") {
    val result = SqlModelExtractor.extract(
      SqlTestModelBuilder.assemble(
        """
          |use smithplates.codegen.sql#sqlAutoUuid
          |use smithplates.codegen.sql#sqlDeriveInsert
          |use smithplates.codegen.sql#sqlPrimaryKey
          |use smithplates.codegen.sql#sqlTable
          |
          |@sqlTable(name: "widgets")
          |structure Widget {
          |    @sqlPrimaryKey
          |    @sqlAutoUuid
          |    id: String
          |    foo: String
          |}
          |
          |@sqlDeriveInsert(targetTable: "example#Widget")
          |operation CreateWidget {
          |    input: Unit
          |    output: String
          |}
          |""".stripMargin
      )
    )

    assert(result.isInvalid)
    assert(
      result.swap.toOption.get.exists {
        case InvalidDeriveInsert(_, reason) if reason.contains("smithplates.codegen.sql#DerivedStruct") => true
        case _                                                                                          => false
      }
    )
  }

  test("DeriveInsert - fails when input is not DerivedStruct") {
    val result = SqlModelExtractor.extract(
      SqlTestModelBuilder.assemble(
        """
          |use smithplates.codegen.sql#sqlAutoUuid
          |use smithplates.codegen.sql#sqlDeriveInsert
          |use smithplates.codegen.sql#sqlPrimaryKey
          |use smithplates.codegen.sql#sqlTable
          |
          |@sqlTable(name: "widgets")
          |structure Widget {
          |    @sqlPrimaryKey
          |    @sqlAutoUuid
          |    id: String
          |    foo: String
          |}
          |
          |structure CreateWidgetInput {
          |    foo: String
          |}
          |
          |@sqlDeriveInsert(targetTable: "example#Widget")
          |operation CreateWidget {
          |    input: CreateWidgetInput
          |    output: String
          |}
          |""".stripMargin
      )
    )

    assert(result.isInvalid)
    assert(
      result.swap.toOption.get.exists {
        case InvalidDeriveInsert(_, reason) if reason.contains("smithplates.codegen.sql#DerivedStruct") => true
        case _                                                                                          => false
      }
    )
  }

  test("DeriveInsert - fails when output is Unit") {
    val result = SqlModelExtractor.extract(
      SqlTestModelBuilder.assemble(
        """
          |use smithplates.codegen.sql#DerivedStruct
          |use smithplates.codegen.sql#sqlAutoUuid
          |use smithplates.codegen.sql#sqlDeriveInsert
          |use smithplates.codegen.sql#sqlPrimaryKey
          |use smithplates.codegen.sql#sqlTable
          |
          |@sqlTable(name: "widgets")
          |structure Widget {
          |    @sqlPrimaryKey
          |    @sqlAutoUuid
          |    id: String
          |    foo: String
          |}
          |
          |@sqlDeriveInsert(targetTable: "example#Widget")
          |operation CreateWidget {
          |    input: DerivedStruct
          |    output: Unit
          |}
          |""".stripMargin
      )
    )

    assert(result.isInvalid)
    assert(
      result.swap.toOption.get.exists {
        case InvalidDeriveInsert(_, reason) if reason.contains("primary key target type") => true
        case _                                                                            => false
      }
    )
  }

  test("DeriveInsert - fails when output type does not match primary key type or a structure") {
    val result = SqlModelExtractor.extract(
      SqlTestModelBuilder.assemble(
        """
          |use smithplates.codegen.sql#DerivedStruct
          |use smithplates.codegen.sql#sqlAutoUuid
          |use smithplates.codegen.sql#sqlDeriveInsert
          |use smithplates.codegen.sql#sqlPrimaryKey
          |use smithplates.codegen.sql#sqlTable
          |
          |@sqlTable(name: "widgets")
          |structure Widget {
          |    @sqlPrimaryKey
          |    @sqlAutoUuid
          |    id: String
          |    foo: String
          |}
          |
          |@sqlDeriveInsert(targetTable: "example#Widget")
          |operation CreateWidget {
          |    input: DerivedStruct
          |    output: Integer
          |}
          |""".stripMargin
      )
    )

    assert(result.isInvalid)
    assert(
      result.swap.toOption.get.exists {
        case InvalidDeriveInsert(_, reason) if reason.contains("does not match a primary key target type") => true
        case _                                                                                             => false
      }
    )
  }

  test("DeriveInsert - fails when output includes an unknown member") {
    val result = SqlModelExtractor.extract(
      SqlTestModelBuilder.assemble(
        """
          |use smithplates.codegen.sql#DerivedStruct
          |use smithplates.codegen.sql#sqlDeriveInsert
          |use smithplates.codegen.sql#sqlPrimaryKey
          |use smithplates.codegen.sql#sqlTable
          |
          |@sqlTable(name: "widgets")
          |structure Widget {
          |    @sqlPrimaryKey
          |    id: String
          |    foo: String
          |}
          |
          |structure CreateWidgetOutput {
          |    id: String
          |    extra: String
          |}
          |
          |@sqlDeriveInsert(targetTable: "example#Widget")
          |operation CreateWidget {
          |    input: DerivedStruct
          |    output: CreateWidgetOutput
          |}
          |""".stripMargin
      )
    )

    assert(result.isInvalid)
    assert(
      result.swap.toOption.get.exists {
        case QueryMemberNotOnTable(_, "extra", "widgets", InvalidQueryTableReference.Kind.Insert) => true
        case _                                                                                    => false
      }
    )
  }

  test("DeriveUpdate - derives whereClause from primary keys and updateFields from updatable columns") {
    val model = SqlTestModelBuilder.assemble(
      """
        |use smithplates.codegen.sql#DerivedStruct
        |use smithplates.codegen.sql#sqlAutoUuid
        |use smithplates.codegen.sql#sqlCreatedTimestamp
        |use smithplates.codegen.sql#sqlDeriveUpdate
        |use smithplates.codegen.sql#sqlPrimaryKey
        |use smithplates.codegen.sql#sqlTable
        |use smithplates.codegen.sql#sqlUpdatedTimestamp
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
        |
        |@sqlDeriveUpdate(targetTable: "example#Widget")
        |operation UpdateWidget {
        |    input: DerivedStruct
        |    output: Boolean
        |}
        |""".stripMargin
    )

    val schema = SqlModelExtractor.extractOrThrow(model)
    assertEquals(schema.queries.updates.size, 1)
    val update = schema.queries.updates.head
    assertEquals(update.shapeId.toString, "example#UpdateWidget")
    assertEquals(update.whereColumns.map(_.memberName), List("id"))
    assertEquals(update.setColumns.map(_.memberName), List("foo", "bar"))
    assertEquals(update.returningColumns.map(_.columnName), List("updated_at"))
  }

  test("DeriveUpdate - fails when input is not DerivedStruct") {
    val result = SqlModelExtractor.extract(
      SqlTestModelBuilder.assemble(
        """
          |use smithplates.codegen.sql#sqlAutoUuid
          |use smithplates.codegen.sql#sqlDeriveUpdate
          |use smithplates.codegen.sql#sqlPrimaryKey
          |use smithplates.codegen.sql#sqlTable
          |
          |@sqlTable(name: "widgets")
          |structure Widget {
          |    @sqlPrimaryKey
          |    @sqlAutoUuid
          |    id: String
          |    foo: String
          |}
          |
          |structure UpdateWidgetInput {
          |    id: String
          |    foo: String
          |}
          |
          |@sqlDeriveUpdate(targetTable: "example#Widget")
          |operation UpdateWidget {
          |    input: UpdateWidgetInput
          |    output: Boolean
          |}
          |""".stripMargin
      )
    )

    assert(result.isInvalid)
    assert(
      result.swap.toOption.get.exists {
        case InvalidDeriveUpdate(_, reason) if reason.contains("smithplates.codegen.sql#DerivedStruct") => true
        case _                                                                                          => false
      }
    )
  }

  test("DeriveUpdate - fails when output is not Boolean") {
    val result = SqlModelExtractor.extract(
      SqlTestModelBuilder.assemble(
        """
          |use smithplates.codegen.sql#DerivedStruct
          |use smithplates.codegen.sql#sqlAutoUuid
          |use smithplates.codegen.sql#sqlDeriveUpdate
          |use smithplates.codegen.sql#sqlPrimaryKey
          |use smithplates.codegen.sql#sqlTable
          |
          |@sqlTable(name: "widgets")
          |structure Widget {
          |    @sqlPrimaryKey
          |    @sqlAutoUuid
          |    id: String
          |    foo: String
          |}
          |
          |@sqlDeriveUpdate(targetTable: "example#Widget")
          |operation UpdateWidget {
          |    input: DerivedStruct
          |    output: String
          |}
          |""".stripMargin
      )
    )

    assert(result.isInvalid)
    assert(
      result.swap.toOption.get.exists {
        case InvalidDeriveUpdate(_, reason) if reason.contains("output must be Boolean") => true
        case _                                                                           => false
      }
    )
  }

  test("DeriveUpdate - fails when table has no updatable updateFields") {
    val result = SqlModelExtractor.extract(
      SqlTestModelBuilder.assemble(
        """
          |use smithplates.codegen.sql#DerivedStruct
          |use smithplates.codegen.sql#sqlAutoUuid
          |use smithplates.codegen.sql#sqlCreatedTimestamp
          |use smithplates.codegen.sql#sqlDeriveUpdate
          |use smithplates.codegen.sql#sqlPrimaryKey
          |use smithplates.codegen.sql#sqlTable
          |use smithplates.codegen.sql#sqlUpdatedTimestamp
          |
          |@sqlTable(name: "widgets")
          |structure Widget {
          |    @sqlPrimaryKey
          |    @sqlAutoUuid
          |    id: String
          |    @sqlCreatedTimestamp
          |    created_at: Timestamp
          |    @sqlUpdatedTimestamp
          |    updated_at: Timestamp
          |}
          |
          |@sqlDeriveUpdate(targetTable: "example#Widget")
          |operation UpdateWidget {
          |    input: DerivedStruct
          |    output: Boolean
          |}
          |""".stripMargin
      )
    )

    assert(result.isInvalid)
    assert(
      result.swap.toOption.get.exists {
        case InvalidDeriveUpdate(_, reason) if reason.contains("no updatable columns for updateFields") => true
        case _                                                                                          => false
      }
    )
  }

  test("DeriveDelete - derives whereClause from primary keys and RETURNING columns") {
    val model = SqlTestModelBuilder.assemble(
      """
        |use smithplates.codegen.sql#DerivedStruct
        |use smithplates.codegen.sql#sqlAutoUuid
        |use smithplates.codegen.sql#sqlCreatedTimestamp
        |use smithplates.codegen.sql#sqlDeriveDelete
        |use smithplates.codegen.sql#sqlPrimaryKey
        |use smithplates.codegen.sql#sqlTable
        |use smithplates.codegen.sql#sqlUpdatedTimestamp
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
        |
        |@sqlDeriveDelete(targetTable: "example#Widget")
        |operation DeleteWidget {
        |    input: DerivedStruct
        |    output: Boolean
        |}
        |""".stripMargin
    )

    val schema = SqlModelExtractor.extractOrThrow(model)
    assertEquals(schema.queries.deletes.size, 1)
    val delete = schema.queries.deletes.head
    assertEquals(delete.shapeId.toString, "example#DeleteWidget")
    assertEquals(delete.whereColumns.map(_.memberName), List("id"))
    assertEquals(delete.returningColumns.map(_.columnName), List("id"))
  }

  test("DeriveDelete - fails when input is not DerivedStruct") {
    val result = SqlModelExtractor.extract(
      SqlTestModelBuilder.assemble(
        """
          |use smithplates.codegen.sql#sqlAutoUuid
          |use smithplates.codegen.sql#sqlDeriveDelete
          |use smithplates.codegen.sql#sqlPrimaryKey
          |use smithplates.codegen.sql#sqlTable
          |
          |@sqlTable(name: "widgets")
          |structure Widget {
          |    @sqlPrimaryKey
          |    @sqlAutoUuid
          |    id: String
          |    foo: String
          |}
          |
          |structure DeleteWidgetInput {
          |    id: String
          |}
          |
          |@sqlDeriveDelete(targetTable: "example#Widget")
          |operation DeleteWidget {
          |    input: DeleteWidgetInput
          |    output: Boolean
          |}
          |""".stripMargin
      )
    )

    assert(result.isInvalid)
    assert(
      result.swap.toOption.get.exists {
        case InvalidDeriveDelete(_, reason) if reason.contains("smithplates.codegen.sql#DerivedStruct") => true
        case _                                                                                          => false
      }
    )
  }

  test("DeriveDelete - fails when output is not Boolean") {
    val result = SqlModelExtractor.extract(
      SqlTestModelBuilder.assemble(
        """
          |use smithplates.codegen.sql#DerivedStruct
          |use smithplates.codegen.sql#sqlAutoUuid
          |use smithplates.codegen.sql#sqlDeriveDelete
          |use smithplates.codegen.sql#sqlPrimaryKey
          |use smithplates.codegen.sql#sqlTable
          |
          |@sqlTable(name: "widgets")
          |structure Widget {
          |    @sqlPrimaryKey
          |    @sqlAutoUuid
          |    id: String
          |    foo: String
          |}
          |
          |@sqlDeriveDelete(targetTable: "example#Widget")
          |operation DeleteWidget {
          |    input: DerivedStruct
          |    output: String
          |}
          |""".stripMargin
      )
    )

    assert(result.isInvalid)
    assert(
      result.swap.toOption.get.exists {
        case InvalidDeriveDelete(_, reason) if reason.contains("output must be Boolean") => true
        case _                                                                           => false
      }
    )
  }

  test("DeriveDelete - fails when table has no primary key members") {
    val result = SqlModelExtractor.extract(
      SqlTestModelBuilder.assemble(
        """
          |use smithplates.codegen.sql#DerivedStruct
          |use smithplates.codegen.sql#sqlDeriveDelete
          |use smithplates.codegen.sql#sqlTable
          |
          |@sqlTable(name: "widgets")
          |structure Widget {
          |    foo: String
          |}
          |
          |@sqlDeriveDelete(targetTable: "example#Widget")
          |operation DeleteWidget {
          |    input: DerivedStruct
          |    output: Boolean
          |}
          |""".stripMargin
      )
    )

    assert(result.isInvalid)
  }

  test("DeriveSelectOne - derives whereClause from primary keys and selects all table columns") {
    val model = SqlTestModelBuilder.assemble(
      """
        |use smithplates.codegen.sql#DerivedStruct
        |use smithplates.codegen.sql#sqlAutoUuid
        |use smithplates.codegen.sql#sqlCreatedTimestamp
        |use smithplates.codegen.sql#sqlDeriveSelectOne
        |use smithplates.codegen.sql#sqlPrimaryKey
        |use smithplates.codegen.sql#sqlTable
        |use smithplates.codegen.sql#sqlUpdatedTimestamp
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
        |
        |@sqlDeriveSelectOne(targetTable: "example#Widget")
        |operation GetWidget {
        |    input: DerivedStruct
        |    output: Widget
        |}
        |""".stripMargin
    )

    val schema    = SqlModelExtractor.extractOrThrow(model)
    assertEquals(schema.queries.selectOnes.size, 1)
    val selectOne = schema.queries.selectOnes.head
    assertEquals(selectOne.shapeId.toString, "example#GetWidget")
    assertEquals(selectOne.whereColumns.map(_.memberName), List("id"))
    assertEquals(
      selectOne.selectColumns.map(_.memberName),
      List("id", "foo", "bar", "created_at", "updated_at")
    )
  }

  test("DeriveSelectOne - joins many-to-one table as singular nested result") {
    val model = SqlTestModelBuilder.assemble(
      """
        |use smithplates.codegen.sql#DerivedStruct
        |use smithplates.codegen.sql#sqlAutoUuid
        |use smithplates.codegen.sql#sqlDeriveSelectOne
        |use smithplates.codegen.sql#sqlForeignKey
        |use smithplates.codegen.sql#sqlPrimaryKey
        |use smithplates.codegen.sql#sqlTable
        |
        |@sqlTable(name: "categories")
        |structure Category {
        |    @sqlPrimaryKey
        |    @sqlAutoUuid
        |    id: String
        |    name: String
        |}
        |
        |@sqlTable(name: "widgets")
        |structure Widget {
        |    @sqlPrimaryKey
        |    @sqlAutoUuid
        |    id: String
        |    @sqlForeignKey(references: "example#Category")
        |    @required
        |    category_id: String
        |    title: String
        |}
        |
        |@sqlDeriveSelectOne(
        |    targetTable: "example#Widget",
        |    joins: [{ table: "example#Category", tableAlias: "c" }]
        |)
        |operation GetWidget {
        |    input: DerivedStruct
        |    output: DerivedStruct
        |}
        |""".stripMargin
    )

    val schema    = SqlModelExtractor.extractOrThrow(model)
    val selectOne = schema.queries.selectOnes.head
    assertEquals(selectOne.joins.size, 1)
    assertEquals(selectOne.nestedResults.map(_.memberName), List("category"))
    assertEquals(selectOne.nestedResults.head.cardinality.toString, "Singular")
    assertEquals(selectOne.nestedResults.head.optional, false)
    assertEquals(
      selectOne.nestedResults.head.columns.map(_.memberName),
      List("id", "name")
    )
  }

  test("DeriveSelectOne - optional FK yields optional singular nested member") {
    val model = SqlTestModelBuilder.assemble(
      """
        |use smithplates.codegen.sql#DerivedStruct
        |use smithplates.codegen.sql#sqlAutoUuid
        |use smithplates.codegen.sql#sqlDeriveSelectOne
        |use smithplates.codegen.sql#sqlForeignKey
        |use smithplates.codegen.sql#sqlPrimaryKey
        |use smithplates.codegen.sql#sqlTable
        |
        |@sqlTable(name: "categories")
        |structure Category {
        |    @sqlPrimaryKey
        |    @sqlAutoUuid
        |    id: String
        |    name: String
        |}
        |
        |@sqlTable(name: "widgets")
        |structure Widget {
        |    @sqlPrimaryKey
        |    @sqlAutoUuid
        |    id: String
        |    title: String
        |    @sqlForeignKey(references: "example#Category")
        |    category_id: String
        |}
        |
        |@sqlDeriveSelectOne(
        |    targetTable: "example#Widget",
        |    joins: [{ table: "example#Category", type: "left", tableAlias: "c" }]
        |)
        |operation GetWidget {
        |    input: DerivedStruct
        |    output: DerivedStruct
        |}
        |""".stripMargin
    )

    val schema    = SqlModelExtractor.extractOrThrow(model)
    val selectOne = schema.queries.selectOnes.head
    assertEquals(selectOne.nestedResults.head.optional, true)
    assertEquals(selectOne.joins.head.joinType, SqlJoinType.Left)
  }

  test("DeriveSelectOne - joins one-to-many table as collection nested result") {
    val model = SqlTestModelBuilder.assemble(
      """
        |use smithplates.codegen.sql#DerivedStruct
        |use smithplates.codegen.sql#sqlAutoUuid
        |use smithplates.codegen.sql#sqlDeriveSelectOne
        |use smithplates.codegen.sql#sqlForeignKey
        |use smithplates.codegen.sql#sqlPrimaryKey
        |use smithplates.codegen.sql#sqlTable
        |
        |@sqlTable(name: "orders")
        |structure Order {
        |    @sqlPrimaryKey
        |    @sqlAutoUuid
        |    id: String
        |    label: String
        |}
        |
        |@sqlTable(name: "order_lines")
        |structure OrderLine {
        |    @sqlPrimaryKey
        |    @sqlAutoUuid
        |    id: String
        |    @sqlForeignKey(references: "example#Order")
        |    order_id: String
        |    sku: String
        |}
        |
        |@sqlDeriveSelectOne(
        |    targetTable: "example#Order",
        |    joins: [{ table: "example#OrderLine", tableAlias: "ol" }]
        |)
        |operation GetOrder {
        |    input: DerivedStruct
        |    output: DerivedStruct
        |}
        |""".stripMargin
    )

    val schema    = SqlModelExtractor.extractOrThrow(model)
    val selectOne = schema.queries.selectOnes.head
    assertEquals(selectOne.nestedResults.map(_.memberName), List("order_lines"))
    assertEquals(selectOne.nestedResults.head.cardinality.toString, "Collection")
  }

  test("DeriveSelectOne - transitive joins resolve through prior joined tables") {
    val model = SqlTestModelBuilder.assemble(
      """
        |use smithplates.codegen.sql#DerivedStruct
        |use smithplates.codegen.sql#sqlAutoUuid
        |use smithplates.codegen.sql#sqlDeriveSelectOne
        |use smithplates.codegen.sql#sqlForeignKey
        |use smithplates.codegen.sql#sqlPrimaryKey
        |use smithplates.codegen.sql#sqlTable
        |use smithy.api#required
        |
        |@sqlTable(name: "departments")
        |structure Department {
        |    @sqlPrimaryKey
        |    @sqlAutoUuid
        |    id: String
        |    name: String
        |}
        |
        |@sqlTable(name: "categories")
        |structure Category {
        |    @sqlPrimaryKey
        |    @sqlAutoUuid
        |    id: String
        |    name: String
        |    @sqlForeignKey(references: "example#Department")
        |    @required
        |    department_id: String
        |}
        |
        |@sqlTable(name: "widgets")
        |structure Widget {
        |    @sqlPrimaryKey
        |    @sqlAutoUuid
        |    id: String
        |    title: String
        |    @sqlForeignKey(references: "example#Category")
        |    @required
        |    category_id: String
        |}
        |
        |@sqlDeriveSelectOne(
        |    targetTable: "example#Widget",
        |    joins: [
        |        { table: "example#Category", tableAlias: "c" },
        |        { table: "example#Department", tableAlias: "d" }
        |    ]
        |)
        |operation GetWidget {
        |    input: DerivedStruct
        |    output: DerivedStruct
        |}
        |""".stripMargin
    )

    val schema    = SqlModelExtractor.extractOrThrow(model)
    val selectOne = schema.queries.selectOnes.head
    assertEquals(selectOne.joins.size, 2)
    assertEquals(selectOne.nestedResults.map(_.memberName), List("category", "department"))
    assertEquals(selectOne.nestedResults.map(_.optional), List(false, false))
    assertEquals(
      selectOne.joins(1).on.map(_.left.columnName),
      Some("department_id")
    )
    assertEquals(
      selectOne.joins(1).on.map(_.left.tableAlias),
      Some("c")
    )
    assertEquals(
      selectOne.joins(1).on.map(_.right.columnName),
      Some("id")
    )
    assertEquals(
      selectOne.joins(1).on.map(_.right.tableAlias),
      Some("d")
    )
  }

  test("DeriveSelectOne - reverse transitive joins resolve from department to widgets") {
    val model = SqlTestModelBuilder.assemble(
      """
        |use smithplates.codegen.sql#DerivedStruct
        |use smithplates.codegen.sql#sqlAutoUuid
        |use smithplates.codegen.sql#sqlDeriveSelectOne
        |use smithplates.codegen.sql#sqlForeignKey
        |use smithplates.codegen.sql#sqlPrimaryKey
        |use smithplates.codegen.sql#sqlTable
        |
        |@sqlTable(name: "departments")
        |structure Department {
        |    @sqlPrimaryKey
        |    @sqlAutoUuid
        |    id: String
        |    name: String
        |}
        |
        |@sqlTable(name: "categories")
        |structure Category {
        |    @sqlPrimaryKey
        |    @sqlAutoUuid
        |    id: String
        |    name: String
        |    @sqlForeignKey(references: "example#Department")
        |    department_id: String
        |}
        |
        |@sqlTable(name: "widgets")
        |structure Widget {
        |    @sqlPrimaryKey
        |    @sqlAutoUuid
        |    id: String
        |    title: String
        |    @sqlForeignKey(references: "example#Category")
        |    category_id: String
        |}
        |
        |@sqlDeriveSelectOne(
        |    targetTable: "example#Department",
        |    joins: [
        |        { table: "example#Category", tableAlias: "c" },
        |        { table: "example#Widget", tableAlias: "w" }
        |    ]
        |)
        |operation GetDepartment {
        |    input: DerivedStruct
        |    output: DerivedStruct
        |}
        |""".stripMargin
    )

    val schema    = SqlModelExtractor.extractOrThrow(model)
    val selectOne = schema.queries.selectOnes.head
    assertEquals(selectOne.joins.size, 2)
    assertEquals(selectOne.nestedResults.map(_.memberName), List("categories", "widgets"))
    assertEquals(
      selectOne.nestedResults.map(_.cardinality.toString),
      List("Collection", "Collection")
    )
    assertEquals(
      selectOne.joins(1).on.map(_.left.tableAlias),
      Some("c")
    )
    assertEquals(
      selectOne.joins(1).on.map(_.left.columnName),
      Some("id")
    )
    assertEquals(
      selectOne.joins(1).on.map(_.right.tableAlias),
      Some("w")
    )
    assertEquals(
      selectOne.joins(1).on.map(_.right.columnName),
      Some("category_id")
    )
  }

  test("DeriveSelectOne - fails when joins are declared but output is not DerivedStruct") {
    val result = SqlModelExtractor.extract(
      SqlTestModelBuilder.assemble(
        """
          |use smithplates.codegen.sql#DerivedStruct
          |use smithplates.codegen.sql#sqlAutoUuid
          |use smithplates.codegen.sql#sqlDeriveSelectOne
          |use smithplates.codegen.sql#sqlForeignKey
          |use smithplates.codegen.sql#sqlPrimaryKey
          |use smithplates.codegen.sql#sqlTable
          |
          |@sqlTable(name: "categories")
          |structure Category {
          |    @sqlPrimaryKey
          |    @sqlAutoUuid
          |    id: String
          |}
          |
          |@sqlTable(name: "widgets")
          |structure Widget {
          |    @sqlPrimaryKey
          |    @sqlAutoUuid
          |    id: String
          |    @sqlForeignKey(references: "example#Category")
          |    category_id: String
          |}
          |
          |@sqlDeriveSelectOne(
          |    targetTable: "example#Widget",
          |    joins: [{ table: "example#Category" }]
          |)
          |operation GetWidget {
          |    input: DerivedStruct
          |    output: Widget
          |}
          |""".stripMargin
      )
    )

    assert(result.isInvalid)
    assert(
      result.swap.toOption.get.exists {
        case InvalidDeriveSelectOne(_, reason) if reason.contains("DerivedStruct") => true
        case _                                                                     => false
      }
    )
  }

  test("DeriveSelectOne - fails when input is not DerivedStruct") {
    val result = SqlModelExtractor.extract(
      SqlTestModelBuilder.assemble(
        """
          |use smithplates.codegen.sql#sqlAutoUuid
          |use smithplates.codegen.sql#sqlDeriveSelectOne
          |use smithplates.codegen.sql#sqlPrimaryKey
          |use smithplates.codegen.sql#sqlTable
          |
          |@sqlTable(name: "widgets")
          |structure Widget {
          |    @sqlPrimaryKey
          |    @sqlAutoUuid
          |    id: String
          |    foo: String
          |}
          |
          |structure GetWidgetInput {
          |    id: String
          |}
          |
          |@sqlDeriveSelectOne(targetTable: "example#Widget")
          |operation GetWidget {
          |    input: GetWidgetInput
          |    output: Widget
          |}
          |""".stripMargin
      )
    )

    assert(result.isInvalid)
    assert(
      result.swap.toOption.get.exists {
        case InvalidDeriveSelectOne(_, reason) if reason.contains("smithplates.codegen.sql#DerivedStruct") => true
        case _                                                                                             => false
      }
    )
  }

  test("DeriveSelectOne - fails when output is not the target table structure") {
    val result = SqlModelExtractor.extract(
      SqlTestModelBuilder.assemble(
        """
          |use smithplates.codegen.sql#DerivedStruct
          |use smithplates.codegen.sql#sqlAutoUuid
          |use smithplates.codegen.sql#sqlDeriveSelectOne
          |use smithplates.codegen.sql#sqlPrimaryKey
          |use smithplates.codegen.sql#sqlTable
          |
          |@sqlTable(name: "widgets")
          |structure Widget {
          |    @sqlPrimaryKey
          |    @sqlAutoUuid
          |    id: String
          |    foo: String
          |}
          |
          |@sqlDeriveSelectOne(targetTable: "example#Widget")
          |operation GetWidget {
          |    input: DerivedStruct
          |    output: String
          |}
          |""".stripMargin
      )
    )

    assert(result.isInvalid)
    assert(
      result.swap.toOption.get.exists {
        case InvalidDeriveSelectOne(_, reason) if reason.contains("output must be the target @sqlTable structure") =>
          true
        case _                                                                                                     => false
      }
    )
  }

  test("DeriveSelectOne - fails when table has no primary key members") {
    val result = SqlModelExtractor.extract(
      SqlTestModelBuilder.assemble(
        """
          |use smithplates.codegen.sql#DerivedStruct
          |use smithplates.codegen.sql#sqlDeriveSelectOne
          |use smithplates.codegen.sql#sqlTable
          |
          |@sqlTable(name: "widgets")
          |structure Widget {
          |    foo: String
          |}
          |
          |@sqlDeriveSelectOne(targetTable: "example#Widget")
          |operation GetWidget {
          |    input: DerivedStruct
          |    output: Widget
          |}
          |""".stripMargin
      )
    )

    assert(result.isInvalid)
  }

  test("Update - requires primary key members and rejects database-managed members") {
    val model = SqlTestModelBuilder.assemble(
      """
        |use smithplates.codegen.sql#sqlAutoUuid
        |use smithplates.codegen.sql#sqlCreatedTimestamp
        |use smithplates.codegen.sql#sqlPrimaryKey
        |use smithplates.codegen.sql#sqlTable
        |use smithplates.codegen.sql#sqlUpdate
        |use smithplates.codegen.sql#sqlUpdatedTimestamp
        |
        |@sqlTable(name: "widgets")
        |structure Widget {
        |    @sqlPrimaryKey
        |    id: String
        |    foo: String
        |    @sqlCreatedTimestamp
        |    created_at: Timestamp
        |    @sqlUpdatedTimestamp
        |    updated_at: Timestamp
        |}
        |
        |@sqlUpdate(tableRef: "example#Widget")
        |structure WidgetUpdate {
        |    id: String
        |    foo: String
        |}
        |""".stripMargin
    )

    val schema = SqlModelExtractor.extractOrThrow(model)
    assertEquals(schema.queries.updates.size, 1)
    val update = schema.queries.updates.head
    assertEquals(update.setColumns.map(_.memberName), List("foo"))
    assertEquals(update.whereColumns.map(_.memberName), List("id"))
    assertEquals(update.returningColumns.map(_.columnName), List("updated_at"))
  }

  test("Update - fails when primary key member is missing") {
    val result = SqlModelExtractor.extract(
      SqlTestModelBuilder.assemble(
        """
          |use smithplates.codegen.sql#sqlPrimaryKey
          |use smithplates.codegen.sql#sqlTable
          |use smithplates.codegen.sql#sqlUpdate
          |
          |@sqlTable(name: "widgets")
          |structure Widget {
          |    @sqlPrimaryKey
          |    id: String
          |    foo: String
          |}
          |
          |@sqlUpdate(tableRef: "example#Widget")
          |structure WidgetUpdate {
          |    foo: String
          |}
          |""".stripMargin
      )
    )

    assert(result.isInvalid)
    assert(
      result.swap.toOption.get.exists {
        case QueryMissingRequiredMember(_, "id", "widgets", InvalidQueryTableReference.Kind.Update) => true
        case _                                                                                      => false
      }
    )
  }
}
