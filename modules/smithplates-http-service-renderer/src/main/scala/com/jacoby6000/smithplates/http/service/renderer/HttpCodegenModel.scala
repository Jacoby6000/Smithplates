package com.jacoby6000.smithplates.http.service.renderer

import com.jacoby6000.smithplates.http.model.HttpOperation
import com.jacoby6000.smithplates.http.model.HttpRouteGroup
import com.jacoby6000.smithplates.http.model.HttpService
import software.amazon.smithy.model.shapes.ShapeId

final case class HttpCodegenServiceContext(
    shapeId: ShapeId,
    name: String,
    namespace: String,
    version: String,
    title: Option[String],
    documentation: Option[String],
    frameworkKey: String,
    packageName: String,
    modelsPackageName: String,
    routeGroups: List[HttpCodegenRouteGroupContext],
    serviceErrors: List[ShapeId]
)

final case class HttpCodegenRouteGroupContext(
    tag: String,
    apiModuleName: String,
    protocolClassName: String,
    operations: List[HttpOperation]
)

object HttpCodegenServiceContext {
  def fromService(service: HttpService, frameworkKey: String, packageName: String): HttpCodegenServiceContext =
    HttpCodegenServiceContext(
      shapeId = service.shapeId,
      name = service.shapeId.getName,
      namespace = service.shapeId.getNamespace,
      version = service.version,
      title = service.title,
      documentation = service.documentation,
      frameworkKey = frameworkKey,
      packageName = packageName,
      modelsPackageName = s"$packageName.models",
      routeGroups = service.routeGroups.map(HttpCodegenRouteGroupContext.fromRouteGroup),
      serviceErrors = service.serviceErrors
    )
}

object HttpCodegenRouteGroupContext {
  def fromRouteGroup(routeGroup: HttpRouteGroup): HttpCodegenRouteGroupContext =
    HttpCodegenRouteGroupContext(
      tag = routeGroup.tag,
      apiModuleName = routeGroup.apiModuleName,
      protocolClassName = routeGroup.protocolClassName,
      operations = routeGroup.operations
    )
}

final case class HttpCodegenArtifact(
    relativePath: String,
    content: String,
    kind: HttpServiceCodegenArtifactKind
)

enum HttpServiceCodegenArtifactKind {
  case Src
  case Test
}
