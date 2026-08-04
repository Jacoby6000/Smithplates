package com.jacoby6000.smithplates.http.service.renderer

import cats.data.Validated
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

    val settings =
      HttpServiceCodegenSettings(
        templateDirectory = HttpServiceCodegenRendererSpec.internal.PythonServerTemplateDirectory,
        defaultFrameworkKey = "fastapi",
        enabledFrameworkKeys = List("fastapi"),
        sourceOutputDirectory = Some("src/generated"),
        testOutputDirectory = Some("tests"),
        artifacts = HttpServiceCodegenApiArtifacts.forEnabledFrameworks(
          serverTemplateDirectory = HttpServiceCodegenRendererSpec.internal.PythonServerTemplateDirectory,
          modelsTemplateDirectory = HttpServiceCodegenRendererSpec.internal.PythonModelsTemplateDirectory,
          frameworkKeys = List("fastapi"),
          emitModels = true
        ),
        rootNamespace = HttpServiceCodegenRendererSpec.internal.RootNamespace,
        packageNameOverride = None,
        modelsPackageNameOverride = None,
        emitModels = true,
        modelTemplateDirectory = Some(HttpServiceCodegenRendererSpec.internal.PythonModelsTemplateDirectory)
      )

    HttpServiceCodegenRenderer.render(model, settings) match {
      case Validated.Valid(artifacts) =>
        val paths = artifacts.map(_.relativePath).toSet
        assert(paths.contains("src/generated/example/widget_api/app_factory.py"))
        assert(paths.contains("src/generated/example/widget_api/apis/v1_widgets_api.py"))
        assert(paths.contains("src/generated/example/widget_api/apis/v1_widgets_api_base.py"))
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

    val settings =
      HttpServiceCodegenSettings(
        templateDirectory = HttpServiceCodegenRendererSpec.internal.PythonClientTemplateDirectory,
        defaultFrameworkKey = "httpx",
        enabledFrameworkKeys = List("httpx"),
        sourceOutputDirectory = Some("src/generated"),
        testOutputDirectory = Some("tests"),
        artifacts = HttpClientCodegenApiArtifacts.forEnabledLibraries(
          HttpServiceCodegenRendererSpec.internal.PythonClientTemplateDirectory,
          List("httpx")
        ) ++
          HttpServiceCodegenApiArtifacts.sharedModels(
            HttpServiceCodegenRendererSpec.internal.PythonModelsTemplateDirectory
          ),
        rootNamespace = HttpServiceCodegenRendererSpec.internal.RootNamespace,
        packageNameOverride = None,
        modelsPackageNameOverride = None,
        emitModels = true,
        modelTemplateDirectory = Some(HttpServiceCodegenRendererSpec.internal.PythonModelsTemplateDirectory)
      )

    HttpServiceCodegenRenderer.render(model, settings) match {
      case Validated.Valid(artifacts) =>
        val paths = artifacts.map(_.relativePath).toSet
        assert(paths.contains("src/generated/example/widget_api/client/client_registry.py"))
        assert(paths.contains("src/generated/example/widget_api/clients/v1_widgets_client.py"))
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
          |@httpProblem(
          |    type: "https://example.com/errors/widget-not-found"
          |    title: "Widget not found"
          |    code: 404
          |)
          |@error("client")
          |structure MutateWidget404 {
          |}
          |
          |@httpProblem(
          |    type: "https://example.com/errors/widget-conflict"
          |    title: "Widget conflict"
          |    code: 409
          |)
          |@error("client")
          |structure MutateWidget409 {
          |}
          |
          |@httpProblem(
          |    type: "https://example.com/errors/widget-invalid"
          |    title: "Widget invalid"
          |    code: 422
          |)
          |@error("client")
          |structure MutateWidget422 {
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
    assert(mutateWidget404.contains("class MutateWidget404(HttpProblem)"))
    assert(
      mutateWidget404.contains(
        "from generated.smithplates.codegen.http.http_problem import HttpProblem"
      )
    )
    assert(mutateWidget404.contains("title: str | None = Field(default=\"Widget not found\")"))
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

    val artifacts    = HttpServiceCodegenRendererSpec.internal.renderFastApiArtifacts(model)
    val enumArtifact =
      artifacts.find(_.relativePath.endsWith("widget_status.py")).map(_.content).getOrElse("")
    assert(enumArtifact.contains("class WidgetStatus(StrEnum)"))
    assert(enumArtifact.contains("ACTIVE = \"ACTIVE\""))
  }

  test("HttpServiceCodegenRenderer filters services by serviceFilter") {
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
          |service AlphaApi {
          |    version: "1"
          |    operations: [GetAlpha]
          |}
          |
          |@httpService
          |service BetaApi {
          |    version: "1"
          |    operations: [GetBeta]
          |}
          |
          |@tags(["alpha"])
          |@http(method: "GET", uri: "/alpha/{id}", code: 200)
          |operation GetAlpha {
          |    input: GetAlphaInput
          |    output: AlphaOutput
          |}
          |
          |@tags(["beta"])
          |@http(method: "GET", uri: "/beta/{id}", code: 200)
          |operation GetBeta {
          |    input: GetBetaInput
          |    output: BetaOutput
          |}
          |
          |structure GetAlphaInput {
          |    @required
          |    @httpLabel
          |    id: String
          |}
          |
          |structure AlphaOutput {
          |    @required
          |    id: String
          |}
          |
          |structure GetBetaInput {
          |    @required
          |    @httpLabel
          |    id: String
          |}
          |
          |structure BetaOutput {
          |    @required
          |    id: String
          |}
          |""".stripMargin
    )

    val allSettings =
      HttpServiceCodegenRendererSpec.internal.defaultFastApiSettings(model)
    HttpServiceCodegenRenderer.render(model, allSettings) match {
      case Validated.Valid(allArtifacts) =>
        val allPaths = allArtifacts.map(_.relativePath).toSet
        assert(allPaths.exists(_.contains("alpha")), s"expected alpha artifacts without filter")
        assert(allPaths.exists(_.contains("beta")), s"expected beta artifacts without filter")
      case Validated.Invalid(errors)     =>
        fail(errors.map(_.message).toList.mkString("; "))
    }

    val filteredSettings =
      HttpServiceCodegenRendererSpec.internal.defaultFastApiSettings(
        model,
        serviceFilter = Some(Set("AlphaApi"))
      )
    HttpServiceCodegenRenderer.render(model, filteredSettings) match {
      case Validated.Valid(filteredArtifacts) =>
        val filteredPaths = filteredArtifacts.map(_.relativePath).toSet
        assert(filteredPaths.exists(_.contains("alpha")), s"expected alpha artifacts with filter")
        assert(!filteredPaths.exists(_.contains("beta")), s"expected no beta artifacts with filter")
      case Validated.Invalid(errors)          =>
        fail(errors.map(_.message).toList.mkString("; "))
    }
  }

  test("HttpServiceCodegenRenderer matches services by full shape ID") {
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
          |service AlphaApi {
          |    version: "1"
          |    operations: [GetAlpha]
          |}
          |
          |@httpService
          |service BetaApi {
          |    version: "1"
          |    operations: [GetBeta]
          |}
          |
          |@tags(["alpha"])
          |@http(method: "GET", uri: "/alpha/{id}", code: 200)
          |operation GetAlpha {
          |    input: GetAlphaInput
          |    output: AlphaOutput
          |}
          |
          |@tags(["beta"])
          |@http(method: "GET", uri: "/beta/{id}", code: 200)
          |operation GetBeta {
          |    input: GetBetaInput
          |    output: BetaOutput
          |}
          |
          |structure GetAlphaInput {
          |    @required
          |    @httpLabel
          |    id: String
          |}
          |
          |structure AlphaOutput {
          |    @required
          |    id: String
          |}
          |
          |structure GetBetaInput {
          |    @required
          |    @httpLabel
          |    id: String
          |}
          |
          |structure BetaOutput {
          |    @required
          |    id: String
          |}
          |""".stripMargin
    )

    val fullShapeIdSettings =
      HttpServiceCodegenRendererSpec.internal.defaultFastApiSettings(
        model,
        serviceFilter = Some(Set("example#AlphaApi"))
      )
    HttpServiceCodegenRenderer.render(model, fullShapeIdSettings) match {
      case Validated.Valid(filteredArtifacts) =>
        val filteredPaths = filteredArtifacts.map(_.relativePath).toSet
        assert(filteredPaths.exists(_.contains("alpha")), s"expected alpha artifacts with full shape ID filter")
        assert(!filteredPaths.exists(_.contains("beta")), s"expected no beta artifacts with full shape ID filter")
      case Validated.Invalid(errors)          =>
        fail(errors.map(_.message).toList.mkString("; "))
    }
  }

  test("HttpServiceCodegenRenderer renders FastAPI REST authentication") {
    val model                         = HttpTestModelLoader.assemble(
      "auth.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithplates.codegen.http#httpCookieAuth
          |use smithplates.codegen.http#httpService
          |use smithy.api#auth
          |use smithy.api#http
          |use smithy.api#httpApiKeyAuth
          |use smithy.api#httpBearerAuth
          |use smithy.api#optionalAuth
          |use smithy.api#tags
          |
          |@httpService
          |@httpBearerAuth
          |@httpApiKeyAuth(name: "X-API-Key", in: "header", scheme: "ApiKey")
          |@httpCookieAuth(name: "session")
          |@auth([httpBearerAuth, httpApiKeyAuth, httpCookieAuth])
          |service WidgetApi {
          |    version: "1"
          |    operations: [Required, Optional, Public]
          |}
          |
          |@tags(["v1_widgets"])
          |@http(method: "GET", uri: "/required", code: 204)
          |operation Required {}
          |
          |@optionalAuth
          |@tags(["v1_widgets"])
          |@http(method: "GET", uri: "/optional", code: 204)
          |operation Optional {}
          |
          |@auth([])
          |@tags(["v1_widgets"])
          |@http(method: "GET", uri: "/public", code: 204)
          |operation Public {}
          |""".stripMargin
    )
    val artifacts                     = HttpServiceCodegenRendererSpec.internal.renderFastApiArtifacts(model)
    def content(path: String): String =
      artifacts.find(_.relativePath.endsWith(path)).map(_.content).getOrElse(fail(s"missing generated $path"))

    val services = content("/app_services.py")
    val factory  = content("/app_factory.py")
    val bindings = content("/operation_bindings.py")
    val protocol = content("/apis/v1_widgets_api_base.py")
    val routes   = content("/apis/v1_widgets_api.py")

    assert(clue(services).contains("class AuthCredential:"))
    assert(clue(services).contains("class AuthContext:"))
    assert(clue(services).contains("class AuthVerifier(Protocol):"))
    assert(clue(factory).contains("auth_verifier: AuthVerifier"))
    assert(clue(factory).contains("app.state.auth_verifier = auth_verifier"))
    assert(clue(bindings).contains("location=\"header\""))
    assert(clue(bindings).contains("prefix=\"ApiKey\""))
    assert(clue(bindings).contains("\"smithy.api#httpBearerAuth\","))
    assert(clue(bindings).contains("allows_anonymous=True"))
    assert(clue(protocol).contains("auth: AuthContext,"))
    assert(clue(protocol).contains("auth: AuthContext | None,"))
    assert(clue(routes).contains("authenticate_required_request("))
    assert(clue(routes).contains("authenticate_optional_request("))
    assert(!clue(routes).contains("async def public(\n    services:" + "\n    request: Request,"))
  }

  test("HttpServiceCodegenRenderer renders Python and TypeScript REST client authentication") {
    val model = HttpTestModelLoader.assemble("auth.smithy" -> HttpServiceCodegenRendererSpec.internal.authModel)

    val python                                                              = HttpServiceCodegenRendererSpec.internal.renderClientArtifacts(model, "python", "httpx2")
    val typescript                                                          = HttpServiceCodegenRendererSpec.internal.renderClientArtifacts(model, "typescript", "fetch")
    def content(artifacts: List[HttpCodegenArtifact], path: String): String =
      artifacts.find(_.relativePath.endsWith(path)).map(_.content).getOrElse(fail(s"missing generated $path"))

    val pythonBindings = content(python, "/client/operation_bindings.py")
    val pythonClient   = content(python, "/clients/auth_client.py")
    val pythonRegistry = content(python, "/client/client_registry.py")
    assert(clue(pythonBindings).contains("class AuthProvider(Protocol):"))
    assert(clue(pythonBindings).contains("def apply_operation_auth("))
    assert(clue(pythonBindings).contains("location=\"cookie\""))
    assert(clue(pythonClient).contains("auth_provider: AuthProvider | None = None"))
    assert(clue(pythonClient).contains("apply_operation_auth("))
    assert(clue(pythonRegistry).contains("auth_provider: AuthProvider | None = None"))

    val typescriptBindings = content(typescript, "/client/operationBindings.ts")
    val typescriptClient   = content(typescript, "/clients/authClient.ts")
    val typescriptRegistry = content(typescript, "/client/clientRegistry.ts")
    assert(clue(typescriptBindings).contains("export interface AuthProvider"))
    assert(clue(typescriptBindings).contains("export function applyOperationAuth("))
    assert(clue(typescriptClient).contains("authProvider?: AuthProvider"))
    assert(clue(typescriptClient).contains("credentials: \"include\""))
    assert(clue(typescriptRegistry).contains("authProvider?: AuthProvider"))
  }

  test("HttpServiceCodegenRenderer rejects authenticated axios and WebSocket operations") {
    val authModel     = HttpTestModelLoader.assemble("auth.smithy" -> HttpServiceCodegenRendererSpec.internal.authModel)
    val axiosSettings = HttpServiceCodegenRendererSpec.internal.clientSettings("typescript", "axios")
    HttpServiceCodegenRenderer.render(authModel, axiosSettings) match {
      case Validated.Valid(_)        => fail("expected authenticated axios generation to fail")
      case Validated.Invalid(errors) =>
        assert(errors.exists(_.message.contains("not supported by HTTP target 'axios'")))
    }

    val customSettings = HttpServiceCodegenRendererSpec.internal
      .clientSettings("typescript", "fetch")
      .copy(templateDirectory = "classpath:typescript/custom-http-templates")
    HttpServiceCodegenRenderer.render(authModel, customSettings) match {
      case Validated.Valid(_)        => fail("expected custom authenticated target generation to fail")
      case Validated.Invalid(errors) =>
        assert(errors.exists(_.message.contains("not supported by HTTP target 'fetch'")))
    }

    val websocketModel = HttpTestModelLoader.assemble(
      "websocket-auth.smithy" -> HttpServiceCodegenRendererSpec.internal.authenticatedWebsocketModel
    )
    val fetchSettings  = HttpServiceCodegenRendererSpec.internal.clientSettings("typescript", "fetch")
    HttpServiceCodegenRenderer.render(websocketModel, fetchSettings) match {
      case Validated.Valid(_)        => fail("expected authenticated WebSocket generation to fail")
      case Validated.Invalid(errors) =>
        assert(errors.exists(_.message.contains("authenticated WebSocket operation 'Stream' is not supported")))
    }

    val publicWebsocket = HttpTestModelLoader.assemble(
      "public-websocket.smithy" -> HttpServiceCodegenRendererSpec.internal.authenticatedWebsocketModel
        .replace("@websocket\n", "@auth([])\n@websocket\n")
    )
    assert(HttpServiceCodegenRenderer.render(publicWebsocket, fetchSettings).isValid)
  }
}
object HttpServiceCodegenRendererSpec {

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    val PythonServerTemplateDirectory     = "classpath:python/src/http/server"
    val PythonClientTemplateDirectory     = "classpath:python/src/http/client"
    val PythonModelsTemplateDirectory     = "classpath:python/src/http/models"
    val TypeScriptClientTemplateDirectory = "classpath:typescript/src/http/client"
    val TypeScriptModelsTemplateDirectory = "classpath:typescript/src/http/models"
    val RootNamespace                     = Some("generated")

    val authModel: String =
      """$version: "2.0"
        |namespace example
        |
        |use smithplates.codegen.http#httpCookieAuth
        |use smithplates.codegen.http#httpService
        |use smithy.api#auth
        |use smithy.api#http
        |use smithy.api#httpApiKeyAuth
        |use smithy.api#httpBearerAuth
        |use smithy.api#optionalAuth
        |use smithy.api#tags
        |
        |@httpService
        |@httpBearerAuth
        |@httpApiKeyAuth(name: "api_key", in: "query")
        |@httpCookieAuth(name: "session")
        |@auth([httpBearerAuth, httpApiKeyAuth, httpCookieAuth])
        |service AuthApi {
        |    version: "1"
        |    operations: [Required, Optional, Public]
        |}
        |
        |@tags(["auth"])
        |@http(method: "GET", uri: "/required", code: 204)
        |operation Required {}
        |
        |@optionalAuth
        |@tags(["auth"])
        |@http(method: "GET", uri: "/optional", code: 204)
        |operation Optional {}
        |
        |@auth([])
        |@tags(["auth"])
        |@http(method: "GET", uri: "/public", code: 204)
        |operation Public {}
        |""".stripMargin

    val authenticatedWebsocketModel: String =
      """$version: "2.0"
        |namespace example
        |
        |use smithplates.codegen.http#httpService
        |use smithplates.codegen.http#websocket
        |use smithy.api#auth
        |use smithy.api#http
        |use smithy.api#httpBearerAuth
        |use smithy.api#tags
        |
        |@httpService
        |@httpBearerAuth
        |@auth([httpBearerAuth])
        |service StreamApi {
        |    version: "1"
        |    operations: [Stream]
        |}
        |
        |@websocket
        |@tags(["stream"])
        |@http(method: "GET", uri: "/stream", code: 200)
        |operation Stream {
        |    input: StreamMessage
        |    output: StreamMessage
        |}
        |
        |structure StreamMessage {
        |    value: String
        |}
        |""".stripMargin

    def clientSettings(language: String, library: String): HttpServiceCodegenSettings = {
      val (clientDirectory, modelsDirectory) = language match {
        case "python"     => PythonClientTemplateDirectory     -> PythonModelsTemplateDirectory
        case "typescript" => TypeScriptClientTemplateDirectory -> TypeScriptModelsTemplateDirectory
        case other        => throw new IllegalArgumentException(s"unsupported test language $other")
      }
      HttpServiceCodegenSettings(
        templateDirectory = clientDirectory,
        defaultFrameworkKey = library,
        enabledFrameworkKeys = List(library),
        sourceOutputDirectory = Some("src/generated"),
        testOutputDirectory = Some("tests"),
        artifacts = HttpClientCodegenApiArtifacts.forEnabledLibraries(clientDirectory, List(library)) ++
          HttpServiceCodegenApiArtifacts.sharedModels(modelsDirectory),
        rootNamespace = RootNamespace,
        packageNameOverride = None,
        modelsPackageNameOverride = None,
        emitModels = true,
        modelTemplateDirectory = Some(modelsDirectory)
      )
    }

    def renderClientArtifacts(
        model: software.amazon.smithy.model.Model,
        language: String,
        library: String
    ): List[HttpCodegenArtifact] =
      HttpServiceCodegenRenderer.render(model, clientSettings(language, library)) match {
        case Validated.Valid(artifacts) => artifacts
        case Validated.Invalid(errors)  =>
          throw new IllegalStateException(errors.map(_.message).toList.mkString("; "))
      }

    def renderFastApiProtocolBase(model: software.amazon.smithy.model.Model, routeGroupTag: String): String =
      renderFastApiArtifacts(model, routeGroupTag)
        .find(_.relativePath == s"src/generated/example/widget_api/apis/${routeGroupTag}_api_base.py")
        .map(_.content)
        .getOrElse(
          throw new IllegalStateException(
            s"Missing generated protocol artifact at src/generated/example/widget_api/apis/${routeGroupTag}_api_base.py"
          )
        )

    def renderFastApiArtifacts(
        model: software.amazon.smithy.model.Model,
        routeGroupTag: String = "v1_widgets"
    ): List[HttpCodegenArtifact] = {
      val settings = defaultFastApiSettings(model)

      HttpServiceCodegenRenderer.render(model, settings) match {
        case Validated.Valid(artifacts) => artifacts
        case Validated.Invalid(errors)  =>
          throw new IllegalStateException(errors.map(_.message).toList.mkString("; "))
      }
    }

    def defaultFastApiSettings(
        model: software.amazon.smithy.model.Model,
        serviceFilter: Option[Set[String]] = None
    ): HttpServiceCodegenSettings =
      HttpServiceCodegenSettings(
        templateDirectory = PythonServerTemplateDirectory,
        defaultFrameworkKey = "fastapi",
        enabledFrameworkKeys = List("fastapi"),
        sourceOutputDirectory = Some("src/generated"),
        testOutputDirectory = Some("tests"),
        artifacts = HttpServiceCodegenApiArtifacts.forEnabledFrameworks(
          serverTemplateDirectory = PythonServerTemplateDirectory,
          modelsTemplateDirectory = PythonModelsTemplateDirectory,
          frameworkKeys = List("fastapi"),
          emitModels = true
        ),
        rootNamespace = RootNamespace,
        packageNameOverride = None,
        modelsPackageNameOverride = None,
        emitModels = true,
        modelTemplateDirectory = Some(PythonModelsTemplateDirectory),
        serviceFilter = serviceFilter
      )
  }
}
