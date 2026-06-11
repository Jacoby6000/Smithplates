package com.jacoby6000.smithplates.http

import com.jacoby6000.smithplates.http.model.HttpInputMemberBinding
import com.jacoby6000.smithplates.http.model.HttpOperationBodyBinding
import com.jacoby6000.smithplates.http.model.HttpSerialization
import com.jacoby6000.smithplates.http.model.HttpTimestampFormat
import munit.FunSuite
import software.amazon.smithy.model.shapes.ShapeId

class HttpIrExtractorSpec extends FunSuite {
  test("HttpIrExtractor groups operations by @tags into route modules") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithplates.codegen.http#httpService
          |use smithy.api#http
          |use smithy.api#tags
          |
          |@httpService
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
          |    @httpLabel
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
    assertEquals(ir.services.head.routeGroups.head.tag, "v1_widgets")
  }

  test("HttpIrExtractor ignores services without @httpService") {
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
          |    @httpLabel
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
          |use smithplates.codegen.http#httpService
          |use smithy.api#http
          |use smithy.api#tags
          |use smithy.api#readonly
          |
          |@httpService
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
        (member.name, member.typeName, member.resourceIdentifierName, member.binding)),
      List(
        ("projectId", "String", Some("projectId"), HttpInputMemberBinding.PathLabel()),
        ("assetId", "String", Some("assetId"), HttpInputMemberBinding.PathLabel())
      )
    )
  }

  test("HttpIrExtractor rejects unknown resource identifier members on for-resource inputs") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithplates.codegen.http#httpService
          |use smithy.api#http
          |use smithy.api#tags
          |use smithy.api#readonly
          |use smithy.api#resourceIdentifier
          |
          |@httpService
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

  test("HttpIrExtractor resolves @timestampFormat into neutral member types") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithplates.codegen.http#httpService
          |use smithy.api#http
          |use smithy.api#tags
          |use smithy.api#readonly
          |use smithy.api#timestampFormat
          |
          |@httpService
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
      operation.inputMembers.map(member => (member.name, member.typeName, member.timestampFormat, member.binding)),
      List(
        (
          "ifModifiedSince",
          "Timestamp",
          Some(HttpTimestampFormat.HttpDate),
          HttpInputMemberBinding.Header("If-Modified-Since")
        ),
        ("since", "Timestamp", Some(HttpTimestampFormat.EpochSeconds), HttpInputMemberBinding.Query("since"))
      )
    )
  }

  test("HttpIrExtractor extracts nested structures and Smithy tagged unions for HTTP bodies") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithplates.codegen.http#httpService
          |use smithy.api#http
          |use smithy.api#tags
          |use smithy.api#readonly
          |
          |@httpService
          |service ContentApi {
          |    version: "1"
          |    operations: [CreateContent, GetContent]
          |}
          |
          |@tags(["content"])
          |@http(method: "POST", uri: "/content", code: 201)
          |operation CreateContent {
          |    input: CreateContentInput
          |    output: ContentOutput
          |}
          |
          |@tags(["content"])
          |@http(method: "GET", uri: "/content/{contentId}", code: 200)
          |@readonly
          |operation GetContent {
          |    input: GetContentInput
          |    output: ContentOutput
          |}
          |
          |structure PostalAddress {
          |    @required
          |    street: String
          |
          |    @required
          |    city: String
          |}
          |
          |structure ImageAsset {
          |    @required
          |    url: String
          |
          |    @required
          |    width: Integer
          |}
          |
          |union MediaAttachment {
          |    caption: String
          |    image: ImageAsset
          |}
          |
          |structure CreateContentInput {
          |    @required
          |    title: String
          |
          |    @required
          |    authorAddress: PostalAddress
          |
          |    @required
          |    attachment: MediaAttachment
          |}
          |
          |structure GetContentInput {
          |    @required
          |    @httpLabel
          |    contentId: String
          |}
          |
          |structure ContentOutput {
          |    @required
          |    contentId: String
          |
          |    @required
          |    title: String
          |
          |    @required
          |    authorAddress: PostalAddress
          |
          |    @required
          |    attachment: MediaAttachment
          |}
          |""".stripMargin
    )

    val service         = HttpIrExtractor.extractOrThrow(model).services.head
    assertEquals(
      service.structures.map(_.name).toSet,
      Set("PostalAddress", "ImageAsset", "CreateContentInput", "ContentOutput")
    )
    assertEquals(service.unions.map(_.name), List("MediaAttachment"))
    val mediaAttachment = service.unions.head
    assertEquals(
      mediaAttachment.members.map(member => (member.name, member.typeName)),
      List(("caption", "String"), ("image", "ImageAsset"))
    )
    val createInput     = service.structures.find(_.name == "CreateContentInput").get
    assertEquals(
      createInput.members.map(member => (member.name, member.typeName)),
      List(
        ("title", "String"),
        ("authorAddress", "PostalAddress"),
        ("attachment", "MediaAttachment")
      )
    )
  }

  test("HttpIrExtractor resolves mixed header, path label, and body payload bindings on POST") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithplates.codegen.http#httpService
          |use smithy.api#http
          |use smithy.api#httpPayload
          |use smithy.api#tags
          |
          |@httpService
          |service WarehouseApi {
          |    version: "1"
          |    operations: [CreateShelfItem, AssignShelfSku]
          |}
          |
          |@tags(["warehouse"])
          |@http(method: "POST", uri: "/warehouses/{warehouseId}/shelves/{shelfId}/items", code: 201)
          |operation CreateShelfItem {
          |    input: CreateShelfItemInput
          |    output: ShelfItemOutput
          |}
          |
          |@tags(["warehouse"])
          |@http(method: "POST", uri: "/shelves/{shelfId}/skus", code: 201)
          |operation AssignShelfSku {
          |    input: AssignShelfSkuInput
          |    output: ShelfSkuOutput
          |}
          |
          |structure ItemDetails {
          |    @required
          |    name: String
          |}
          |
          |structure CreateShelfItemInput {
          |    @httpHeader("X-Idempotency-Key")
          |    idempotencyKey: String
          |
          |    @required
          |    @httpLabel
          |    warehouseId: String
          |
          |    @required
          |    @httpLabel
          |    shelfId: String
          |
          |    @httpPayload
          |    @required
          |    details: ItemDetails
          |}
          |
          |structure AssignShelfSkuInput {
          |    @httpHeader("X-Request-Id")
          |    requestId: String
          |
          |    @required
          |    @httpLabel
          |    shelfId: String
          |
          |    @required
          |    sku: String
          |}
          |
          |structure ShelfItemOutput {
          |    @required
          |    itemId: String
          |
          |    @required
          |    name: String
          |}
          |
          |structure ShelfSkuOutput {
          |    @required
          |    shelfId: String
          |
          |    @required
          |    sku: String
          |}
          |""".stripMargin
    )

    val operations = HttpIrExtractor.extractOrThrow(model).services.head.routeGroups.head.operations
    val createItem = operations.find(_.name == "CreateShelfItem").get
    val assignSku  = operations.find(_.name == "AssignShelfSku").get
    assertEquals(
      createItem.inputMembers.map(member => (member.name, member.binding)),
      List(
        ("idempotencyKey", HttpInputMemberBinding.Header("X-Idempotency-Key")),
        ("warehouseId", HttpInputMemberBinding.PathLabel()),
        ("shelfId", HttpInputMemberBinding.PathLabel()),
        ("details", HttpInputMemberBinding.Payload())
      )
    )
    createItem.bodyBinding match {
      case HttpOperationBodyBinding.Members(members) =>
        assertEquals(members.map(_.name), List("details"))
      case other                                     =>
        fail(s"CreateShelfItem should use member payload binding, got $other")
    }
    assertEquals(
      assignSku.inputMembers.map(member => (member.name, member.binding)),
      List(
        ("requestId", HttpInputMemberBinding.Header("X-Request-Id")),
        ("shelfId", HttpInputMemberBinding.PathLabel()),
        ("sku", HttpInputMemberBinding.Payload())
      )
    )
  }

  test("HttpIrExtractor resolves document and member HTTP body bindings") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithplates.codegen.http#httpService
          |use smithy.api#http
          |use smithy.api#tags
          |
          |@httpService
          |service ProjectApi {
          |    version: "1"
          |    operations: [CreateProject, CreateProjectTask]
          |}
          |
          |@tags(["projects"])
          |@http(method: "POST", uri: "/projects", code: 201)
          |operation CreateProject {
          |    input: CreateProjectInput
          |    output: ProjectOutput
          |}
          |
          |@tags(["project_tasks"])
          |@http(method: "POST", uri: "/projects/{projectId}/tasks", code: 201)
          |operation CreateProjectTask {
          |    input: CreateProjectTaskInput
          |    output: TaskOutput
          |}
          |
          |structure CreateProjectInput {
          |    @required
          |    name: String
          |}
          |
          |structure CreateProjectTaskInput {
          |    @required
          |    @httpLabel
          |    projectId: String
          |
          |    @required
          |    title: String
          |}
          |
          |structure ProjectOutput {
          |    @required
          |    projectId: String
          |
          |    @required
          |    name: String
          |}
          |
          |structure TaskOutput {
          |    @required
          |    projectId: String
          |
          |    @required
          |    taskId: String
          |
          |    @required
          |    title: String
          |}
          |""".stripMargin
    )

    val service    = HttpIrExtractor.extractOrThrow(model).services.head
    val create     = service.routeGroups.flatMap(_.operations).find(_.name == "CreateProject").get
    val createTask = service.routeGroups.flatMap(_.operations).find(_.name == "CreateProjectTask").get
    assertEquals(
      create.bodyBinding,
      HttpOperationBodyBinding.Document(ShapeId.from("example#CreateProjectInput"))
    )
    create.bodyBinding match {
      case HttpOperationBodyBinding.Members(members) =>
        fail(s"CreateProject should use document body binding, got members=$members")
      case _                                         => ()
    }
    createTask.bodyBinding match {
      case HttpOperationBodyBinding.Members(members) =>
        assertEquals(members.map(_.name), List("title"))
      case other                                     =>
        fail(s"CreateProjectTask should use member payload binding, got $other")
    }
    assertEquals(
      service.structures.map(_.name).toSet,
      Set("CreateProjectInput", "ProjectOutput", "TaskOutput")
    )
  }

  test("HttpIrExtractor warns when route input members are declared out of order") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithplates.codegen.http#httpService
          |use smithy.api#http
          |use smithy.api#tags
          |use smithy.api#readonly
          |
          |@httpService
          |service WidgetApi {
          |    version: "1"
          |    operations: [InspectWidget]
          |}
          |
          |@tags(["widgets"])
          |@http(method: "GET", uri: "/v1/widgets/{id}", code: 200)
          |@readonly
          |operation InspectWidget {
          |    input: InspectWidgetInput
          |    output: WidgetOutput
          |}
          |
          |structure InspectWidgetInput {
          |    @httpQuery("category")
          |    category: String
          |
          |    @required
          |    @httpLabel
          |    id: String
          |
          |    @httpHeader("X-Region")
          |    region: String
          |}
          |
          |structure WidgetOutput {
          |    @required
          |    id: String
          |}
          |""".stripMargin
    )

    val ir        = HttpIrExtractor.extractOrThrow(model)
    val operation = ir.services.head.routeGroups.head.operations.head
    assertEquals(operation.inputMembers.map(_.name), List("region", "id", "category"))
    assertEquals(ir.warnings.size, 1)
    assert(ir.warnings.head.message.contains("declares route input members out of order"))
  }

  test("HttpIrExtractor defaults @httpService serialization to json") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithplates.codegen.http#httpService
          |use smithy.api#http
          |use smithy.api#tags
          |
          |@httpService
          |service WidgetApi {
          |    version: "1"
          |    operations: [GetWidget]
          |}
          |
          |@tags(["v1_widgets"])
          |@http(method: "GET", uri: "/v1/widgets", code: 200)
          |operation GetWidget {
          |    input: Unit
          |    output: WidgetOutput
          |}
          |
          |structure WidgetOutput {
          |    @required
          |    id: String
          |}
          |""".stripMargin
    )

    val service = HttpIrExtractor.extractOrThrow(model).services.head
    assertEquals(service.serialization, HttpSerialization.Json)
  }

  test("HttpIrExtractor rejects unsupported @httpService serialization") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithplates.codegen.http#httpService
          |use smithy.api#http
          |use smithy.api#tags
          |
          |@httpService(serialization: "xml")
          |service WidgetApi {
          |    version: "1"
          |    operations: [GetWidget]
          |}
          |
          |@tags(["v1_widgets"])
          |@http(method: "GET", uri: "/v1/widgets", code: 200)
          |operation GetWidget {
          |    input: Unit
          |    output: WidgetOutput
          |}
          |
          |structure WidgetOutput {
          |    @required
          |    id: String
          |}
          |""".stripMargin
    )

    val error = intercept[IllegalArgumentException](HttpIrExtractor.extractOrThrow(model))
    assert(error.getMessage.contains("serialization"))
  }

  test("HttpIrExtractor extracts service-level errors with @httpError status codes") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithplates.codegen.http#httpService
          |use smithy.api#error
          |use smithy.api#http
          |use smithy.api#httpError
          |use smithy.api#tags
          |
          |@httpService
          |service WidgetApi {
          |    version: "1"
          |    operations: [GetWidget]
          |    errors: [WidgetNotFound]
          |}
          |
          |@error("client")
          |@httpError(404)
          |structure WidgetNotFound {
          |    @required
          |    message: String
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
          |    id: String
          |}
          |
          |structure WidgetOutput {
          |    @required
          |    id: String
          |}
          |""".stripMargin
    )

    val service = HttpIrExtractor.extractOrThrow(model).services.head
    assertEquals(service.serviceErrors.map(error => (error.name, error.statusCode)), List(("WidgetNotFound", 404)))
  }

  test("HttpIrExtractor rejects service errors without @httpError") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithplates.codegen.http#httpService
          |use smithy.api#error
          |use smithy.api#http
          |use smithy.api#tags
          |
          |@httpService
          |service WidgetApi {
          |    version: "1"
          |    operations: [GetWidget]
          |    errors: [WidgetNotFound]
          |}
          |
          |@error("client")
          |structure WidgetNotFound {
          |    @required
          |    message: String
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
          |    id: String
          |}
          |
          |structure WidgetOutput {
          |    @required
          |    id: String
          |}
          |""".stripMargin
    )

    val error = intercept[IllegalArgumentException](HttpIrExtractor.extractOrThrow(model))
    assert(error.getMessage.contains("@httpError"))
  }
}
