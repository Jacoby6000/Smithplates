package com.jacoby6000.smithplates.http.service.renderer

import com.jacoby6000.smithplates.codegen.core.Model
import com.jacoby6000.smithplates.codegen.core.ModelId
import com.jacoby6000.smithplates.codegen.core.ModelSet
import com.jacoby6000.smithplates.codegen.core.NeutralType
import com.jacoby6000.smithplates.codegen.core.NeutralType.*
import com.jacoby6000.smithplates.codegen.core.OperationModel
import com.jacoby6000.smithplates.codegen.core.TimestampFormat
import com.jacoby6000.smithplates.codegen.core.TypeResolver
import com.jacoby6000.smithplates.codegen.core.planning.CodegenPlanner
import com.jacoby6000.smithplates.codegen.core.planning.TemplateView
import com.jacoby6000.smithplates.codegen.core.strategy.RenderContext
import com.jacoby6000.smithplates.http.HttpSmithyTypeResolver
import com.jacoby6000.smithplates.http.codegen.HttpInputMemberBindingMeta
import com.jacoby6000.smithplates.http.codegen.HttpMeta
import com.jacoby6000.smithplates.http.codegen.HttpOperationBodyBindingMeta
import com.jacoby6000.smithplates.http.codegen.HttpOperationInputMemberMeta
import com.jacoby6000.smithplates.http.codegen.HttpOperationMeta
import com.jacoby6000.smithplates.http.codegen.HttpServiceMeta
import com.jacoby6000.smithplates.http.model.HttpTimestampFormat

/** Neutral [[TemplateView]] helpers for HTTP route-group SSP templates. */
object HttpNeutralRouteGroupTemplateAttributes {
  type RouteGroupView = TemplateView[
    CodegenPlanner.internal.OperationGroupSubject[HttpServiceMeta, HttpOperationMeta],
    HttpMeta
  ]

  def tag(ctx: RouteGroupView): String =
    ctx.subject.tag

  def operations(ctx: RouteGroupView): List[OperationModel[HttpOperationMeta]] =
    ctx.subject.operations

  /** Operations in this route group that are not WebSocket endpoints. REST route/client templates render only these;
    * WebSocket operations are handled by dedicated templates.
    */
  def restOperations(ctx: RouteGroupView): List[OperationModel[HttpOperationMeta]] =
    operations(ctx).filterNot(_.meta.feature.websocket.isDefined)

  def websocketOperations(ctx: RouteGroupView): List[OperationModel[HttpOperationMeta]] =
    operations(ctx).filter(_.meta.feature.websocket.isDefined)

  def packageName(ctx: RouteGroupView): String =
    ctx.conventions.packageName(ctx.subject.service.id.namespace)

  def namespaceModuleDir(ctx: RouteGroupView): String = {
    val namespace = ctx.subject.service.id.namespace
    val dir       = namespace.split('.').filter(_.nonEmpty).mkString("/")
    s"${ctx.conventions.rootNamespaceDir}/$dir"
  }

  def operationMethodName(ctx: RouteGroupView, operationName: String): String =
    ctx.conventions.functionName(operationName)

  def protocolClassName(tag: String): String =
    HttpNeutralServiceTemplateAttributes.protocolClassName(tag)

  def clientClassName(tag: String): String =
    HttpNeutralServiceTemplateAttributes.clientClassName(tag)

  def modelTypeImportModule(ctx: RouteGroupView, typeName: String): String =
    ctx.usedTypes
      .find(model => ctx.conventions.className(model.id) == typeName)
      .map(model => ctx.conventions.modulePath(model.id))
      .orElse {
        ctx.subject.service.meta.feature.modelNamespaces.get(typeName).map { namespace =>
          ctx.conventions.modulePath(ModelId(namespace, typeName))
        }
      }
      .getOrElse {
        val moduleBase =
          ctx.conventions.fileStem(ModelId("", typeName))
        s"${packageName(ctx)}.$moduleBase"
      }

  def routeGroupUsesHeaderBinding(ctx: RouteGroupView): Boolean =
    operations(ctx).exists(_.meta.feature.inputMembers.exists(internal.isHeaderBinding))

  def routeGroupUsesPathBinding(ctx: RouteGroupView): Boolean =
    operations(ctx).exists(_.meta.feature.inputMembers.exists(internal.isPathLabelBinding))

  def routeGroupUsesQueryBinding(ctx: RouteGroupView): Boolean =
    operations(ctx).exists(_.meta.feature.inputMembers.exists(internal.isQueryBinding))

  def routeGroupHasBody(ctx: RouteGroupView): Boolean =
    operations(ctx).exists(operation => internal.operationHasBody(operation.meta.feature))

  def routeGroupNeedsDatetimeImport(ctx: RouteGroupView): Boolean =
    operations(ctx).exists(operation =>
      operation.meta.feature.inputMembers.exists(member =>
        internal.typeContainsDatetime(internal.toNeutralType(member.typeName, member.timestampFormat))))

  def operationImportedModelNames(
      ctx: RouteGroupView,
      operation: OperationModel[HttpOperationMeta]
  ): List[String] = {
    val knownModelNames       = internal.modelNameSet(ctx)
    val feature               = operation.meta.feature
    val variantTypes          =
      feature.responseVariants.map(_.variantTypeName).filterNot(_ == "__empty__")
    val inputMemberModelTypes =
      (feature.inputMembers ++ internal.bodyBindingMembers(feature))
        .flatMap(member => internal.referencedModelNames(member.typeName, knownModelNames))
    (internal.operationBodyModelNames(feature) ++ variantTypes ++ inputMemberModelTypes).distinct.sorted
  }

  def httpMemberTypeAnnotation(
      ctx: RouteGroupView,
      member: HttpOperationInputMemberMeta,
      required: Boolean
  ): String =
    if (required) {
      internal.renderMemberType(ctx, member)
    } else {
      s"${internal.renderMemberType(ctx, member)} | None"
    }

  def isRouteParameter(member: HttpOperationInputMemberMeta): Boolean =
    member.binding match {
      case HttpInputMemberBindingMeta.Payload => false
      case _                                  => true
    }

  def documentBodyInputShape(operation: OperationModel[HttpOperationMeta]): Option[String] =
    operation.meta.feature.bodyBinding match {
      case HttpOperationBodyBindingMeta.Document(inputShapeName) => Some(inputShapeName)
      case _                                                     => None
    }

  def bodyBindingMembers(operation: OperationModel[HttpOperationMeta]): List[HttpOperationInputMemberMeta] =
    internal.bodyBindingMembers(operation.meta.feature)

  def documentBodyRequired(operation: OperationModel[HttpOperationMeta]): Boolean =
    operation.meta.feature.inputMembers.exists(_.required)

  def responseTypeName(operation: OperationModel[HttpOperationMeta]): String =
    operation.meta.feature.responseVariants.find(_.statusCode == operation.meta.feature.successStatus) match {
      case None                                                    => "None"
      case Some(variant) if variant.variantTypeName == "__empty__" => "None"
      case Some(variant)                                           => variant.variantTypeName
    }

  def operationProtocolReturnType(operation: OperationModel[HttpOperationMeta]): String = {
    val feature         = operation.meta.feature
    val hasEmptySuccess =
      feature.responseVariants.exists(variant =>
        variant.statusCode == feature.successStatus && variant.variantTypeName == "__empty__")
    val types           =
      feature.responseVariants.map(_.variantTypeName).filterNot(_ == "__empty__").distinct
    val allTypes        = if (hasEmptySuccess) types :+ "None" else types
    if (allTypes.isEmpty) {
      "None"
    } else {
      allTypes.mkString(" | ")
    }
  }

  def operationServiceCallExpression(
      ctx: RouteGroupView,
      operation: OperationModel[HttpOperationMeta]
  ): String = {
    val args   = internal.operationCallArgumentList(ctx, operation)
    val target =
      s"services.${HttpNeutralServiceTemplateAttributes.apiModuleName(tag(ctx))}.${operationMethodName(ctx, operation.id.name)}"
    if (args.length <= 2) {
      s"await $target(${args.mkString(", ")})"
    } else {
      s"await $target(\n            ${args.mkString(",\n            ")},\n        )"
    }
  }

  def clientMethodParameters(
      ctx: RouteGroupView,
      operation: OperationModel[HttpOperationMeta]
  ): List[String] = {
    val feature     = operation.meta.feature
    val routeParams =
      feature.inputMembers.filter(isRouteParameter).map { member =>
        s"${routeParameterName(ctx, member.name)}: ${httpMemberTypeAnnotation(ctx, member, member.required)}"
      }
    val bodyParams  = feature.bodyBinding match {
      case HttpOperationBodyBindingMeta.Document(inputShapeName) =>
        List(s"${routeParameterName(ctx, inputShapeName)}: $inputShapeName")
      case HttpOperationBodyBindingMeta.Members(members)         =>
        members.map { member =>
          s"${routeParameterName(ctx, member.name)}: ${httpMemberTypeAnnotation(ctx, member, member.required)}"
        }
      case HttpOperationBodyBindingMeta.None                     =>
        Nil
    }
    routeParams ++ bodyParams
  }

  def clientMethodReturnType(operation: OperationModel[HttpOperationMeta]): String =
    operationProtocolReturnType(operation)

  def clientRequestUrlExpression(
      ctx: RouteGroupView,
      operation: OperationModel[HttpOperationMeta]
  ): String = {
    val pathMembers     =
      operation.meta.feature.inputMembers.filter(member => internal.isPathLabelBinding(member))
    val interpolatedUri =
      pathMembers.foldLeft(operation.meta.feature.uriPattern) { case (uri, member) =>
        uri.replace("{" + member.name + "}", "{" + routeParameterName(ctx, member.name) + "}")
      }
    s"f\"{self._base_url}$interpolatedUri\""
  }

  def clientRequestHeadersBlock(
      ctx: RouteGroupView,
      operation: OperationModel[HttpOperationMeta]
  ): String = {
    val headerMembers =
      operation.meta.feature.inputMembers.filter(member => internal.isHeaderBinding(member))
    if (headerMembers.isEmpty) {
      "        headers: dict[str, str] | None = None"
    } else {
      val assignmentLines =
        headerMembers.map { member =>
          val headerName = member.binding match {
            case HttpInputMemberBindingMeta.Header(name) => name
            case _                                       => member.name
          }
          val paramName  = routeParameterName(ctx, member.name)
          if (member.required) {
            s"""        headers["$headerName"] = str($paramName)"""
          } else {
            s"""        if $paramName is not None:
            |            headers["$headerName"] = str($paramName)""".stripMargin
          }
        }
      ("        headers: dict[str, str] = {}" +: assignmentLines).mkString("\n")
    }
  }

  def clientRequestJsonArgument(
      ctx: RouteGroupView,
      operation: OperationModel[HttpOperationMeta]
  ): String =
    operation.meta.feature.bodyBinding match {
      case HttpOperationBodyBindingMeta.None                     =>
        ""
      case HttpOperationBodyBindingMeta.Document(inputShapeName) =>
        val paramName = routeParameterName(ctx, inputShapeName)
        s", json=$paramName.model_dump(mode=\"json\", exclude_none=True)"
      case HttpOperationBodyBindingMeta.Members(members)         =>
        if (members.size == 1) {
          val member = members.head
          if (HttpSmithyTypeResolver.isStructureTypeName(member.typeName)) {
            s", json=${routeParameterName(ctx, member.name)}.model_dump(mode=\"json\", exclude_none=True)"
          } else {
            s", json=${routeParameterName(ctx, member.name)}"
          }
        } else {
          val entries =
            members.map(member => s"\"${member.name}\": ${routeParameterName(ctx, member.name)}").mkString(", ")
          s", json={$entries}"
        }
    }

  def routeParameterName(ctx: RouteGroupView, name: String): String =
    ctx.conventions.memberName(name)

  def memberName(ctx: RouteGroupView, smithyName: String): String =
    routeParameterName(ctx, smithyName)

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def modelsPackageName(ctx: RouteGroupView): String =
      s"${packageName(ctx)}.models"

    def modelNameSet(ctx: RouteGroupView): Set[String] =
      ctx.usedTypes.collect { case model: Model[?] =>
        ctx.conventions.className(model.id)
      }.toSet

    def referencedModelNames(typeName: String, knownNames: Set[String]): List[String] =
      if (typeName.startsWith("List[")) {
        referencedModelNames(typeName.substring(5, typeName.length - 1), knownNames)
      } else if (typeName.startsWith("Map[String, ")) {
        referencedModelNames(typeName.substring(12, typeName.length - 1), knownNames)
      } else if (knownNames.contains(typeName) || HttpSmithyTypeResolver.isStructureTypeName(typeName)) {
        List(typeName)
      } else {
        Nil
      }

    def isHeaderBinding(member: HttpOperationInputMemberMeta): Boolean =
      member.binding.isInstanceOf[HttpInputMemberBindingMeta.Header]

    def isPathLabelBinding(member: HttpOperationInputMemberMeta): Boolean =
      member.binding == HttpInputMemberBindingMeta.PathLabel

    def isQueryBinding(member: HttpOperationInputMemberMeta): Boolean =
      member.binding.isInstanceOf[HttpInputMemberBindingMeta.Query]

    def operationHasBody(feature: HttpOperationMeta): Boolean =
      feature.bodyBinding match {
        case HttpOperationBodyBindingMeta.None => false
        case _                                 => true
      }

    def bodyBindingMembers(feature: HttpOperationMeta): List[HttpOperationInputMemberMeta] =
      feature.bodyBinding match {
        case HttpOperationBodyBindingMeta.Members(members) => members
        case _                                             => Nil
      }

    def operationBodyModelNames(feature: HttpOperationMeta): List[String] =
      feature.bodyBinding match {
        case HttpOperationBodyBindingMeta.Document(inputShapeName) => List(inputShapeName)
        case HttpOperationBodyBindingMeta.Members(members)         =>
          members
            .filter(member => HttpSmithyTypeResolver.isStructureTypeName(member.typeName))
            .map(_.typeName)
            .distinct
        case HttpOperationBodyBindingMeta.None                     => Nil
      }

    def operationCallArgumentList(
        ctx: RouteGroupView,
        operation: OperationModel[HttpOperationMeta]
    ): List[String] = {
      val feature   = operation.meta.feature
      val routeArgs =
        feature.inputMembers
          .filter(member => HttpNeutralRouteGroupTemplateAttributes.isRouteParameter(member))
          .map(member =>
            s"${HttpNeutralRouteGroupTemplateAttributes.routeParameterName(ctx, member.name)}=${HttpNeutralRouteGroupTemplateAttributes.routeParameterName(ctx, member.name)}")
      val bodyArgs  = feature.bodyBinding match {
        case HttpOperationBodyBindingMeta.Document(inputShapeName) =>
          val paramName = HttpNeutralRouteGroupTemplateAttributes.routeParameterName(ctx, inputShapeName)
          List(s"$paramName=$paramName")
        case HttpOperationBodyBindingMeta.Members(members)         =>
          members.map(member =>
            s"${HttpNeutralRouteGroupTemplateAttributes.routeParameterName(ctx, member.name)}=${HttpNeutralRouteGroupTemplateAttributes.routeParameterName(ctx, member.name)}")
        case HttpOperationBodyBindingMeta.None                     =>
          Nil
      }
      routeArgs ++ bodyArgs
    }

    def renderMemberType(ctx: RouteGroupView, member: HttpOperationInputMemberMeta): String =
      if (member.typeName == "Unit") {
        "None"
      } else {
        val tpe = toNeutralType(member.typeName, member.timestampFormat)
        ctx.typeRenderer.render(tpe, RenderContext(typeResolver(ctx), ctx.conventions))
      }

    def typeResolver(ctx: RouteGroupView): TypeResolver[HttpMeta] =
      TypeResolver.fromModelSet(ModelSet(ctx.usedTypes))

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

    def typeContainsDatetime(tpe: NeutralType): Boolean =
      tpe match {
        case TimestampT(TimestampFormat.DateTime) => true
        case OptionalT(inner)                     => typeContainsDatetime(inner)
        case ListT(element)                       => typeContainsDatetime(element)
        case MapT(key, value)                     => typeContainsDatetime(key) || typeContainsDatetime(value)
        case _                                    => false
      }
  }
}
