package com.jacoby6000.smithplates.http.service.renderer

import com.jacoby6000.smithplates.http.model.HttpRouteGroup
import com.jacoby6000.smithplates.http.model.HttpService

final case class HttpCodegenTemplateView(
    service: HttpService,
    packageName: String,
    routeGroup: Option[HttpRouteGroup] = None
)
