package com.jacoby6000.smithplates.http.service.renderer

import com.jacoby6000.smithplates.codegen.core.ModelId
import com.jacoby6000.smithplates.codegen.core.ModelSet
import com.jacoby6000.smithplates.codegen.core.NeutralType
import com.jacoby6000.smithplates.codegen.core.NeutralType.*
import com.jacoby6000.smithplates.codegen.core.OperationModel
import com.jacoby6000.smithplates.codegen.core.ServiceModel
import com.jacoby6000.smithplates.codegen.core.TimestampFormat
import com.jacoby6000.smithplates.codegen.core.TypeResolver
import com.jacoby6000.smithplates.codegen.core.planning.TemplateView
import com.jacoby6000.smithplates.codegen.core.strategy.RenderContext
import com.jacoby6000.smithplates.http.HttpSmithyTypeResolver
import com.jacoby6000.smithplates.http.codegen.HttpInputMemberBindingMeta
import com.jacoby6000.smithplates.http.codegen.HttpMeta
import com.jacoby6000.smithplates.http.codegen.HttpOperationBodyBindingMeta
import com.jacoby6000.smithplates.http.codegen.HttpOperationInputMemberMeta
import com.jacoby6000.smithplates.http.codegen.HttpOperationMeta
import com.jacoby6000.smithplates.http.codegen.HttpServiceErrorMeta
import com.jacoby6000.smithplates.http.codegen.HttpServiceMeta
import com.jacoby6000.smithplates.http.model.HttpTimestampFormat

/** Neutral [[TemplateView]] helpers for HTTP service-scoped SSP templates. */
object HttpNeutralServiceTemplateAttributes {
  type ServiceView = TemplateView[ServiceModel[HttpServiceMeta, HttpOperationMeta], HttpMeta]

  def packageName(ctx: ServiceView): String =
    ctx.conventions.packageName(ctx.subject.id.namespace)

  def servicePackageName(ctx: ServiceView): String =
    s"${packageName(ctx)}${ctx.conventions.packageSeparator}${serviceModuleName(ctx)}"

  def namespaceModuleDir(ctx: ServiceView): String = {
    val namespace = ctx.subject.id.namespace
    val dir       = namespace.split('.').filter(_.nonEmpty).mkString("/")
    s"${ctx.conventions.rootNamespaceDir}/$dir"
  }

  def serviceModuleDir(ctx: ServiceView): String =
    s"${namespaceModuleDir(ctx)}/${serviceModuleName(ctx)}"

  def serviceName(ctx: ServiceView): String =
    ctx.subject.id.name

  def serviceModuleName(ctx: ServiceView): String =
    ctx.conventions.memberName(ctx.subject.id.name)

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

  def clientModuleName(ctx: ServiceView, tag: String): String =
    clientModuleName(ctx.conventions.memberName(tag))

  def syncClientModuleName(ctx: ServiceView, tag: String): String =
    s"${ctx.conventions.memberName(tag)}_sync_client"

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
      .orElse(
        ctx.subject.meta.feature.modelNamespaces
          .get(typeName)
          .map(namespace => ModelRef(ModelId(namespace, typeName)))
      )
      .map(ref => ctx.conventions.modulePath(ref.id))
      .getOrElse {
        val moduleBase =
          ctx.conventions.fileStem(ModelId("", typeName))
        s"${internal.modelsPackageName(ctx)}.$moduleBase"
      }

  /** WebSocket operations on this service. A websocket operation's input shape is the union/structure of
    * client-to-server messages and its output shape is the union/structure of server-to-client messages.
    */
  def websocketOperations(ctx: ServiceView): List[OperationModel[HttpOperationMeta]] =
    operations(ctx).filter(_.meta.feature.websocket.isDefined)

  def hasWebsockets(ctx: ServiceView): Boolean =
    websocketOperations(ctx).nonEmpty

  def websocketPath(operation: OperationModel[HttpOperationMeta]): String =
    operation.meta.feature.websocket.map(_.path).getOrElse(operation.meta.feature.uriPattern)

  /** Path label members of a websocket operation's input. These become route parameters in the generated FastAPI
    * websocket handler and client connect method.
    */
  def websocketPathLabels(
      operation: OperationModel[HttpOperationMeta]
  ): List[HttpOperationInputMemberMeta] =
    operation.meta.feature.inputMembers.filter(_.binding == HttpInputMemberBindingMeta.PathLabel)

  /** Python parameter name (snake_case) for a path label member. */
  def websocketPathLabelName(
      ctx: ServiceView,
      member: HttpOperationInputMemberMeta
  ): String =
    ctx.conventions.memberName(member.name)

  /** WebSocket route path with URI labels converted to Python parameter names (snake_case) so they match function
    * parameters and f-string interpolation variables.
    */
  def websocketPythonPath(
      ctx: ServiceView,
      operation: OperationModel[HttpOperationMeta]
  ): String =
    websocketPathLabels(operation).foldLeft(websocketPath(operation)) { case (path, member) =>
      val label       = "{" + member.name + "}"
      val pythonLabel = "{" + websocketPathLabelName(ctx, member) + "}"
      path.replace(label, pythonLabel)
    }

  def websocketPythonClientPath(
      ctx: ServiceView,
      operation: OperationModel[HttpOperationMeta]
  ): String =
    websocketPathLabels(operation).foldLeft(websocketPath(operation)) { case (path, member) =>
      val parameter = websocketPathLabelName(ctx, member)
      path
        .replace("{" + member.name + "+}", "{quote(str(" + parameter + "), safe=\"/\")}")
        .replace("{" + member.name + "}", "{quote(str(" + parameter + "), safe=\"\")}")
    }

  /** Whether the websocket operation has client-to-server message content (body members). Path-label-only inputs define
    * routing parameters, not messages.
    */
  def websocketHasInputMessages(operation: OperationModel[HttpOperationMeta]): Boolean =
    operation.meta.feature.bodyBinding != HttpOperationBodyBindingMeta.None

  def websocketBodyMembers(operation: OperationModel[HttpOperationMeta]): List[HttpOperationInputMemberMeta] =
    operation.meta.feature.bodyBinding match {
      case HttpOperationBodyBindingMeta.Members(members) => members
      case _                                             => Nil
    }

  def websocketUsesMemberMessage(operation: OperationModel[HttpOperationMeta]): Boolean =
    websocketBodyMembers(operation).nonEmpty

  def websocketMemberMessageTypeName(
      ctx: ServiceView,
      operation: OperationModel[HttpOperationMeta]
  ): String =
    s"${ctx.conventions.className(operation.id)}InputMessage"

  def websocketMemberName(ctx: ServiceView, member: HttpOperationInputMemberMeta): String =
    ctx.conventions.memberName(member.name)

  def websocketMemberTypeAnnotation(ctx: ServiceView, member: HttpOperationInputMemberMeta): String =
    internal.renderMemberType(ctx, member)

  def websocketMemberModelImports(
      ctx: ServiceView,
      operation: OperationModel[HttpOperationMeta]
  ): List[(String, String)] =
    websocketBodyMembers(operation)
      .flatMap(member => internal.referencedModelTypeNames(member.typeName))
      .map(typeName => typeName -> modelTypeImportModule(ctx, typeName))
      .distinct

  def websocketHandlerName(ctx: ServiceView, operation: OperationModel[HttpOperationMeta]): String =
    s"handle_${ctx.conventions.functionName(operation.id.name)}"

  def websocketConnectionName(ctx: ServiceView, operation: OperationModel[HttpOperationMeta]): String =
    s"${ctx.conventions.className(operation.id)}Connection"

  def websocketInputRef(operation: OperationModel[HttpOperationMeta]): Option[ModelRef] =
    operation.input

  def websocketOutputRef(operation: OperationModel[HttpOperationMeta]): Option[ModelRef] =
    operation.output

  def websocketInputTypeName(ctx: ServiceView, operation: OperationModel[HttpOperationMeta]): Option[String] =
    operation.meta.feature.bodyBinding match {
      case HttpOperationBodyBindingMeta.None                     => None
      case HttpOperationBodyBindingMeta.Document(inputShapeName) => Some(inputShapeName)
      case HttpOperationBodyBindingMeta.Members(_ :: _)          => Some(websocketMemberMessageTypeName(ctx, operation))
      case HttpOperationBodyBindingMeta.Members(Nil)             => None
      case nested: HttpOperationBodyBindingMeta.NestedDocument   => Some(nested.payloadTargetShapeName)
    }

  def websocketInputModelTypeName(ctx: ServiceView, operation: OperationModel[HttpOperationMeta]): Option[String] =
    operation.meta.feature.bodyBinding match {
      case HttpOperationBodyBindingMeta.None                   => None
      case HttpOperationBodyBindingMeta.Document(name)         => Some(name)
      case HttpOperationBodyBindingMeta.Members(_)             => None
      case nested: HttpOperationBodyBindingMeta.NestedDocument => Some(nested.payloadTargetShapeName)
    }

  def websocketOutputTypeName(ctx: ServiceView, operation: OperationModel[HttpOperationMeta]): Option[String] =
    operation.output.map(ref => ctx.conventions.className(ref.id))

  def websocketInputModule(ctx: ServiceView, operation: OperationModel[HttpOperationMeta]): Option[String] =
    websocketInputModelTypeName(ctx, operation).map(modelTypeImportModule(ctx, _))

  def websocketOutputModule(ctx: ServiceView, operation: OperationModel[HttpOperationMeta]): Option[String] =
    operation.output.map(ref => ctx.conventions.modulePath(ref.id))

  def websocketInputValidateFunction(ctx: ServiceView, operation: OperationModel[HttpOperationMeta]): Option[String] =
    operation.input.map(ref => internal.validateFunctionName(ctx, ref))

  def websocketOutputValidateFunction(ctx: ServiceView, operation: OperationModel[HttpOperationMeta]): Option[String] =
    operation.output.map(ref => internal.validateFunctionName(ctx, ref))

  def websocketInputParseFunction(ctx: ServiceView, operation: OperationModel[HttpOperationMeta]): Option[String] =
    operation.input.map(ref => internal.parseFunctionName(ctx, ref))

  def websocketOutputParseFunction(ctx: ServiceView, operation: OperationModel[HttpOperationMeta]): Option[String] =
    operation.output.map(ref => internal.parseFunctionName(ctx, ref))

  def websocketInputSerializeFunction(ctx: ServiceView, operation: OperationModel[HttpOperationMeta]): Option[String] =
    websocketInputModelTypeName(ctx, operation).map(typeName => s"serialize$typeName")

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def referencedModelTypeNames(typeName: String): List[String] =
      if (typeName.startsWith("List[")) {
        referencedModelTypeNames(typeName.substring(5, typeName.length - 1))
      } else if (typeName.startsWith("Map[String, ")) {
        referencedModelTypeNames(typeName.substring(12, typeName.length - 1))
      } else if (HttpSmithyTypeResolver.isStructureTypeName(typeName)) {
        List(typeName)
      } else {
        Nil
      }

    def renderMemberType(ctx: ServiceView, member: HttpOperationInputMemberMeta): String =
      if (member.typeName == "Unit") {
        "None"
      } else {
        val tpe = toNeutralType(member.typeName, member.timestampFormat)
        ctx.typeRenderer.render(tpe, RenderContext(TypeResolver.fromModelSet(ModelSet(ctx.usedTypes)), ctx.conventions))
      }

    def toNeutralType(typeName: String, timestampFormat: Option[HttpTimestampFormat]): NeutralType =
      if (typeName.startsWith("List[")) {
        val inner = typeName.substring(5, typeName.length - 1)
        ListT(toNeutralType(inner, None))
      } else if (typeName.startsWith("Map[String, ")) {
        val inner = typeName.substring(12, typeName.length - 1)
        MapT(StringT, toNeutralType(inner, None))
      } else {
        typeName match {
          case "String"     => StringT
          case "Integer"    => IntegerT
          case "Long"       => LongT
          case "BigInteger" => BigIntegerT
          case "Float"      => FloatT
          case "Double"     => DoubleT
          case "BigDecimal" => BigDecimalT
          case "Boolean"    => BooleanT
          case "Blob"       => BytesT
          case "Document"   => DocumentT
          case "Timestamp"  =>
            TimestampT(timestampFormat match {
              case Some(HttpTimestampFormat.EpochSeconds) => TimestampFormat.EpochSeconds
              case _                                      => TimestampFormat.DateTime
            })
          case other        => ModelRef(ModelId("", other))
        }
      }

    def responseModelRefs(ctx: ServiceView): List[ModelRef] = {
      val successRefs =
        ctx.subject.operations.flatMap { operation =>
          val variantRefs = operation.meta.feature.responseVariants
            .filter(_.statusCode == operation.meta.feature.successStatus)
            .flatMap(_.modelShapeId)
            .map(ModelRef.apply)
          if (variantRefs.nonEmpty) variantRefs else operation.output.toList
        }
      val errorRefs   = ctx.subject.operations.flatMap(_.errors)
      (successRefs ++ errorRefs).distinct
    }

    def serviceErrorModelRefs(ctx: ServiceView): List[ModelRef] =
      serviceErrors(ctx).map(error => ModelRef(error.id))

    def modelsPackageName(ctx: ServiceView): String =
      s"${packageName(ctx)}.models"

    def validateFunctionName(ctx: ServiceView, ref: ModelRef): String = {
      val name = ctx.conventions.className(ref.id)
      s"validate_${ctx.conventions.memberName(name).stripSuffix("_")}"
    }

    def parseFunctionName(ctx: ServiceView, ref: ModelRef): String =
      s"parse${ctx.conventions.className(ref.id)}"

    def serializeFunctionName(ctx: ServiceView, ref: ModelRef): String =
      s"serialize${ctx.conventions.className(ref.id)}"

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
