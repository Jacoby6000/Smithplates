package com.jacoby6000.smithplates.http.service.renderer

final case class HttpCodegenTemplateView(
    service: HttpCodegenServiceContext,
    routeGroup: Option[HttpCodegenRouteGroupContext] = None
)
