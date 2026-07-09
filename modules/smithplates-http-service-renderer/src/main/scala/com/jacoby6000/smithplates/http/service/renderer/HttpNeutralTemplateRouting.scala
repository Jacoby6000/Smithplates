package com.jacoby6000.smithplates.http.service.renderer

/** Routes migrated HTTP SSP templates through the neutral
  * [[com.jacoby6000.smithplates.codegen.core.planning.TemplateView]] renderer.
  */
object HttpNeutralTemplateRouting {
  private val neutralServiceTemplateSuffixes: Set[String] =
    Set(
      "model_validation.ssp",
      "api_response.ssp",
      "app_services.ssp",
      "client_registry.ssp",
      "client_response.ssp",
      "api_exceptions.ssp",
      "api_exception_handler.ssp",
      "app_factory.ssp",
      "operation_bindings.ssp",
      "apis/__init__.ssp",
      "clients/__init__.ssp"
    )

  def isNeutralServiceTemplate(templatePath: String): Boolean = {
    val normalized = normalize(templatePath)
    neutralServiceTemplateSuffixes.exists(suffix => normalized == suffix || normalized.endsWith(s"/$suffix"))
  }

  private val neutralRouteGroupTemplateSuffixes: Set[String] =
    Set(
      "fastapi/route_group_protocol.ssp",
      "fastapi/route_group_routes.ssp",
      "httpx/route_group_client.ssp"
    )

  def isNeutralRouteGroupTemplate(templatePath: String): Boolean = {
    val normalized = normalize(templatePath)
    neutralRouteGroupTemplateSuffixes.exists(suffix => normalized == suffix || normalized.endsWith(s"/$suffix"))
  }

  private def normalize(templatePath: String): String =
    templatePath.stripPrefix("classpath:").stripPrefix("file:")
}
