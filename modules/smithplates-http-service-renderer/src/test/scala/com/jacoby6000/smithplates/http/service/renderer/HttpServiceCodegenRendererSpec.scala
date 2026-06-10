package com.jacoby6000.smithplates.http.service.renderer

import cats.data.Validated
import com.jacoby6000.smithplates.http.HttpIrExtractor
import com.jacoby6000.smithplates.http.HttpTestModelLoader
import munit.FunSuite

class HttpServiceCodegenRendererSpec extends FunSuite {
  test("HttpServiceCodegenRenderer emits FastAPI route and protocol modules") {
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

    val serviceIr = HttpIrExtractor.extractOrThrow(model)
    val settings  =
      HttpServiceCodegenSettings(
        templateDirectory = PythonTemplateNamespaces.bundledHttpTemplateDirectory,
        defaultFrameworkKey = "fastapi",
        enabledFrameworkKeys = List("fastapi"),
        packageName = "generated.widget_api",
        sourceOutputDirectory = Some("src/generated"),
        testOutputDirectory = Some("tests"),
        artifacts = HttpServiceCodegenApiArtifacts.forEnabledFrameworks(List("fastapi"), List("v1_widgets"))
      )

    HttpServiceCodegenRenderer.render(model, serviceIr, settings) match {
      case Validated.Valid(artifacts) =>
        val paths  = artifacts.map(_.relativePath).toSet
        assert(paths.contains("src/generated/api/app_factory.py"))
        assert(paths.contains("src/generated/api/apis/v1_widgets_api.py"))
        assert(paths.contains("src/generated/api/apis/v1_widgets_api_base.py"))
        val routes = artifacts.find(_.relativePath.endsWith("v1_widgets_api.py")).getOrElse(fail("missing routes"))
        assert(routes.content.contains("router = APIRouter()"))
        assert(routes.content.contains("get_widget"))
      case Validated.Invalid(errors)  =>
        fail(errors.map(_.message).toList.mkString("; "))
    }
  }

  test("HttpServiceCodegenRenderer maps @timestampFormat members to FastAPI parameter types") {
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
          |}
          |
          |structure AssetListOutput {
          |    @required
          |    items: String
          |}
          |""".stripMargin
    )

    val serviceIr = HttpIrExtractor.extractOrThrow(model)
    val settings  =
      HttpServiceCodegenSettings(
        templateDirectory = PythonTemplateNamespaces.bundledHttpTemplateDirectory,
        defaultFrameworkKey = "fastapi",
        enabledFrameworkKeys = List("fastapi"),
        packageName = "generated.asset_api",
        sourceOutputDirectory = Some("src/generated"),
        testOutputDirectory = Some("tests"),
        artifacts = HttpServiceCodegenApiArtifacts.forEnabledFrameworks(List("fastapi"), List("assets"))
      )

    HttpServiceCodegenRenderer.render(model, serviceIr, settings) match {
      case Validated.Valid(artifacts) =>
        val routes   = artifacts.find(_.relativePath.endsWith("assets_api.py")).getOrElse(fail("missing routes"))
        assert(routes.content.contains("since: float | None = Query(None, alias=\"since\")"))
        val protocol =
          artifacts.find(_.relativePath.endsWith("assets_api_base.py")).getOrElse(fail("missing protocol"))
        assert(protocol.content.contains("since: float | None,"))
      case Validated.Invalid(errors)  =>
        fail(errors.map(_.message).toList.mkString("; "))
    }
  }

  test("HttpServiceCodegenRenderer emits fallback handlers for service-level errors") {
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

    val serviceIr = HttpIrExtractor.extractOrThrow(model)
    val settings  =
      HttpServiceCodegenSettings(
        templateDirectory = PythonTemplateNamespaces.bundledHttpTemplateDirectory,
        defaultFrameworkKey = "fastapi",
        enabledFrameworkKeys = List("fastapi"),
        packageName = "generated.widget_api",
        sourceOutputDirectory = Some("src/generated"),
        testOutputDirectory = Some("tests"),
        artifacts = HttpServiceCodegenApiArtifacts.forEnabledFrameworks(List("fastapi"), List("v1_widgets"))
      )

    HttpServiceCodegenRenderer.render(model, serviceIr, settings) match {
      case Validated.Valid(artifacts) =>
        val handler    =
          artifacts.find(_.relativePath.endsWith("api_exception_handler.py")).getOrElse(fail("missing handler"))
        val appFactory =
          artifacts.find(_.relativePath.endsWith("app_factory.py")).getOrElse(fail("missing app factory"))
        assert(handler.content.contains("handle_widget_not_found_api_error"))
        assert(handler.content.contains("service_error_json_response(404, exc.payload)"))
        assert(appFactory.content.contains("@app.exception_handler(WidgetNotFoundApiError)"))
        assert(artifacts.exists(_.relativePath.endsWith("api/models/widget_not_found.py")))
      case Validated.Invalid(errors)  =>
        fail(errors.map(_.message).toList.mkString("; "))
    }
  }

  test("HttpServiceCodegenRenderer preserves Smithy input member order across route, protocol, and service call") {
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
          |    @httpHeader("X-Region")
          |    region: String
          |
          |    @required
          |    @httpLabel
          |    id: String
          |
          |    @httpQuery("category")
          |    category: String
          |
          |    @httpQuery("since")
          |    @timestampFormat("epoch-seconds")
          |    since: Timestamp
          |}
          |
          |structure WidgetOutput {
          |    @required
          |    id: String
          |}
          |""".stripMargin
    )

    val serviceIr = HttpIrExtractor.extractOrThrow(model)
    val settings  =
      HttpServiceCodegenSettings(
        templateDirectory = PythonTemplateNamespaces.bundledHttpTemplateDirectory,
        defaultFrameworkKey = "fastapi",
        enabledFrameworkKeys = List("fastapi"),
        packageName = "generated.widget_api",
        sourceOutputDirectory = Some("src/generated"),
        testOutputDirectory = Some("tests"),
        artifacts = HttpServiceCodegenApiArtifacts.forEnabledFrameworks(List("fastapi"), List("widgets"))
      )

    HttpServiceCodegenRenderer.render(model, serviceIr, settings) match {
      case Validated.Valid(artifacts) =>
        val routes                 = artifacts.find(_.relativePath.endsWith("widgets_api.py")).getOrElse(fail("missing routes"))
        val protocol               =
          artifacts.find(_.relativePath.endsWith("widgets_api_base.py")).getOrElse(fail("missing protocol"))
        val expectedParameterOrder = List("region", "id", "category", "since")
        val routeSection           =
          routes.content.split("async def inspect_widget").last.split("\\) -> WidgetOutput").head
        val protocolSection        =
          protocol.content.split("async def inspect_widget").last.split("\\) -> WidgetOutput").head
        assertParameterOrder(routeSection, expectedParameterOrder, "route handler")
        assertParameterOrder(protocolSection, expectedParameterOrder, "protocol")
        assertEquals(
          routes.content.contains(
            "inspect_widget(region=region, id=id, category=category, since=since)"
          ),
          true
        )
      case Validated.Invalid(errors)  =>
        fail(errors.map(_.message).toList.mkString("; "))
    }
  }

  test("HttpServiceCodegenRenderer emits Smithy tagged unions and nested body models") {
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
          |    archivedAt: Timestamp
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

    val serviceIr = HttpIrExtractor.extractOrThrow(model)
    val settings  =
      HttpServiceCodegenSettings(
        templateDirectory = PythonTemplateNamespaces.bundledHttpTemplateDirectory,
        defaultFrameworkKey = "fastapi",
        enabledFrameworkKeys = List("fastapi"),
        packageName = "generated.content_api",
        sourceOutputDirectory = Some("src/generated"),
        testOutputDirectory = Some("tests"),
        artifacts = HttpServiceCodegenApiArtifacts.forEnabledFrameworks(List("fastapi"), List("content"))
      )

    HttpServiceCodegenRenderer.render(model, serviceIr, settings) match {
      case Validated.Valid(artifacts) =>
        val unionModel  =
          artifacts
            .find(_.relativePath.endsWith("api/models/media_attachment.py"))
            .getOrElse(fail("missing union model"))
        val inputModel  =
          artifacts
            .find(_.relativePath.endsWith("api/models/create_content_input.py"))
            .getOrElse(fail("missing input model"))
        val outputModel =
          artifacts
            .find(_.relativePath.endsWith("api/models/content_output.py"))
            .getOrElse(fail("missing output model"))
        val routes      =
          artifacts.find(_.relativePath.endsWith("content_api.py")).getOrElse(fail("missing routes"))
        assert(unionModel.content.contains("class MediaAttachmentCaption(TypedDict):"))
        assert(unionModel.content.contains("class MediaAttachmentImage(TypedDict):"))
        assert(unionModel.content.contains("image: ImageAsset"))
        assert(
          unionModel.content.contains(
            "MediaAttachment = MediaAttachmentCaption | MediaAttachmentImage | MediaAttachmentArchivedAt"))
        assert(inputModel.content.contains("authorAddress: PostalAddress"))
        assert(inputModel.content.contains("attachment: MediaAttachment"))
        assert(outputModel.content.contains("attachment: MediaAttachment"))
        assert(routes.content.contains("create_content_input: CreateContentInput = Body(...)"))
      case Validated.Invalid(errors)  =>
        fail(errors.map(_.message).toList.mkString("; "))
    }
  }

  test("HttpServiceCodegenRenderer emits document and member HTTP request bodies") {
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

    val serviceIr = HttpIrExtractor.extractOrThrow(model)
    val settings  =
      HttpServiceCodegenSettings(
        templateDirectory = PythonTemplateNamespaces.bundledHttpTemplateDirectory,
        defaultFrameworkKey = "fastapi",
        enabledFrameworkKeys = List("fastapi"),
        packageName = "generated.project_api",
        sourceOutputDirectory = Some("src/generated"),
        testOutputDirectory = Some("tests"),
        artifacts =
          HttpServiceCodegenApiArtifacts.forEnabledFrameworks(List("fastapi"), List("projects", "project_tasks"))
      )

    HttpServiceCodegenRenderer.render(model, serviceIr, settings) match {
      case Validated.Valid(artifacts) =>
        val projectsRoutes   =
          artifacts.find(_.relativePath.endsWith("projects_api.py")).getOrElse(fail("missing projects routes"))
        val tasksRoutes      =
          artifacts.find(_.relativePath.endsWith("project_tasks_api.py")).getOrElse(fail("missing task routes"))
        val projectsProtocol =
          artifacts.find(_.relativePath.endsWith("projects_api_base.py")).getOrElse(fail("missing projects protocol"))
        val tasksProtocol    =
          artifacts.find(_.relativePath.endsWith("project_tasks_api_base.py")).getOrElse(fail("missing task protocol"))
        assert(projectsRoutes.content.contains("create_project_input: CreateProjectInput = Body(...)"))
        assert(projectsRoutes.content.contains("create_project(create_project_input=create_project_input)"))
        assert(tasksRoutes.content.contains("title: str = Body(...)"))
        assert(tasksRoutes.content.contains("create_project_task(project_id=project_id, title=title)"))
        assert(projectsProtocol.content.contains("create_project_input: CreateProjectInput,"))
        assert(tasksProtocol.content.contains("title: str,"))
        assert(artifacts.exists(_.relativePath.endsWith("api/models/create_project_input.py")))
      case Validated.Invalid(errors)  =>
        fail(errors.map(_.message).toList.mkString("; "))
    }
  }

  private def assertParameterOrder(section: String, expectedOrder: List[String], label: String): Unit = {
    val indices =
      expectedOrder.map(name => name -> section.indexOf(s"$name:"))
    indices.sliding(2).foreach {
      case List((leftName, leftIndex), (rightName, rightIndex)) =>
        assert(
          leftIndex >= 0 && rightIndex >= 0 && leftIndex < rightIndex,
          s"$label parameter '$rightName' must follow '$leftName' in generated signature; indices=$indices"
        )
      case _                                                    => ()
    }
  }
}
