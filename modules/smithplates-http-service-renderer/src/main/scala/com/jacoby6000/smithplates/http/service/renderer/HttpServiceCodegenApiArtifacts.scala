package com.jacoby6000.smithplates.http.service.renderer

/** Bundled `@httpService` artifact paths under the `api/` service-type layout. */
object HttpServiceCodegenApiArtifacts {
  val sharedPerService: List[HttpServiceCodegenArtifactConfig] =
    List(
      HttpServiceCodegenArtifactConfig(
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "app_factory.ssp",
        outputFile = "api/app_factory.py",
        scope = HttpCodegenArtifactScope.Service
      ),
      HttpServiceCodegenArtifactConfig(
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "app_services.ssp",
        outputFile = "api/app_services.py",
        scope = HttpCodegenArtifactScope.Service
      ),
      HttpServiceCodegenArtifactConfig(
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "api_response.ssp",
        outputFile = "api/api_response.py",
        scope = HttpCodegenArtifactScope.Service
      ),
      HttpServiceCodegenArtifactConfig(
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "operation_bindings.ssp",
        outputFile = "api/operation_bindings.py",
        scope = HttpCodegenArtifactScope.Service
      ),
      HttpServiceCodegenArtifactConfig(
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "api_exceptions.ssp",
        outputFile = "api/api_exceptions.py",
        scope = HttpCodegenArtifactScope.Service
      ),
      HttpServiceCodegenArtifactConfig(
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "api_exception_handler.ssp",
        outputFile = "api/api_exception_handler.py",
        scope = HttpCodegenArtifactScope.Service
      ),
      HttpServiceCodegenArtifactConfig(
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "apis/__init__.ssp",
        outputFile = "api/apis/__init__.py",
        scope = HttpCodegenArtifactScope.Service
      ),
      HttpServiceCodegenArtifactConfig(
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "__init__.ssp",
        outputFile = "api/models/__init__.py",
        scope = HttpCodegenArtifactScope.Service,
        templateDirectoryOverride = Some(PythonTemplateNamespaces.bundledHttpModelsTemplateDirectory)
      ),
      HttpServiceCodegenArtifactConfig(
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "problem.ssp",
        outputFile = "api/models/problem.py",
        scope = HttpCodegenArtifactScope.Service,
        templateDirectoryOverride = Some(PythonTemplateNamespaces.bundledHttpModelsTemplateDirectory)
      )
    )

  def fastapi(routeGroupTag: String): List[HttpServiceCodegenArtifactConfig] =
    List(
      HttpServiceCodegenArtifactConfig(
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "fastapi/route_group_protocol.ssp",
        outputFile = s"api/apis/${routeGroupTag}_api_base.py",
        scope = HttpCodegenArtifactScope.RouteGroup(routeGroupTag)
      ),
      HttpServiceCodegenArtifactConfig(
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "fastapi/route_group_routes.ssp",
        outputFile = s"api/apis/${routeGroupTag}_api.py",
        scope = HttpCodegenArtifactScope.RouteGroup(routeGroupTag)
      )
    )

  def frameworkSpecific(frameworkKey: String, routeGroupTags: List[String]): List[HttpServiceCodegenArtifactConfig] =
    frameworkKey match {
      case "fastapi" => routeGroupTags.flatMap(fastapi)
      case other     => throw new IllegalArgumentException(s"unsupported HTTP framework key: $other")
    }

  def forEnabledFrameworks(
      frameworkKeys: List[String],
      routeGroupTags: List[String]
  ): List[HttpServiceCodegenArtifactConfig] =
    if (frameworkKeys.isEmpty) {
      sharedPerService
    } else {
      sharedPerService ++ frameworkKeys.flatMap(frameworkKey => frameworkSpecific(frameworkKey, routeGroupTags))
    }
}

enum HttpCodegenArtifactScope {
  case Service
  case RouteGroup(tag: String)
}

final case class HttpServiceCodegenArtifactConfig(
    kind: HttpServiceCodegenArtifactKind,
    template: String,
    outputFile: String,
    scope: HttpCodegenArtifactScope,
    templateDirectoryOverride: Option[String] = None
)

final case class HttpServiceCodegenSettings(
    templateDirectory: String,
    defaultFrameworkKey: String,
    enabledFrameworkKeys: List[String],
    packageName: String,
    sourceOutputDirectory: Option[String] = None,
    testOutputDirectory: Option[String] = None,
    artifacts: List[HttpServiceCodegenArtifactConfig],
    serviceTypePrefix: String = "api",
    modelTemplateDirectory: Option[String] = None
) {
  def resolvedModelTemplateDirectory: String =
    modelTemplateDirectory.getOrElse(templateDirectory)
}
