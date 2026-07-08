package com.jacoby6000.smithplates.http.service.renderer

import com.jacoby6000.smithplates.codegen.core.ServiceModel
import com.jacoby6000.smithplates.codegen.core.planning.TemplateView
import com.jacoby6000.smithplates.http.codegen.HttpMeta

/** Neutral [[TemplateView]] helpers for HTTP service-scoped SSP templates. */
object HttpNeutralServiceTemplateAttributes {
  type ServiceView = TemplateView[ServiceModel[HttpMeta, HttpMeta], HttpMeta]

  def packageName(ctx: ServiceView): String =
    ctx.conventions.packageName(ctx.subject.id.namespace)

  def serviceName(ctx: ServiceView): String =
    ctx.subject.id.name
}
