package com.jacoby6000.smithplates.http

import com.jacoby6000.smithplates.http.model.HttpInputMemberBinding
import com.jacoby6000.smithplates.http.model.HttpTimestampFormat
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

  test("HttpIrExtractor validates nested resources and for-resource input identifiers") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use aws.protocols#restJson1
          |use smithy.api#http
          |use smithy.api#tags
          |use smithy.api#readonly
          |
          |@restJson1
          |service AssetApi {
          |    version: "1"
          |    resources: [Project]
          |}
          |
          |resource Project {
          |    identifiers: { projectId: String }
          |    resources: [Asset]
          |}
          |
          |resource Asset {
          |    identifiers: { projectId: String, assetId: String }
          |    read: GetProjectAsset
          |}
          |
          |@tags(["assets"])
          |@http(method: "GET", uri: "/projects/{projectId}/assets/{assetId}", code: 200)
          |@readonly
          |operation GetProjectAsset {
          |    input: GetProjectAssetInput
          |    output: AssetOutput
          |}
          |
          |structure GetProjectAssetInput for Asset {
          |    @required
          |    @httpLabel
          |    $projectId
          |
          |    @required
          |    @httpLabel
          |    $assetId
          |}
          |
          |structure AssetOutput {
          |    @required
          |    assetId: String
          |}
          |""".stripMargin
    )

    val ir      = HttpIrExtractor.extractOrThrow(model)
    val service = ir.services.head
    assertEquals(service.resources.map(_.name).toSet, Set("Project", "Asset"))

    val getAsset = service.routeGroups.head.operations.find(_.name == "GetProjectAsset").get
    assertEquals(getAsset.inputBoundResource.map(_.getName), Some("Asset"))
    assertEquals(
      getAsset.inputMembers.map(member =>
        (member.name, member.pythonTypeName, member.resourceIdentifierName, member.binding)),
      List(
        ("projectId", "str", Some("projectId"), HttpInputMemberBinding.PathLabel()),
        ("assetId", "str", Some("assetId"), HttpInputMemberBinding.PathLabel())
      )
    )
  }

  test("HttpIrExtractor rejects unknown resource identifier members on for-resource inputs") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use aws.protocols#restJson1
          |use smithy.api#http
          |use smithy.api#tags
          |use smithy.api#readonly
          |use smithy.api#resourceIdentifier
          |
          |@restJson1
          |service AssetApi {
          |    version: "1"
          |    resources: [Asset]
          |}
          |
          |resource Asset {
          |    identifiers: { assetId: String }
          |    read: GetAsset
          |}
          |
          |@tags(["assets"])
          |@http(method: "GET", uri: "/assets/{assetId}", code: 200)
          |@readonly
          |operation GetAsset {
          |    input: GetAssetInput
          |    output: AssetOutput
          |}
          |
          |structure GetAssetInput for Asset {
          |    @required
          |    @httpLabel
          |    @resourceIdentifier("unknownIdentifier")
          |    customName: String
          |}
          |
          |structure AssetOutput {
          |    @required
          |    assetId: String
          |}
          |""".stripMargin
    )

    val error = intercept[IllegalArgumentException](HttpIrExtractor.extractOrThrow(model))
    assert(error.getMessage.contains("unknownIdentifier"))
    assert(error.getMessage.contains("assetId"))
  }

  test("HttpIrExtractor resolves @timestampFormat into HTTP member python types") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use aws.protocols#restJson1
          |use smithy.api#http
          |use smithy.api#tags
          |use smithy.api#readonly
          |use smithy.api#timestampFormat
          |
          |@restJson1
          |service AssetApi {
          |    version: "1"
          |    operations: [ListAssets]
          |}
          |
          |@tags(["assets"])
          |@http(method: "GET", uri: "/assets", code: 200)
          |@readonly
          |operation ListAssets {
          |    input: ListAssetsInput
          |    output: AssetListOutput
          |}
          |
          |structure ListAssetsInput {
          |    @httpQuery("since")
          |    @timestampFormat("epoch-seconds")
          |    since: Timestamp
          |
          |    @httpHeader("If-Modified-Since")
          |    @timestampFormat("http-date")
          |    ifModifiedSince: Timestamp
          |}
          |
          |structure AssetListOutput {
          |    @required
          |    items: String
          |}
          |""".stripMargin
    )

    val operation = HttpIrExtractor.extractOrThrow(model).services.head.routeGroups.head.operations.head
    assertEquals(
      operation.inputMembers.map(member =>
        (member.name, member.pythonTypeName, member.timestampFormat, member.binding)),
      List(
        ("since", "float", Some(HttpTimestampFormat.EpochSeconds), HttpInputMemberBinding.Query("since")),
        (
          "ifModifiedSince",
          "str",
          Some(HttpTimestampFormat.HttpDate),
          HttpInputMemberBinding.Header("If-Modified-Since")
        )
      )
    )
  }
}
