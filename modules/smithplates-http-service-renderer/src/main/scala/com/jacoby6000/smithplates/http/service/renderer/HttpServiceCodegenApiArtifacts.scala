package com.jacoby6000.smithplates.http.service.renderer

/** Bundled `@httpService` artifact paths relative to the HTTP server template root. */
object HttpServiceCodegenApiArtifacts {
  val sharedPerService: List[HttpServiceCodegenArtifactConfig] =
    List(
      HttpServiceCodegenArtifactConfig(
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "app_factory.ssp",
        outputFile = "app_factory.py",
        scope = HttpCodegenArtifactScope.Service
      ),
      HttpServiceCodegenArtifactConfig(
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "app_services.ssp",
        outputFile = "app_services.py",
        scope = HttpCodegenArtifactScope.Service
      ),
      HttpServiceCodegenArtifactConfig(
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "api_response.ssp",
        outputFile = "api_response.py",
        scope = HttpCodegenArtifactScope.Service
      ),
      HttpServiceCodegenArtifactConfig(
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "operation_bindings.ssp",
        outputFile = "operation_bindings.py",
        scope = HttpCodegenArtifactScope.Service
      ),
      HttpServiceCodegenArtifactConfig(
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "api_exceptions.ssp",
        outputFile = "api_exceptions.py",
        scope = HttpCodegenArtifactScope.Service
      ),
      HttpServiceCodegenArtifactConfig(
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "api_exception_handler.ssp",
        outputFile = "api_exception_handler.py",
        scope = HttpCodegenArtifactScope.Service
      ),
      HttpServiceCodegenArtifactConfig(
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "apis/__init__.ssp",
        outputFile = "apis/__init__.py",
        scope = HttpCodegenArtifactScope.Service
      )
    )

  val sharedModels: List[HttpServiceCodegenArtifactConfig] =
    List(
      HttpServiceCodegenArtifactConfig(
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "__init__.ssp",
        outputFile = "__init__.py",
        scope = HttpCodegenArtifactScope.Service,
        templateSource = HttpCodegenTemplateSource.Models
      ),
      HttpServiceCodegenArtifactConfig(
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "problem.ssp",
        outputFile = "problem.py",
        scope = HttpCodegenArtifactScope.Service,
        templateSource = HttpCodegenTemplateSource.Models
      )
    )

  def fastapi(routeGroupTag: String): List[HttpServiceCodegenArtifactConfig] =
    List(
      HttpServiceCodegenArtifactConfig(
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "fastapi/route_group_protocol.ssp",
        outputFile = s"apis/${routeGroupTag}_api_base.py",
        scope = HttpCodegenArtifactScope.RouteGroup(routeGroupTag)
      ),
      HttpServiceCodegenArtifactConfig(
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "fastapi/route_group_routes.ssp",
        outputFile = s"apis/${routeGroupTag}_api.py",
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
      routeGroupTags: List[String],
      emitModels: Boolean
  ): List[HttpServiceCodegenArtifactConfig] = {
    val frameworkArtifacts =
      if (frameworkKeys.isEmpty) {
        Nil
      } else {
        frameworkKeys.flatMap(frameworkKey => frameworkSpecific(frameworkKey, routeGroupTags))
      }
    sharedPerService ++ frameworkArtifacts ++ (if (emitModels) sharedModels else Nil)
  }
}

enum HttpCodegenArtifactScope {
  case Service
  case RouteGroup(tag: String)
}

enum HttpCodegenTemplateSource {
  case Service
  case Models
}

final case class HttpServiceCodegenArtifactConfig(
    kind: HttpServiceCodegenArtifactKind,
    template: String,
    outputFile: String,
    scope: HttpCodegenArtifactScope,
    templateSource: HttpCodegenTemplateSource = HttpCodegenTemplateSource.Service
)

final case class HttpServiceCodegenSettings(
    templateDirectory: String,
    defaultFrameworkKey: String,
    enabledFrameworkKeys: List[String],
    packageName: String,
    sourceOutputDirectory: Option[String] = None,
    testOutputDirectory: Option[String] = None,
    artifacts: List[HttpServiceCodegenArtifactConfig],
    outputPrefix: String,
    modelsPackageName: String,
    modelsOutputPrefix: String,
    emitModels: Boolean = true,
    modelTemplateDirectory: Option[String] = None
) {
  def resolvedModelTemplateDirectory: String =
    modelTemplateDirectory.getOrElse(templateDirectory)
}
