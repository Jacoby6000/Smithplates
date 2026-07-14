package com.jacoby6000.smithplates.http.service.renderer

import com.jacoby6000.smithplates.codegen.core.ModelId
import com.jacoby6000.smithplates.codegen.core.NeutralType.ModelRef
import com.jacoby6000.smithplates.codegen.core.OperationModel
import com.jacoby6000.smithplates.codegen.core.ServiceModel
import com.jacoby6000.smithplates.codegen.core.planning.TemplateView
import com.jacoby6000.smithplates.http.codegen.HttpMeta
import com.jacoby6000.smithplates.http.codegen.HttpOperationMeta
import com.jacoby6000.smithplates.http.codegen.HttpServiceErrorMeta
import com.jacoby6000.smithplates.http.codegen.HttpServiceMeta

/** Neutral [[TemplateView]] helpers for HTTP service-scoped SSP templates. */
object HttpNeutralServiceTemplateAttributes {
  type ServiceView = TemplateView[ServiceModel[HttpServiceMeta, HttpOperationMeta], HttpMeta]

  def packageName(ctx: ServiceView): String =
    ctx.conventions.packageName(ctx.subject.id.namespace)

  def serviceName(ctx: ServiceView): String =
    ctx.subject.id.name

  def serviceTitle(ctx: ServiceView): String =
    ctx.subject.meta.feature.title.getOrElse(ctx.subject.id.name)

  def serviceVersion(ctx: ServiceView): String =
    ctx.subject.meta.feature.version

  def serviceDocumentation(ctx: ServiceView): Option[String] =
    ctx.subject.meta.documentation

  def serviceErrors(ctx: ServiceView): List[HttpServiceErrorMeta] =
    ctx.subject.meta.feature.serviceErrors

  def serviceErrorsNeedProblemImport(ctx: ServiceView): Boolean =
    serviceErrors(ctx).exists(_.error.isDefined)

  def serviceErrorUsesProblem(error: HttpServiceErrorMeta): Boolean =
    error.error.isDefined

  def serviceErrorExceptionName(errorName: String): String =
    s"${errorName}ApiError"

  def serviceErrorHandlerName(ctx: ServiceView, errorName: String): String =
    s"handle_${ctx.conventions.memberName(errorName)}_api_error"

  def serviceErrorRegistrationName(ctx: ServiceView, errorName: String): String =
    s"on_${ctx.conventions.memberName(errorName)}_api_error"

  def httpProblemImportModule(ctx: ServiceView): String =
    HttpCodegenProblemBase.importModule(ctx)

  def httpProblemClassName: String =
    HttpCodegenProblemBase.ClassName

  def routerImportAlias(moduleName: String): String =
    internal.tagSegments(moduleName).map(internal.capitalizeSegment).mkString + "Router"

  def routeGroupTags(ctx: ServiceView): List[String] =
    ctx.subject.operations
      .groupBy(operation => operation.meta.tags.headOption.getOrElse("default"))
      .keys
      .toList
      .sorted

  def operations(ctx: ServiceView): List[OperationModel[HttpOperationMeta]] =
    ctx.subject.operations

  def operationMethodName(ctx: ServiceView, operationName: String): String =
    ctx.conventions.functionName(operationName)

  def operationBindingKeys(ctx: ServiceView, operation: OperationModel[HttpOperationMeta]): List[String] = {
    val methodName = operationMethodName(ctx, operation.id.name)
    if (methodName == operation.id.name) {
      List(methodName)
    } else {
      List(methodName, operation.id.name)
    }
  }

  def responseVariantMediaType(mediaType: Option[String]): String =
    mediaType.map(value => s"'$value'").getOrElse("None")

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
      .serviceErrorModelRefs(ctx)
      .find(ref => ctx.conventions.className(ref.id) == typeName)
      .orElse(internal.responseModelRefs(ctx).find(ref => ctx.conventions.className(ref.id) == typeName))
      .orElse(
        ctx.usedTypes
          .find(model => ctx.conventions.className(model.id) == typeName)
          .map(model => ModelRef(model.id))
      )
      .map(ref => ctx.conventions.modulePath(ref.id))
      .getOrElse {
        val moduleBase =
          ctx.conventions.fileStem(ModelId("", typeName))
        s"${internal.modelsPackageName(ctx)}.$moduleBase"
      }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def responseModelRefs(ctx: ServiceView): List[ModelRef] = {
      val successRefs =
        ctx.subject.operations.flatMap { operation =>
          val variantRefs = operation.meta.feature.responseVariants
            .filter(_.statusCode == operation.meta.feature.successStatus)
            .flatMap(_.modelShapeId)
            .map(ModelRef.apply)
          if (variantRefs.nonEmpty) variantRefs else operation.output.toList
        }
      val errorRefs = ctx.subject.operations.flatMap(_.errors)
      (successRefs ++ errorRefs).distinct
    }

    def serviceErrorModelRefs(ctx: ServiceView): List[ModelRef] =
      serviceErrors(ctx).map(error => ModelRef(error.id))

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
