package com.jacoby6000.smithy.sql

import software.amazon.smithy.model.Model

class SqlServiceExtractorSpec extends munit.FunSuite {
  private val tableUses =
    """
      |use jacoby6000.codegen.sql#sqlPrimaryKey
      |use jacoby6000.codegen.sql#sqlTable
      |""".stripMargin

  private val minimalTableStructure =
    """
      |@sqlTable(name: "items")
      |structure Item {
      |    @sqlPrimaryKey
      |    id: String
      |}
      |""".stripMargin

  private def assembleServiceModel(uses: String, shapes: String): Model =
    SqlTestModelBuilder.assemble(
      s"""$tableUses
         |$uses
         |
         |$minimalTableStructure
         |$shapes
         |""".stripMargin
    )

  test("Service - extracts operations with input, output, and errors") {
    val schema = SqlModelExtractor.extractOrThrow(
      assembleServiceModel(
        """
          |use jacoby6000.codegen.sql#sqlService
          |use smithy.api#error
          |""".stripMargin,
        """
          |@error("client")
          |structure ItemNotFound {
          |    @required
          |    message: String
          |}
          |
          |structure GetItemInput {
          |    @required
          |    id: String
          |}
          |
          |structure GetItemOutput {
          |    @required
          |    name: String
          |}
          |
          |operation GetItem {
          |    input: GetItemInput
          |    output: GetItemOutput
          |    errors: [ItemNotFound]
          |}
          |
          |operation ListItems {
          |    input: Unit
          |    output: GetItemOutput
          |}
          |
          |@sqlService
          |service ItemRepository {
          |    version: "1"
          |    operations: [GetItem, ListItems]
          |}
          |""".stripMargin
      )
    )

    assertEquals(schema.services.size, 1)
    val service = schema.services.head
    assertEquals(service.shapeId.toString, "example#ItemRepository")
    assertEquals(service.version, "1")
    assertEquals(service.operations.map(_.name), List("GetItem", "ListItems"))

    val getItem = service.operations.head
    assertEquals(getItem.inputShape.toString, "example#GetItemInput")
    assertEquals(getItem.outputShape.map(_.toString), Some("example#GetItemOutput"))
    assertEquals(getItem.errorShapes.map(_.toString), List("example#ItemNotFound"))

    val listItems = service.operations(1)
    assertEquals(listItems.inputShape.toString, "smithy.api#Unit")
    assertEquals(listItems.errorShapes, Nil)
  }

  test("Service - fails when no operations are declared") {
    val result = SqlModelExtractor.extract(
      assembleServiceModel(
        """
          |use jacoby6000.codegen.sql#sqlService
          |""".stripMargin,
        """
          |@sqlService
          |service EmptyRepository {
          |    version: "1"
          |    operations: []
          |}
          |""".stripMargin
      )
    )

    assert(result.isInvalid)
    assert(result.swap.toOption.get.exists(_.isInstanceOf[EmptySqlService]))
  }

  test("Service - fails when resources are declared") {
    val result = SqlModelExtractor.extract(
      assembleServiceModel(
        """
          |use jacoby6000.codegen.sql#sqlService
          |""".stripMargin,
        """
          |resource ItemResource {
          |    identifiers: {id: String}
          |    read: GetItem
          |}
          |
          |operation GetItem {
          |    input: Unit
          |    output: Unit
          |}
          |
          |@sqlService
          |service ItemRepository {
          |    version: "1"
          |    resources: [ItemResource]
          |}
          |""".stripMargin
      )
    )

    assert(result.isInvalid)
    assert(result.swap.toOption.get.exists(_.isInstanceOf[InvalidSqlService]))
  }

  test("Service - ignores services without @sqlService") {
    val schema = SqlModelExtractor.extractOrThrow(
      assembleServiceModel(
        "",
        """
          |operation Ping {
          |    input: Unit
          |    output: Unit
          |}
          |
          |service PlainService {
          |    version: "1"
          |    operations: [Ping]
          |}
          |""".stripMargin
      )
    )

    assertEquals(schema.services, Nil)
  }
}
