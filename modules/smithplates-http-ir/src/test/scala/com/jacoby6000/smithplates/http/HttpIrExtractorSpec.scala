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

  test("HttpIrExtractor collects resource operations bindings on nested resources") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithplates.codegen.http#httpService
          |use smithy.api#http
          |use smithy.api#httpHeader
          |use smithy.api#tags
          |use smithy.api#readonly
          |
          |@httpService
          |service ProjectApi {
          |    version: "1"
          |    resources: [Project]
          |}
          |
          |resource Project {
          |    identifiers: { projectId: String }
          |    resources: [ProjectAssets]
          |}
          |
          |resource ProjectAssets {
          |    identifiers: { projectId: String }
          |    resources: [Asset]
          |}
          |
          |resource Asset {
          |    identifiers: { projectId: String, assetId: String }
          |    read: GetProjectAsset
          |    operations: [GetProjectAssetContent, ListAssetEvents]
          |}
          |
          |@tags(["project_assets"])
          |@http(method: "GET", uri: "/projects/{projectId}/assets/{assetId}", code: 200)
          |@readonly
          |operation GetProjectAsset {
          |    input: GetProjectAssetInput
          |    output: AssetOutput
          |}
          |
          |@tags(["project_assets"])
          |@http(method: "GET", uri: "/projects/{projectId}/assets/{assetId}/content", code: 302)
          |@readonly
          |operation GetProjectAssetContent {
          |    input: GetProjectAssetContentInput
          |    output: Redirect
          |}
          |
          |@tags(["project_assets"])
          |@http(method: "GET", uri: "/projects/{projectId}/assets/{assetId}/events", code: 200)
          |@readonly
          |operation ListAssetEvents {
          |    input: ListAssetEventsInput
          |    output: AssetEventListOutput
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
          |structure GetProjectAssetContentInput for Asset {
          |    @required
          |    @httpLabel
          |    $projectId
          |
          |    @required
          |    @httpLabel
          |    $assetId
          |}
          |
          |structure ListAssetEventsInput for Asset {
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
          |
          |structure AssetEventListOutput {
          |    @required
          |    items: String
          |}
          |
          |structure Redirect {
          |    @httpHeader("Location")
          |    @required
          |    url: String
          |}
          |""".stripMargin
    )

    val service      = HttpIrExtractor.extractOrThrow(model).services.head
    val operationIds = service.routeGroups.flatMap(_.operations).map(_.name).toSet
    assertEquals(
      operationIds,
      Set("GetProjectAsset", "GetProjectAssetContent", "ListAssetEvents")
    )

    val contentOp = service.routeGroups.flatMap(_.operations).find(_.name == "GetProjectAssetContent").get
    assertEquals(contentOp.inputBoundResource.map(_.getName), Some("Asset"))
    assertEquals(contentOp.successStatusCode, 302)
    assertEquals(contentOp.responseBinding.successVariant.map(_.headerBindings), Some(List(("url", "Location"))))
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

  test("HttpIrExtractor rejects service errors without @httpError or @httpProblem(code)") {
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
    assert(error.getMessage.contains("@httpProblem(code"))
  }

  test("HttpIrExtractor rejects service errors when @httpError and @httpProblem(code) disagree") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithplates.codegen.http#httpService
          |use smithplates.codegen.http#httpProblem
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
          |@httpProblem(title: "Widget not found", code: 404)
          |@error("client")
          |@httpError(500)
          |structure WidgetNotFound {
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
    assert(error.getMessage.contains("different status codes"))
  }

  test("HttpIrExtractor resolves service error status from @httpProblem(code) without @httpError") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithplates.codegen.http#httpService
          |use smithplates.codegen.http#httpProblem
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
          |@httpProblem(title: "Widget not found", code: 404)
          |@error("client")
          |structure WidgetNotFound {
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

    val serviceError = HttpIrExtractor.extractOrThrow(model).services.head.serviceErrors.head
    assertEquals(serviceError.statusCode, 404)
    assertEquals(serviceError.problemBinding.map(_.title), Some("Widget not found"))
  }

  test("HttpIrExtractor resolves operation errors and response binding variants") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithplates.codegen.http#httpService
          |use smithy.api#error
          |use smithy.api#http
          |use smithy.api#httpError
          |use smithy.api#httpPayload
          |use smithy.api#tags
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
          |    output: GetWidget200
          |    errors: [GetWidget404]
          |}
          |
          |structure GetWidgetInput {
          |    @required
          |    @httpLabel
          |    id: String
          |}
          |
          |structure GetWidget200 {
          |    @httpPayload
          |    @required
          |    body: WidgetOutput
          |}
          |
          |structure WidgetOutput {
          |    @required
          |    id: String
          |}
          |
          |@error("client")
          |@httpError(404)
          |structure GetWidget404 {
          |    @required
          |    message: String
          |}
          |""".stripMargin
    )

    val operation = HttpIrExtractor.extractOrThrow(model).services.head.routeGroups.head.operations.head
    assertEquals(operation.operationErrors.map(error => (error.name, error.statusCode)), List(("GetWidget404", 404)))
    assertEquals(
      operation.responseBinding.allVariants.map(variant => (variant.variantTypeName, variant.statusCode)),
      List(("WidgetOutput", 200), ("GetWidget404", 404))
    )
  }

  test("HttpIrExtractor resolves output @httpHeader redirect bindings") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithplates.codegen.http#httpService
          |use smithy.api#http
          |use smithy.api#httpHeader
          |use smithy.api#tags
          |
          |@httpService
          |service AssetApi {
          |    version: "1"
          |    operations: [GetAssetContent]
          |}
          |
          |@tags(["assets"])
          |@http(method: "GET", uri: "/assets/{id}/content", code: 302)
          |operation GetAssetContent {
          |    input: GetAssetContentInput
          |    output: Redirect
          |}
          |
          |structure GetAssetContentInput {
          |    @required
          |    @httpLabel
          |    id: String
          |}
          |
          |structure Redirect {
          |    @httpHeader("Location")
          |    @required
          |    url: String
          |}
          |""".stripMargin
    )

    val operation = HttpIrExtractor.extractOrThrow(model).services.head.routeGroups.head.operations.head
    val success   = operation.responseBinding.successVariant.get
    assertEquals(success.variantTypeName, "Redirect")
    assertEquals(success.statusCode, 302)
    assertEquals(success.mediaType, None)
    assertEquals(success.headerBindings, List(("url", "Location")))
  }

  test("HttpIrExtractor implies Content-Type from @httpProblem on operation error variants") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithplates.codegen.http#httpService
          |use smithplates.codegen.http#httpProblem
          |use smithy.api#error
          |use smithy.api#http
          |use smithy.api#httpPayload
          |use smithy.api#tags
          |
          |@httpService
          |service AssetApi {
          |    version: "1"
          |    operations: [UpdateAssetState]
          |}
          |
          |@tags(["assets"])
          |@http(method: "PATCH", uri: "/assets/{id}/state", code: 200)
          |operation UpdateAssetState {
          |    input: UpdateAssetStateInput
          |    output: GetAsset200
          |    errors: [UpdateAssetState409]
          |}
          |
          |structure UpdateAssetStateInput {
          |    @required
          |    @httpLabel
          |    id: String
          |
          |    @httpPayload
          |    @required
          |    body: AssetStatePatch
          |}
          |
          |structure AssetStatePatch {
          |    @required
          |    status: String
          |}
          |
          |structure GetAsset200 {
          |    @httpPayload
          |    @required
          |    body: AssetOutput
          |}
          |
          |structure AssetOutput {
          |    @required
          |    id: String
          |}
          |
          |structure Problem {
          |    @required
          |    title: String
          |}
          |
          |@httpProblem(
          |    type: "https://example.com/errors/state-conflict"
          |    title: "Asset state conflict"
          |    code: 409
          |)
          |@error("client")
          |structure UpdateAssetState409 {
          |    @httpPayload
          |    @required
          |    body: Problem
          |}
          |""".stripMargin
    )

    val operation = HttpIrExtractor.extractOrThrow(model).services.head.routeGroups.head.operations.head
    val problem   = operation.responseBinding.errorVariants.head
    assertEquals(problem.variantTypeName, "Problem")
    assertEquals(problem.mediaType, Some("application/json"))
    assertEquals(problem.staticHeaders, List(("Content-Type", "application/problem+json")))
  }

  test("HttpIrExtractor resolves output @httpStaticHeader traits on response variants") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithplates.codegen.http#httpService
          |use smithplates.codegen.http#httpStaticHeader
          |use smithy.api#error
          |use smithy.api#http
          |use smithy.api#httpError
          |use smithy.api#httpPayload
          |use smithy.api#tags
          |
          |@httpService
          |service AssetApi {
          |    version: "1"
          |    operations: [UpdateAssetState]
          |}
          |
          |@tags(["assets"])
          |@http(method: "PATCH", uri: "/assets/{id}/state", code: 200)
          |operation UpdateAssetState {
          |    input: UpdateAssetStateInput
          |    output: GetAsset200
          |    errors: [UpdateAssetState409]
          |}
          |
          |structure UpdateAssetStateInput {
          |    @required
          |    @httpLabel
          |    id: String
          |
          |    @httpPayload
          |    @required
          |    body: AssetStatePatch
          |}
          |
          |structure AssetStatePatch {
          |    @required
          |    status: String
          |}
          |
          |structure GetAsset200 {
          |    @httpPayload
          |    @required
          |    body: AssetOutput
          |}
          |
          |structure AssetOutput {
          |    @required
          |    id: String
          |}
          |
          |@httpStaticHeader(name: "Content-Type", value: "application/problem+json")
          |structure Problem {
          |    @required
          |    title: String
          |}
          |
          |@error("client")
          |@httpError(409)
          |structure UpdateAssetState409 {
          |    @httpPayload
          |    @required
          |    body: Problem
          |}
          |""".stripMargin
    )

    val operation = HttpIrExtractor.extractOrThrow(model).services.head.routeGroups.head.operations.head
    val problem   = operation.responseBinding.errorVariants.head
    assertEquals(problem.variantTypeName, "Problem")
    assertEquals(problem.mediaType, Some("application/json"))
    assertEquals(problem.staticHeaders, List(("Content-Type", "application/problem+json")))
  }

  test("HttpIrExtractor extracts @httpProblem bindings on service errors") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithplates.codegen.http#httpService
          |use smithplates.codegen.http#httpProblem
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
          |@httpProblem(
          |    type: "https://example.com/errors/widget-not-found"
          |    title: "Widget not found"
          |    detail: "The requested widget does not exist."
          |    code: 404
          |)
          |@error("client")
          |structure WidgetNotFound {
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

    val ir           = HttpIrExtractor.extractOrThrow(model)
    val serviceError = ir.services.head.serviceErrors.head
    assertEquals(serviceError.name, "WidgetNotFound")
    assertEquals(
      serviceError.problemBinding,
      Some(
        com.jacoby6000.smithplates.http.model.HttpProblemBinding(
          problemType = "https://example.com/errors/widget-not-found",
          title = "Widget not found",
          defaultDetail = Some("The requested widget does not exist.")
        )
      )
    )
    assertEquals(ir.warnings, Nil)
  }

  test("HttpIrExtractor warns when @httpProblem type is not an HTTPS URL") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithplates.codegen.http#httpService
          |use smithplates.codegen.http#httpProblem
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
          |@httpProblem(title: "Widget not found", code: 404)
          |@error("client")
          |structure WidgetNotFound {
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
    assertEquals(ir.services.head.serviceErrors.head.problemBinding.map(_.problemType), Some("about:blank"))
    assertEquals(ir.warnings.length, 1)
    assert(ir.warnings.head.message.contains("about:blank"))
    assert(ir.warnings.head.message.contains("HTTPS URL"))
  }
}
