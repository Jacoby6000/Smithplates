package com.jacoby6000.smithplates.http.service.renderer

import com.jacoby6000.smithplates.codegen.core.planning.CodegenOutput

/** Bundled `@httpService` client artifact paths relative to the HTTP client template root. */
object HttpClientCodegenApiArtifacts {
  val sharedPerService: List[CodegenOutput] =
    List(
      HttpServiceCodegenApiArtifacts.serviceTemplate(
        id = "python.http.client.model_validation",
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "model_validation.ssp",
        outputFile = "model_validation.py"
      ),
      HttpServiceCodegenApiArtifacts.serviceTemplate(
        id = "python.http.client.operation_bindings",
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "operation_bindings.ssp",
        outputFile = "client/operation_bindings.py"
      ),
      HttpServiceCodegenApiArtifacts.serviceTemplate(
        id = "python.http.client.client_response",
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "client_response.ssp",
        outputFile = "client/client_response.py"
      ),
      HttpServiceCodegenApiArtifacts.serviceTemplate(
        id = "python.http.client.client_registry",
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "client_registry.ssp",
        outputFile = "client/client_registry.py"
      ),
      HttpServiceCodegenApiArtifacts.serviceTemplate(
        id = "python.http.client.clients_init",
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "clients/__init__.ssp",
        outputFile = "clients/__init__.py"
      )
    )

  def httpx: List[CodegenOutput] =
    List(
      HttpServiceCodegenApiArtifacts.operationTagTemplate(
        id = "python.http.client.httpx.route_group_client",
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "httpx/route_group_client.ssp",
        outputFile = "clients/{{tagName}}_client.py"
      )
    )

  def librarySpecific(libraryKey: String): List[CodegenOutput] =
    libraryKey match {
      case "httpx" => httpx
      case other   => throw new IllegalArgumentException(s"unsupported HTTP client library key: $other")
    }

  def forEnabledLibraries(
      libraryKeys: List[String],
      routeGroupTags: List[String]
  ): List[CodegenOutput] = {
    val _ = routeGroupTags
    if (libraryKeys.isEmpty) {
      sharedPerService
    } else {
      sharedPerService ++ libraryKeys.flatMap(librarySpecific)
    }
  }
}
