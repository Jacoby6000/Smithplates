package com.jacoby6000.smithplates.http

import com.jacoby6000.smithplates.http.model.HttpTimestampFormat
import munit.FunSuite
import software.amazon.smithy.model.shapes.ShapeId

class SmithyHttpTimestampFormatResolverSpec extends FunSuite {
  private val serviceShape = ShapeId.from("example#AssetApi")

  test("member @timestampFormat overrides target shape format") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |@timestampFormat("epoch-seconds")
          |timestamp EpochTimestamp
          |
          |structure ListAssetsInput {
          |    @timestampFormat("date-time")
          |    since: EpochTimestamp
          |}
          |""".stripMargin
    )

    val structure = model.getShape(ShapeId.from("example#ListAssetsInput")).get().asStructureShape().get()
    val member    = structure.getAllMembers.get("since")
    val result    =
      SmithyHttpTimestampFormatResolver.resolve(model, serviceShape, "ListAssets", "since", member).toEither
    assertEquals(result, Right(HttpTimestampFormat.DateTime))
  }

  test("supports http-date for HTTP header timestamps") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithy.api#timestampFormat
          |
          |structure GetAssetInput {
          |    @timestampFormat("http-date")
          |    ifModifiedSince: Timestamp
          |}
          |""".stripMargin
    )

    val structure = model.getShape(ShapeId.from("example#GetAssetInput")).get().asStructureShape().get()
    val member    = structure.getAllMembers.get("ifModifiedSince")
    val result    =
      SmithyHttpTimestampFormatResolver.resolve(model, serviceShape, "GetAsset", "ifModifiedSince", member).toEither
    assertEquals(result, Right(HttpTimestampFormat.HttpDate))
  }
}
