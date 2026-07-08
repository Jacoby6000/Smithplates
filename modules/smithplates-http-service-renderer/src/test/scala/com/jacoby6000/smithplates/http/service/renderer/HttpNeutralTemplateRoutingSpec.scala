package com.jacoby6000.smithplates.http.service.renderer

import munit.FunSuite

class HttpNeutralTemplateRoutingSpec extends FunSuite {
  test("isNeutralServiceTemplate matches migrated service templates") {
    assert(HttpNeutralTemplateRouting.isNeutralServiceTemplate("classpath:python/src/http/server/api_response.ssp"))
    assert(HttpNeutralTemplateRouting.isNeutralServiceTemplate("app_services.ssp"))
    assert(HttpNeutralTemplateRouting.isNeutralServiceTemplate("client_registry.ssp"))
    assert(HttpNeutralTemplateRouting.isNeutralServiceTemplate("client_response.ssp"))
    assert(HttpNeutralTemplateRouting.isNeutralServiceTemplate("file:/tmp/additional/apis/__init__.ssp"))
    assert(!HttpNeutralTemplateRouting.isNeutralServiceTemplate("app_factory.ssp"))
    assert(!HttpNeutralTemplateRouting.isNeutralServiceTemplate("fastapi/route_group_routes.ssp"))
  }
}
