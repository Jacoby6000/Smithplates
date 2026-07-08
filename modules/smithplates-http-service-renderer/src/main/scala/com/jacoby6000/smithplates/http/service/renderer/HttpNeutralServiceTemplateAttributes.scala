package com.jacoby6000.smithplates.http.service.renderer

import com.jacoby6000.smithplates.codegen.core.ServiceModel
import com.jacoby6000.smithplates.codegen.core.planning.TemplateView
import com.jacoby6000.smithplates.http.codegen.HttpMeta
import com.jacoby6000.smithplates.http.codegen.HttpOperationMeta
import com.jacoby6000.smithplates.http.codegen.HttpServiceMeta

/** Neutral [[TemplateView]] helpers for HTTP service-scoped SSP templates. */
object HttpNeutralServiceTemplateAttributes {
  type ServiceView = TemplateView[ServiceModel[HttpServiceMeta, HttpOperationMeta], HttpMeta]

  def packageName(ctx: ServiceView): String =
    ctx.conventions.packageName(ctx.subject.id.namespace)

  def serviceName(ctx: ServiceView): String =
    ctx.subject.id.name

  def routeGroupTags(ctx: ServiceView): List[String] =
    ctx.subject.operations
      .groupBy(operation => operation.meta.tags.headOption.getOrElse("default"))
      .keys
      .toList
      .sorted

  def apiModuleName(tag: String): String =
    s"${tag}_api"

  def protocolClassName(tag: String): String =
    internal.tagSegments(tag).map(internal.capitalizeSegment).mkString + "ApiServiceProtocol"

  def clientClassName(tag: String): String =
    internal.tagSegments(tag).map(internal.capitalizeSegment).mkString + "ApiClient"

  def clientModuleName(tag: String): String =
    s"${tag}_client"

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def tagSegments(tag: String): List[String] =
      tag.split("[_\\-]+").toList.filter(_.nonEmpty)

    def capitalizeSegment(segment: String): String =
      if (segment.isEmpty) {
        segment
      } else {
        s"${segment.head.toUpper}${segment.tail}"
      }
  }
}
