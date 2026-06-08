package com.jacoby6000.smithy.stache.sql.codegen

object SqlCodegenTemplateAttributes {
  def forService(context: SqlCodegenServiceContext): ServiceTemplateView =
    SqlCodegenTemplateViews.buildServiceView(context)

  def renderOutputPath(pattern: String, context: SqlCodegenServiceContext, templateRoot: String): String =
    pattern
      .replace("{{serviceName}}", context.name)
      .replace("{{serviceClassName}}", context.name)
      .replace(
        "{{serviceFileName}}",
        ScalateSspTemplateEngine.renderServiceModuleBaseName(templateRoot, context.name)
      )
      .replace("{{serviceNamespace}}", context.namespace)
      .replace("{{serviceShapeId}}", context.shapeId.toString)
      .replace("{{serviceVersion}}", context.version)
}
