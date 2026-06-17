package com.jacoby6000.smithplates.http.service.renderer

/** Bundled `@httpService` client artifact paths relative to the HTTP client template root. */
object HttpClientCodegenApiArtifacts {
  val sharedPerService: List[HttpServiceCodegenArtifactConfig] =
    List(
      HttpServiceCodegenArtifactConfig(
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "operation_bindings.ssp",
        outputFile = "operation_bindings.py",
        scope = HttpCodegenArtifactScope.Service
      ),
      HttpServiceCodegenArtifactConfig(
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "client_response.ssp",
        outputFile = "client_response.py",
        scope = HttpCodegenArtifactScope.Service
      ),
      HttpServiceCodegenArtifactConfig(
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "client_registry.ssp",
        outputFile = "client_registry.py",
        scope = HttpCodegenArtifactScope.Service
      ),
      HttpServiceCodegenArtifactConfig(
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "clients/__init__.ssp",
        outputFile = "clients/__init__.py",
        scope = HttpCodegenArtifactScope.Service
      )
    )

  def httpx(routeGroupTag: String): List[HttpServiceCodegenArtifactConfig] =
    List(
      HttpServiceCodegenArtifactConfig(
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "httpx/route_group_client.ssp",
        outputFile = s"clients/${routeGroupTag}_client.py",
        scope = HttpCodegenArtifactScope.RouteGroup(routeGroupTag)
      )
    )

  def librarySpecific(libraryKey: String, routeGroupTags: List[String]): List[HttpServiceCodegenArtifactConfig] =
    libraryKey match {
      case "httpx" => routeGroupTags.flatMap(httpx)
      case other   => throw new IllegalArgumentException(s"unsupported HTTP client library key: $other")
    }

  def forEnabledLibraries(
      libraryKeys: List[String],
      routeGroupTags: List[String]
  ): List[HttpServiceCodegenArtifactConfig] =
    if (libraryKeys.isEmpty) {
      sharedPerService
    } else {
      sharedPerService ++ libraryKeys.flatMap(libraryKey => librarySpecific(libraryKey, routeGroupTags))
    }
}
