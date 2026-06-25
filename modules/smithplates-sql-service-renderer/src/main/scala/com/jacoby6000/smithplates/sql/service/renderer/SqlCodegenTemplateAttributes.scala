package com.jacoby6000.smithplates.sql.service.renderer

object SqlCodegenTemplateAttributes {
  def forService(context: SqlCodegenServiceContext): ServiceTemplateView =
    SqlCodegenTemplateViews.buildServiceView(context)
}
