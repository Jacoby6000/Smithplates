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
import com.jacoby6000.smithplates.http.codegen.HttpResponseVariantMeta
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

  def serviceModuleName(ctx: RouteGroupView): String =
    ctx.conventions.memberName(ctx.subject.service.id.name)

  def servicePackageName(ctx: RouteGroupView): String =
    s"${packageName(ctx)}${ctx.conventions.packageSeparator}${serviceModuleName(ctx)}"

  def namespaceModuleDir(ctx: RouteGroupView): String = {
    val namespace = ctx.subject.service.id.namespace
    val dir       = namespace.split('.').filter(_.nonEmpty).mkString("/")
    s"${ctx.conventions.rootNamespaceDir}/$dir"
  }

  def serviceModuleDir(ctx: RouteGroupView): String =
    s"${namespaceModuleDir(ctx)}/${serviceModuleName(ctx)}"

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

  def clientOperationImportedModelNames(
      ctx: RouteGroupView,
      operation: OperationModel[HttpOperationMeta]
  ): List[String] = {
    val operationModelNames = operationImportedModelNames(ctx, operation)
    val clientVariantTypes  = clientResponseVariants(ctx, operation).map(_.variantTypeName).filterNot(_ == "__empty__")
    (operationModelNames ++ clientVariantTypes).distinct.sorted
  }

  def clientResponseVariants(
      ctx: RouteGroupView,
      operation: OperationModel[HttpOperationMeta]
  ): List[HttpResponseVariantMeta] =
    HttpNeutralServiceTemplateAttributes.internal.mergeResponseVariants(
      operation.meta.feature.responseVariants,
      ctx.subject.service.meta.feature.serviceErrors
    )

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
      case nested: HttpOperationBodyBindingMeta.NestedDocument   => Some(nested.inputShapeName)
      case _                                                     => None
    }

  /** The FastAPI body parameter (name, type) for ``Document`` and ``NestedDocument`` body bindings. For ``Document``
    * the parameter is the input shape (e.g. ``create_project_input: CreateProjectInput``). For ``NestedDocument`` the
    * parameter is the payload target (e.g. ``body: ProjectCreateRequest``) because ``@nestedProperties`` flattens the
    * target's fields to the document root.
    */
  def documentBodyParameter(
      ctx: RouteGroupView,
      operation: OperationModel[HttpOperationMeta]
  ): Option[(String, String)] =
    operation.meta.feature.bodyBinding match {
      case HttpOperationBodyBindingMeta.Document(inputShapeName) =>
        Some((routeParameterName(ctx, inputShapeName), inputShapeName))
      case nested: HttpOperationBodyBindingMeta.NestedDocument   =>
        Some((routeParameterName(ctx, nested.payloadMemberName), nested.payloadTargetShapeName))
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
      case nested: HttpOperationBodyBindingMeta.NestedDocument   =>
        List(s"${routeParameterName(ctx, nested.payloadMemberName)}: ${nested.payloadTargetShapeName}")
      case HttpOperationBodyBindingMeta.None                     =>
        Nil
    }
    routeParams ++ bodyParams
  }

  def clientMethodReturnType(ctx: RouteGroupView, operation: OperationModel[HttpOperationMeta]): String = {
    val variants        = clientResponseVariants(ctx, operation)
    val hasEmptySuccess = variants.exists(variant =>
      variant.statusCode == operation.meta.feature.successStatus && variant.variantTypeName == "__empty__")
    val types           = variants.map(_.variantTypeName).filterNot(_ == "__empty__").distinct
    val allTypes        = if (hasEmptySuccess) types :+ "None" else types
    if (allTypes.isEmpty) "None" else allTypes.mkString(" | ")
  }

  def clientRequestUrlExpression(
      ctx: RouteGroupView,
      operation: OperationModel[HttpOperationMeta]
  ): String = {
    val pathMembers     =
      operation.meta.feature.inputMembers.filter(member => internal.isPathLabelBinding(member))
    val interpolatedUri =
      pathMembers.foldLeft(operation.meta.feature.uriPattern) { case (uri, member) =>
        val parameter = routeParameterName(ctx, member.name)
        uri
          .replace("{" + member.name + "+}", "{_encoded_" + parameter + "}")
          .replace("{" + member.name + "}", "{_encoded_" + parameter + "}")
      }
    s"f\"{self._base_url}$interpolatedUri\""
  }

  def clientRequestPathLabelsBlock(
      ctx: RouteGroupView,
      operation: OperationModel[HttpOperationMeta]
  ): String =
    operation.meta.feature.inputMembers
      .filter(member => internal.isPathLabelBinding(member))
      .map { member =>
        val parameter = routeParameterName(ctx, member.name)
        val greedy    = operation.meta.feature.uriPattern.contains("{" + member.name + "+}")
        val argument  = if (greedy) s"$parameter, greedy=True" else parameter
        s"        _encoded_$parameter = _encode_path_label($argument)"
      }
      .mkString("\n")

  def clientRequestQueryParamsBlock(
      ctx: RouteGroupView,
      operation: OperationModel[HttpOperationMeta]
  ): String = {
    val queryMembers = operation.meta.feature.inputMembers.filter(member => internal.isQueryBinding(member))
    if (queryMembers.isEmpty) {
      return s"        request_url = ${clientRequestUrlExpression(ctx, operation)}"
    }
    val lines        = queryMembers.flatMap { member =>
      val wireName                          = member.binding match {
        case HttpInputMemberBindingMeta.Query(name) => name
        case _                                      => member.name
      }
      val parameter                         = routeParameterName(ctx, member.name)
      def serialized(value: String): String = member.timestampFormat match {
        case Some(HttpTimestampFormat.EpochSeconds) =>
          s"_serialize_query_value($value, timestamp_format=\"epoch-seconds\")"
        case Some(HttpTimestampFormat.HttpDate)     => s"_serialize_query_value($value, timestamp_format=\"http-date\")"
        case _                                      => s"_serialize_query_value($value)"
      }
      val append                            =
        if (member.typeName.startsWith("List[")) {
          List(
            s"        for value in $parameter:",
            s"            query_params.append((\"$wireName\", ${serialized("value")}))"
          )
        } else {
          List(
            "        query_params.append(",
            s"            (\"$wireName\", ${serialized(parameter)})",
            "        )"
          )
        }
      if (member.required) append else s"        if $parameter is not None:" +: append.map("    " + _)
    }
    (
      List("        query_params: list[tuple[str, str]] = []") ++
        lines ++
        List(
          "        query_string = urlencode(query_params, quote_via=quote)",
          s"        request_url = ${clientRequestUrlExpression(ctx, operation)}",
          "        if query_string:",
          "            request_url = f\"{request_url}?{query_string}\""
        )
    ).mkString("\n")
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
      case nested: HttpOperationBodyBindingMeta.NestedDocument   =>
        val paramName = routeParameterName(ctx, nested.payloadMemberName)
        s", json=$paramName.model_dump(mode=\"json\", exclude_none=True)"
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
        case nested: HttpOperationBodyBindingMeta.NestedDocument   =>
          List(nested.inputShapeName, nested.payloadTargetShapeName).distinct
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
        case nested: HttpOperationBodyBindingMeta.NestedDocument   =>
          val inputParamName  = HttpNeutralRouteGroupTemplateAttributes.routeParameterName(ctx, nested.inputShapeName)
          val memberParamName =
            HttpNeutralRouteGroupTemplateAttributes.routeParameterName(ctx, nested.payloadMemberName)
          List(s"$inputParamName=${nested.inputShapeName}($memberParamName=$memberParamName)")
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
