package com.jacoby6000.smithplates.http.service.renderer

import com.jacoby6000.smithplates.codegen.core.Model
import com.jacoby6000.smithplates.codegen.core.ModelId
import com.jacoby6000.smithplates.codegen.core.OperationModel
import com.jacoby6000.smithplates.codegen.core.planning.CodegenPlanner
import com.jacoby6000.smithplates.codegen.core.planning.TemplateView
import com.jacoby6000.smithplates.http.HttpModelTypeNames
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

  def packageName(ctx: RouteGroupView): String =
    ctx.conventions.packageName(ctx.subject.service.id.namespace)

  def operationMethodName(ctx: RouteGroupView, operationName: String): String =
    internal.snakeCaseName(operationName)

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
          ctx.conventions.fileName(ModelId("", typeName)).stripSuffix(".py")
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
        internal.httpMemberPythonType(member.typeName, member.timestampFormat) == "datetime"))

  def operationImportedModelNames(
      ctx: RouteGroupView,
      operation: OperationModel[HttpOperationMeta]
  ): List[String] = {
    val (structureNames, unionNames, enumNames) = internal.modelNameSets(ctx)
    val feature                                 = operation.meta.feature
    val variantTypes                            =
      feature.responseVariants.map(_.variantTypeName).filterNot(_ == "__empty__")
    val inputMemberModelTypes                   =
      (feature.inputMembers ++ internal.bodyBindingMembers(feature))
        .flatMap { member =>
          val referenced =
            HttpModelTypeNames.referencedModelTypeNames(
              member.typeName,
              structureNames,
              unionNames,
              enumNames
            )
          val direct     =
            if (HttpSmithyTypeResolver.isStructureTypeName(member.typeName)) {
              List(member.typeName)
            } else {
              Nil
            }
          referenced ++ direct
        }
    (internal.operationBodyModelNames(feature) ++ variantTypes ++ inputMemberModelTypes).distinct.sorted
  }

  def fastapiParameterBinding(
      ctx: RouteGroupView,
      member: HttpOperationInputMemberMeta
  ): String =
    member.binding match {
      case HttpInputMemberBindingMeta.PathLabel          =>
        val pathAlias =
          if (routeParameterName(member.name) == member.name) {
            ""
          } else {
            s""", alias="${member.name}""""
          }
        if (member.required) {
          s"Path(...$pathAlias)"
        } else {
          s"Path(None$pathAlias)"
        }
      case HttpInputMemberBindingMeta.Query(queryName)   =>
        if (member.required) {
          s"""Query(..., alias="$queryName")"""
        } else {
          s"""Query(None, alias="$queryName")"""
        }
      case HttpInputMemberBindingMeta.Header(headerName) =>
        if (member.required) {
          s"""Header(..., alias="$headerName")"""
        } else {
          s"""Header(None, alias="$headerName")"""
        }
      case HttpInputMemberBindingMeta.Payload            =>
        if (member.required) {
          "Body(...)"
        } else {
          "Body(None)"
        }
    }

  def httpMemberTypeAnnotation(
      member: HttpOperationInputMemberMeta,
      required: Boolean
  ): String =
    if (required) {
      internal.httpMemberPythonType(member.typeName, member.timestampFormat)
    } else {
      s"${internal.httpMemberPythonType(member.typeName, member.timestampFormat)} | None"
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
        s"${routeParameterName(member.name)}: ${httpMemberTypeAnnotation(member, member.required)}"
      }
    val bodyParams  = feature.bodyBinding match {
      case HttpOperationBodyBindingMeta.Document(inputShapeName) =>
        List(s"${routeParameterName(inputShapeName)}: $inputShapeName")
      case HttpOperationBodyBindingMeta.Members(members)         =>
        members.map { member =>
          s"${routeParameterName(member.name)}: ${httpMemberTypeAnnotation(member, member.required)}"
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
        uri.replace("{" + member.name + "}", "{" + routeParameterName(member.name) + "}")
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
          val paramName  = routeParameterName(member.name)
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

  def bodyParameterBinding(required: Boolean): String =
    if (required) {
      "Body(...)"
    } else {
      "Body(None)"
    }

  def clientRequestJsonArgument(
      ctx: RouteGroupView,
      operation: OperationModel[HttpOperationMeta]
  ): String =
    operation.meta.feature.bodyBinding match {
      case HttpOperationBodyBindingMeta.None                     =>
        ""
      case HttpOperationBodyBindingMeta.Document(inputShapeName) =>
        val paramName = routeParameterName(inputShapeName)
        s", json=$paramName.model_dump(mode=\"json\", exclude_none=True)"
      case HttpOperationBodyBindingMeta.Members(members)         =>
        if (members.size == 1) {
          val member = members.head
          if (HttpSmithyTypeResolver.isStructureTypeName(member.typeName)) {
            s", json=${routeParameterName(member.name)}.model_dump(mode=\"json\", exclude_none=True)"
          } else {
            s", json=${routeParameterName(member.name)}"
          }
        } else {
          val entries =
            members.map(member => s"\"${member.name}\": ${routeParameterName(member.name)}").mkString(", ")
          s", json={$entries}"
        }
    }

  def routeParameterName(name: String): String =
    internal.snakeCaseName(name)

  def memberName(ctx: RouteGroupView, smithyName: String): String =
    routeParameterName(smithyName)

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def snakeCaseName(name: String): String =
      name
        .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
        .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
        .toLowerCase

    def modelsPackageName(ctx: RouteGroupView): String =
      s"${packageName(ctx)}.models"

    def modelNameSets(ctx: RouteGroupView): (Set[String], Set[String], Set[String]) =
      (
        ctx.usedTypes.collect { case structure: Model.Structure[?] =>
          ctx.conventions.className(structure.id)
        }.toSet,
        ctx.usedTypes.collect { case union: Model.Union[?] =>
          ctx.conventions.className(union.id)
        }.toSet,
        ctx.usedTypes.collect { case enumModel: Model.EnumModel[?] =>
          ctx.conventions.className(enumModel.id)
        }.toSet
      )

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
            s"${HttpNeutralRouteGroupTemplateAttributes.routeParameterName(member.name)}=${HttpNeutralRouteGroupTemplateAttributes.routeParameterName(member.name)}")
      val bodyArgs  = feature.bodyBinding match {
        case HttpOperationBodyBindingMeta.Document(inputShapeName) =>
          val paramName = HttpNeutralRouteGroupTemplateAttributes.routeParameterName(inputShapeName)
          List(s"$paramName=$paramName")
        case HttpOperationBodyBindingMeta.Members(members)         =>
          members.map(member =>
            s"${HttpNeutralRouteGroupTemplateAttributes.routeParameterName(member.name)}=${HttpNeutralRouteGroupTemplateAttributes.routeParameterName(member.name)}")
        case HttpOperationBodyBindingMeta.None                     =>
          Nil
      }
      routeArgs ++ bodyArgs
    }

    def httpMemberPythonType(typeName: String, timestampFormat: Option[HttpTimestampFormat]): String =
      if (typeName == "Timestamp") {
        timestampFormat match {
          case Some(HttpTimestampFormat.EpochSeconds) => "float"
          case Some(HttpTimestampFormat.HttpDate)     => "str"
          case _                                      => "datetime"
        }
      } else if (typeName.startsWith("List[")) {
        val inner = typeName.substring(5, typeName.length - 1)
        s"list[${httpMemberPythonType(inner, None)}]"
      } else if (typeName.startsWith("Map[String, ")) {
        val inner = typeName.substring(12, typeName.length - 1)
        s"dict[str, ${httpMemberPythonType(inner, None)}]"
      } else {
        typeName match {
          case "String"                          => "str"
          case "Integer" | "Long" | "BigInteger" => "int"
          case "Float" | "Double"                => "float"
          case "BigDecimal"                      => "Decimal"
          case "Boolean"                         => "bool"
          case "Blob"                            => "bytes"
          case "Document"                        => "object"
          case "Unit"                            => "None"
          case other                             => other
        }
      }
  }
}
