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
}
