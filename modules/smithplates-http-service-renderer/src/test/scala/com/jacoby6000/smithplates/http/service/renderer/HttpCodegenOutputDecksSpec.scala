package com.jacoby6000.smithplates.http.service.renderer

import cats.data.Validated
import com.jacoby6000.smithplates.codegen.core.ModelKind
import com.jacoby6000.smithplates.codegen.core.planning.ArtifactKind
import com.jacoby6000.smithplates.codegen.core.planning.BindingFilterAtom
import com.jacoby6000.smithplates.codegen.core.planning.BindingGroup
import com.jacoby6000.smithplates.codegen.core.planning.CodegenOutput
import com.jacoby6000.smithplates.codegen.core.planning.SmithyBinding
import munit.FunSuite

class HttpCodegenOutputDecksSpec extends FunSuite {
  private val ServerDir = "classpath:python/src/http/server"
  private val ClientDir = "classpath:python/src/http/client"
  private val ModelsDir = "classpath:python/src/http/models"

  private def outputPaths(outputs: List[CodegenOutput]): Map[String, String] =
    outputs.collect { case template: CodegenOutput.CodegenTemplateBindingOutput =>
      template.id.value -> template.outputPath
    }.toMap

  test("bundled server deck composes the shared and fastapi outputs") {
    val outputs =
      HttpServiceCodegenApiArtifacts.forEnabledFrameworks(ServerDir, ModelsDir, List("fastapi"), emitModels = false)
    assertEquals(
      outputs.map(_.id.value),
      List(
        "python.http.server.app_factory",
        "python.http.server.app_services",
        "python.http.server.model_validation",
        "python.http.server.api_response",
        "python.http.server.operation_bindings",
        "python.http.server.api_exceptions",
        "python.http.server.api_exception_handler",
        "python.http.server.apis_init",
        "python.http.server.fastapi.route_group_protocol",
        "python.http.server.fastapi.route_group_routes"
      )
    )
    assertEquals(
      outputPaths(outputs).get("python.http.server.fastapi.route_group_routes"),
      Some("{{smithyNamespaceDir}}/apis/{{tagName}}_api.py")
    )
  }

  test("bundled server deck appends the model outputs when emitModels is set") {
    val withModels =
      HttpServiceCodegenApiArtifacts.forEnabledFrameworks(ServerDir, ModelsDir, List("fastapi"), emitModels = true)
    val modelIds   = HttpServiceCodegenApiArtifacts.sharedModels(ModelsDir).map(_.id.value)
    assertEquals(modelIds.toSet.subsetOf(withModels.map(_.id.value).toSet), true)
    assertEquals(
      modelIds,
      List(
        "python.http.models.init",
        "python.http.models.problem",
        "python.http.models.structure",
        "python.http.models.union",
        "python.http.models.enum"
      )
    )
  }

  test("bundled client deck composes the shared and httpx outputs") {
    val outputs = HttpClientCodegenApiArtifacts.forEnabledLibraries(ClientDir, List("httpx"))
    assertEquals(
      outputs.map(_.id.value),
      List(
        "python.http.client.model_validation",
        "python.http.client.operation_bindings",
        "python.http.client.client_response",
        "python.http.client.client_registry",
        "python.http.client.clients_init",
        "python.http.client.httpx.route_group_client"
      )
    )
  }

  test("model outputs carry kind-filtered model bindings") {
    val bindings =
      HttpServiceCodegenApiArtifacts
        .sharedModels(ModelsDir)
        .collect { case template: CodegenOutput.CodegenTemplateBindingOutput =>
          template.id.value -> template.binding
        }
        .toMap
    assertEquals(bindings.get("python.http.models.init"), Some(SmithyBinding.Service))
    assertEquals(
      bindings.get("python.http.models.enum"),
      Some(SmithyBinding.Model(List(BindingFilterAtom.Kind(ModelKind.Enum)), BindingGroup.None))
    )
  }

  test("model outputs are all emitted as source artifacts") {
    assert(HttpServiceCodegenApiArtifacts.sharedModels(ModelsDir).forall(_.kind == ArtifactKind.Src))
  }

  test("modelArtifacts reports a missing deck resource") {
    HttpServiceCodegenApiArtifacts.modelArtifacts("classpath:python/src/http/does-not-exist") match {
      case Validated.Invalid(errors) => assert(errors.exists(_.message.contains("missing codegen output deck")))
      case Validated.Valid(_)        => fail("expected missing resource to fail")
    }
  }
}
