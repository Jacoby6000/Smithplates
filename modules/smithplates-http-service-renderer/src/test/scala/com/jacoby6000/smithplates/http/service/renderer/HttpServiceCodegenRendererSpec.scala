package com.jacoby6000.smithplates.http.service.renderer

import cats.data.Validated
import com.jacoby6000.smithplates.codegen.TemplateOutputPrefix
import com.jacoby6000.smithplates.http.HttpIrExtractor
import com.jacoby6000.smithplates.http.HttpTestModelLoader
import munit.FunSuite

class HttpServiceCodegenRendererSpec extends FunSuite {
  private val PythonServerTemplateDirectory = "classpath:python/src/http/server"
  private val PythonClientTemplateDirectory = "classpath:python/src/http/client"
  private val PythonModelsTemplateDirectory = "classpath:python/src/http/models"
  private val RootNamespace                 = Some("generated")

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
        templateDirectory = PythonServerTemplateDirectory,
        defaultFrameworkKey = "fastapi",
        enabledFrameworkKeys = List("fastapi"),
        packageName = TemplateOutputPrefix.toPackageName(
          TemplateOutputPrefix.fromTemplateDirectory(PythonServerTemplateDirectory),
          RootNamespace
        ),
        sourceOutputDirectory = Some("src/generated"),
        testOutputDirectory = Some("tests"),
        artifacts =
          HttpServiceCodegenApiArtifacts.forEnabledFrameworks(List("fastapi"), List("v1_widgets"), emitModels = true),
        outputPrefix = TemplateOutputPrefix.fromTemplateDirectory(PythonServerTemplateDirectory),
        modelsPackageName = TemplateOutputPrefix.toPackageName(
          TemplateOutputPrefix.fromTemplateDirectory(PythonModelsTemplateDirectory),
          RootNamespace
        ),
        modelsOutputPrefix = TemplateOutputPrefix.fromTemplateDirectory(PythonModelsTemplateDirectory),
        modelTemplateDirectory = Some(PythonModelsTemplateDirectory)
      )

    HttpServiceCodegenRenderer.render(model, serviceIr, settings) match {
      case Validated.Valid(artifacts) =>
        val paths = artifacts.map(_.relativePath).toSet
        assert(paths.contains("src/generated/http/server/app_factory.py"))
        assert(paths.contains("src/generated/http/server/apis/v1_widgets_api.py"))
        assert(paths.contains("src/generated/http/server/apis/v1_widgets_api_base.py"))
        assert(paths.contains("src/generated/http/models/widget_output.py"))
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
        templateDirectory = PythonClientTemplateDirectory,
        defaultFrameworkKey = "httpx",
        enabledFrameworkKeys = List("httpx"),
        packageName = TemplateOutputPrefix.toPackageName(
          TemplateOutputPrefix.fromTemplateDirectory(PythonClientTemplateDirectory),
          RootNamespace
        ),
        sourceOutputDirectory = Some("src/generated"),
        testOutputDirectory = Some("tests"),
        artifacts = HttpClientCodegenApiArtifacts.forEnabledLibraries(List("httpx"), List("v1_widgets")) ++
          HttpServiceCodegenApiArtifacts.sharedModels,
        outputPrefix = TemplateOutputPrefix.fromTemplateDirectory(PythonClientTemplateDirectory),
        modelsPackageName = TemplateOutputPrefix.toPackageName(
          TemplateOutputPrefix.fromTemplateDirectory(PythonModelsTemplateDirectory),
          RootNamespace
        ),
        modelsOutputPrefix = TemplateOutputPrefix.fromTemplateDirectory(PythonModelsTemplateDirectory),
        emitModels = true,
        modelTemplateDirectory = Some(PythonModelsTemplateDirectory)
      )

    HttpServiceCodegenRenderer.render(model, serviceIr, settings) match {
      case Validated.Valid(artifacts) =>
        val paths = artifacts.map(_.relativePath).toSet
        assert(paths.contains("src/generated/http/client/client_registry.py"))
        assert(paths.contains("src/generated/http/client/clients/v1_widgets_client.py"))
        assert(paths.contains("src/generated/http/models/widget_output.py"))
      case Validated.Invalid(errors)  =>
        fail(errors.map(_.message).toList.mkString("; "))
    }
  }
}
