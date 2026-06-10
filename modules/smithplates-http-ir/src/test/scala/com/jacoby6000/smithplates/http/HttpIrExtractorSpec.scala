package com.jacoby6000.smithplates.http

import munit.FunSuite

class HttpIrExtractorSpec extends FunSuite {
  test("HttpIrExtractor groups operations by @tags into route modules") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use aws.protocols#restJson1
          |use smithy.api#http
          |use smithy.api#tags
          |
          |@restJson1
          |service WidgetApi {
          |    version: "1"
          |    operations: [GetWidget, ListWidgets]
          |}
          |
          |@tags(["v1_widgets"])
          |@http(method: "GET", uri: "/v1/widgets/{id}", code: 200)
          |operation GetWidget {
          |    input: GetWidgetInput
          |    output: WidgetOutput
          |}
          |
          |@tags(["v1_widgets"])
          |@http(method: "GET", uri: "/v1/widgets", code: 200)
          |operation ListWidgets {
          |    input: Unit
          |    output: WidgetListOutput
          |}
          |
          |structure GetWidgetInput {
          |    @required
          |    id: String
          |}
          |
          |structure WidgetOutput {
          |    @required
          |    id: String
          |}
          |
          |structure WidgetListOutput {
          |    @required
          |    items: String
          |}
          |""".stripMargin
    )

    val ir = HttpIrExtractor.extractOrThrow(model)
    assertEquals(ir.services.length, 1)
    assertEquals(ir.services.head.routeGroups.map(_.tag), List("v1_widgets"))
    assertEquals(ir.services.head.routeGroups.head.operations.map(_.name), List("GetWidget", "ListWidgets"))
    assertEquals(ir.services.head.routeGroups.head.apiModuleName, "v1_widgets_api")
  }

  test("HttpIrExtractor ignores services without @restJson1") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithy.api#http
          |use smithy.api#tags
          |
          |service WidgetApi {
          |    version: "1"
          |    operations: [GetWidget]
          |}
          |
          |@tags(["v1_widgets"])
          |@http(method: "GET", uri: "/v1/widgets/{id}", code: 200)
          |operation GetWidget {
          |    input: GetWidgetInput
          |    output: WidgetOutput
          |}
          |
          |structure GetWidgetInput {
          |    @required
          |    id: String
          |}
          |
          |structure WidgetOutput {
          |    @required
          |    id: String
          |}
          |""".stripMargin
    )

    val ir = HttpIrExtractor.extractOrThrow(model)
    assertEquals(ir.services, Nil)
  }
}
