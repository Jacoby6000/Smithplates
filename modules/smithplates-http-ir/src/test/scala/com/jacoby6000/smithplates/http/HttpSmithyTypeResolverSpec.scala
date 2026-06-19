package com.jacoby6000.smithplates.http

import munit.FunSuite
import software.amazon.smithy.model.shapes.ShapeId

class HttpSmithyTypeResolverSpec extends FunSuite {
  test("HttpSmithyTypeResolver resolves string aliases to String") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithplates.codegen.http#httpService
          |use smithy.api#http
          |use smithy.api#pattern
          |use smithy.api#tags
          |
          |@pattern("^[a-z0-9-]+$")
          |string WidgetId
          |
          |@httpService
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
          |    @httpLabel
          |    id: WidgetId
          |}
          |
          |structure WidgetOutput {
          |    @required
          |    id: WidgetId
          |}
          |""".stripMargin
    )

    val serviceShape = ShapeId.fromParts("example", "WidgetApi")
    val inputShape   = model.expectShape(ShapeId.fromParts("example", "GetWidgetInput")).asStructureShape.get()
    val idMember     = inputShape.getMember("id").get()

    HttpSmithyTypeResolver.resolveMemberType(model, serviceShape, "GetWidget", "id", idMember) match {
      case cats.data.Validated.Valid(memberType) =>
        assertEquals(memberType.typeName, "String")
      case cats.data.Validated.Invalid(errors)   =>
        fail(errors.map(_.message).toList.mkString("; "))
    }
  }
}
