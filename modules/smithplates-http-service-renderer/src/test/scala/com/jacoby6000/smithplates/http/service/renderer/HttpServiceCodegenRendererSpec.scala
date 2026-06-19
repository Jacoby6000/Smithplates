package com.jacoby6000.smithplates.http.service.renderer

import cats.data.Validated
import com.jacoby6000.smithplates.http.HttpIrExtractor
import com.jacoby6000.smithplates.http.HttpTestModelLoader
import munit.FunSuite

class HttpServiceCodegenRendererSpec extends FunSuite {
  test("HttpServiceCodegenRenderer emits configured HTTP server artifacts") {
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
        templateDirectory = HttpServiceCodegenRendererSpec.internal.PythonServerTemplateDirectory,
        defaultFrameworkKey = "fastapi",
        enabledFrameworkKeys = List("fastapi"),
        sourceOutputDirectory = Some("src/generated"),
        testOutputDirectory = Some("tests"),
        artifacts =
          HttpServiceCodegenApiArtifacts.forEnabledFrameworks(List("fastapi"), List("v1_widgets"), emitModels = true),
        rootNamespace = HttpServiceCodegenRendererSpec.internal.RootNamespace,
        packageNameOverride = None,
        modelsPackageNameOverride = None,
        emitModels = true,
        modelTemplateDirectory = Some(HttpServiceCodegenRendererSpec.internal.PythonModelsTemplateDirectory)
      )

    HttpServiceCodegenRenderer.render(model, serviceIr, settings) match {
      case Validated.Valid(artifacts) =>
        val paths = artifacts.map(_.relativePath).toSet
        assert(paths.contains("src/generated/example/app_factory.py"))
        assert(paths.contains("src/generated/example/apis/v1_widgets_api.py"))
        assert(paths.contains("src/generated/example/apis/v1_widgets_api_base.py"))
        assert(paths.contains("src/generated/example/widget_output.py"))
      case Validated.Invalid(errors)  =>
        fail(errors.map(_.message).toList.mkString("; "))
    }
  }

  test("HttpServiceCodegenRenderer emits configured HTTP client artifacts") {
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
        templateDirectory = HttpServiceCodegenRendererSpec.internal.PythonClientTemplateDirectory,
        defaultFrameworkKey = "httpx",
        enabledFrameworkKeys = List("httpx"),
        sourceOutputDirectory = Some("src/generated"),
        testOutputDirectory = Some("tests"),
        artifacts = HttpClientCodegenApiArtifacts.forEnabledLibraries(List("httpx"), List("v1_widgets")) ++
          HttpServiceCodegenApiArtifacts.sharedModels,
        rootNamespace = HttpServiceCodegenRendererSpec.internal.RootNamespace,
        packageNameOverride = None,
        modelsPackageNameOverride = None,
        emitModels = true,
        modelTemplateDirectory = Some(HttpServiceCodegenRendererSpec.internal.PythonModelsTemplateDirectory)
      )

    HttpServiceCodegenRenderer.render(model, serviceIr, settings) match {
      case Validated.Valid(artifacts) =>
        val paths = artifacts.map(_.relativePath).toSet
        assert(paths.contains("src/generated/example/client/client_registry.py"))
        assert(paths.contains("src/generated/example/clients/v1_widgets_client.py"))
        assert(paths.contains("src/generated/example/widget_output.py"))
      case Validated.Invalid(errors)  =>
        fail(errors.map(_.message).toList.mkString("; "))
    }
  }

  test("HttpServiceCodegenRenderer includes None in protocol return type for Unit success responses") {
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
          |    operations: [DeleteWidget]
          |}
          |
          |@tags(["v1_widgets"])
          |@http(method: "DELETE", uri: "/v1/widgets/{id}", code: 204)
          |operation DeleteWidget {
          |    input: DeleteWidgetInput
          |    output: Unit
          |    errors: [DeleteWidget404]
          |}
          |
          |structure DeleteWidgetInput {
          |    @required
          |    @httpLabel
          |    id: String
          |}
          |
          |@error("client")
          |@httpError(404)
          |structure DeleteWidget404 {
          |    @required
          |    message: String
          |}
          |""".stripMargin
    )

    val protocolContent =
      HttpServiceCodegenRendererSpec.internal.renderFastApiProtocolBase(model, "v1_widgets")

    assert(clue(protocolContent).contains(") -> DeleteWidget404 | None:"))
  }

  test("HttpServiceCodegenRenderer deduplicates protocol return types for shared @httpProblem error shapes") {
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
          |service WidgetApi {
          |    version: "1"
          |    operations: [MutateWidget]
          |}
          |
          |@tags(["v1_widgets"])
          |@http(method: "PATCH", uri: "/v1/widgets/{id}", code: 200)
          |operation MutateWidget {
          |    input: MutateWidgetInput
          |    output: WidgetOutput
          |    errors: [MutateWidget404, MutateWidget409, MutateWidget422]
          |}
          |
          |structure MutateWidgetInput {
          |    @required
          |    @httpLabel
          |    id: String
          |
          |    @httpPayload
          |    @required
          |    body: WidgetPatch
          |}
          |
          |structure WidgetPatch {
          |    @required
          |    status: String
          |}
          |
          |structure WidgetOutput {
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
          |    type: "https://example.com/errors/widget-not-found"
          |    title: "Widget not found"
          |    code: 404
          |)
          |@error("client")
          |structure MutateWidget404 {
          |    @httpPayload
          |    @required
          |    body: Problem
          |}
          |
          |@httpProblem(
          |    type: "https://example.com/errors/widget-conflict"
          |    title: "Widget conflict"
          |    code: 409
          |)
          |@error("client")
          |structure MutateWidget409 {
          |    @httpPayload
          |    @required
          |    body: Problem
          |}
          |
          |@httpProblem(
          |    type: "https://example.com/errors/widget-invalid"
          |    title: "Widget invalid"
          |    code: 422
          |)
          |@error("client")
          |structure MutateWidget422 {
          |    @httpPayload
          |    @required
          |    body: Problem
          |}
          |""".stripMargin
    )

    val protocolContent =
      HttpServiceCodegenRendererSpec.internal.renderFastApiProtocolBase(model, "v1_widgets")

    assert(
      clue(protocolContent).contains(
        ") -> WidgetOutput | MutateWidget404 | MutateWidget409 | MutateWidget422:"
      )
    )
    assert(!protocolContent.contains("Problem | Problem"))

    val artifacts       = HttpServiceCodegenRendererSpec.internal.renderFastApiArtifacts(model)
    val mutateWidget404 =
      artifacts.find(_.relativePath.endsWith("mutate_widget404.py")).map(_.content).getOrElse("")
    assert(mutateWidget404.contains("class MutateWidget404(Problem)"))
    assert(mutateWidget404.contains("title: str = Field(...)"))
  }

  test("HttpServiceCodegenRenderer imports enum types used in route parameter signatures") {
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
          |    operations: [SearchWidgets]
          |}
          |
          |enum WidgetStatus {
          |    ACTIVE
          |    ARCHIVED
          |}
          |
          |@tags(["v1_widgets"])
          |@http(method: "GET", uri: "/v1/widgets", code: 200)
          |@readonly
          |operation SearchWidgets {
          |    input: SearchWidgetsInput
          |    output: WidgetListOutput
          |}
          |
          |structure SearchWidgetsInput {
          |    @httpQuery("status")
          |    status: WidgetStatus
          |}
          |
          |structure WidgetListOutput {
          |    @required
          |    items: String
          |}
          |""".stripMargin
    )

    val protocolContent =
      HttpServiceCodegenRendererSpec.internal.renderFastApiProtocolBase(model, "v1_widgets")

    assert(protocolContent.contains("from generated.example.widget_status import WidgetStatus"))
    assert(protocolContent.contains("status: WidgetStatus | None"))
  }
}
object HttpServiceCodegenRendererSpec {

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    val PythonServerTemplateDirectory = "classpath:python/src/http/server"
    val PythonClientTemplateDirectory = "classpath:python/src/http/client"
    val PythonModelsTemplateDirectory = "classpath:python/src/http/models"
    val RootNamespace                 = Some("generated")

    def renderFastApiProtocolBase(model: software.amazon.smithy.model.Model, routeGroupTag: String): String =
      renderFastApiArtifacts(model, routeGroupTag)
        .find(_.relativePath == s"src/generated/example/apis/${routeGroupTag}_api_base.py")
        .map(_.content)
        .getOrElse(
          throw new IllegalStateException(
            s"Missing generated protocol artifact at src/generated/example/apis/${routeGroupTag}_api_base.py"
          )
        )

    def renderFastApiArtifacts(
        model: software.amazon.smithy.model.Model,
        routeGroupTag: String = "v1_widgets"
    ): List[HttpCodegenArtifact] = {
      val serviceIr = HttpIrExtractor.extractOrThrow(model)
      val settings  =
        HttpServiceCodegenSettings(
          templateDirectory = PythonServerTemplateDirectory,
          defaultFrameworkKey = "fastapi",
          enabledFrameworkKeys = List("fastapi"),
          sourceOutputDirectory = Some("src/generated"),
          testOutputDirectory = Some("tests"),
          artifacts = HttpServiceCodegenApiArtifacts.forEnabledFrameworks(
            List("fastapi"),
            List(routeGroupTag),
            emitModels = true),
          rootNamespace = RootNamespace,
          packageNameOverride = None,
          modelsPackageNameOverride = None,
          emitModels = true,
          modelTemplateDirectory = Some(PythonModelsTemplateDirectory)
        )

      HttpServiceCodegenRenderer.render(model, serviceIr, settings) match {
        case Validated.Valid(artifacts) => artifacts
        case Validated.Invalid(errors)  =>
          throw new IllegalStateException(errors.map(_.message).toList.mkString("; "))
      }
    }
  }
}
