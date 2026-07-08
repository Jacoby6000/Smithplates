package com.jacoby6000.smithplates.http.service.renderer

import com.jacoby6000.smithplates.codegen.core.ModelId
import com.jacoby6000.smithplates.codegen.core.NeutralType.ModelRef
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

  def responseModelTypeNames(ctx: ServiceView): List[String] =
    internal
      .responseModelRefs(ctx)
      .map(ref => ctx.conventions.className(ref.id))
      .distinct
      .sorted

  def modelTypeImportModule(ctx: ServiceView, typeName: String): String =
    internal
      .responseModelRefs(ctx)
      .find(ref => ctx.conventions.className(ref.id) == typeName)
      .orElse(
        ctx.usedTypes
          .find(model => ctx.conventions.className(model.id) == typeName)
          .map(model => ModelRef(model.id))
      )
      .map(ref => ctx.conventions.modulePath(ref.id))
      .getOrElse {
        val moduleBase =
          ctx.conventions.fileName(ModelId("", typeName)).stripSuffix(".py")
        s"${internal.modelsPackageName(ctx)}.$moduleBase"
      }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def responseModelRefs(ctx: ServiceView): List[ModelRef] =
      ctx.subject.operations.flatMap(operation => operation.output.toList ++ operation.errors).distinct

    def modelsPackageName(ctx: ServiceView): String =
      s"${packageName(ctx)}.models"

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
