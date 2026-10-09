package com.jacoby6000.smithplates.http.service.renderer

import cats.syntax.all.*
import com.jacoby6000.smithplates.codegen.core.ModelSet
import com.jacoby6000.smithplates.codegen.core.NeutralType
import com.jacoby6000.smithplates.codegen.core.NeutralType.*
import com.jacoby6000.smithplates.codegen.core.ServiceModel
import com.jacoby6000.smithplates.codegen.core.strategy.Conventions
import com.jacoby6000.smithplates.http.HttpValidated
import com.jacoby6000.smithplates.http.codegen.*
import com.jacoby6000.smithplates.http.model.InvalidHttpService
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.neighbor.Walker
import software.amazon.smithy.model.shapes.ShapeId

import scala.jdk.CollectionConverters.*

/** Fail-closed capability gate for the bundled, deliberately bounded Rust JSON client. */
object RustHttpCapabilities {
  def prevalidate(original: Model, settings: HttpServiceCodegenSettings): HttpValidated[Unit] =
    if (settings.templateDirectory.stripPrefix("classpath:") != "rust/src/http/client") {
      ().validNel
    } else {
      val selected  = original.getServiceShapes.asScala.toList.filter { service =>
        service.hasTrait("smithplates.codegen.http#httpService") && settings.serviceFilter.forall(filter =>
          filter.contains(service.getId.toString) || filter.contains(service.getId.getName))
      }
      val reachable =
        selected.flatMap(service => new Walker(original).walkShapes(service).asScala.toList).distinctBy(_.getId)
      val errors    = reachable
        .filter(shape => internal.recursive(original, shape.getId, Set.empty))
        .map(shape =>
          InvalidHttpService(
            shape.getId,
            "bundled Rust client: recursive models are unsupported; remove the recursive edge or use another target"))
      errors match {
        case Nil          => ().validNel
        case head :: tail => cats.data.Validated.Invalid(cats.data.NonEmptyList(head, tail))
      }
    }

  def validate(
      original: Model,
      models: ModelSet[HttpMeta],
      resolutionModels: ModelSet[HttpMeta],
      services: List[ServiceModel[HttpServiceMeta, HttpOperationMeta]],
      settings: HttpServiceCodegenSettings,
      conventions: Conventions
  ): HttpValidated[Unit] =
    if (settings.templateDirectory.stripPrefix("classpath:") != "rust/src/http/client") {
      ().validNel
    } else {
      val errors = internal.errors(original, models, resolutionModels, services, conventions)
      errors match {
        case Nil          => ().validNel
        case head :: tail => cats.data.Validated.Invalid(cats.data.NonEmptyList(head, tail))
      }
    }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def recursive(model: Model, id: ShapeId, ancestors: Set[ShapeId]): Boolean =
      ancestors.contains(id) || model
        .expectShape(id)
        .getAllMembers
        .values
        .asScala
        .exists(member => recursive(model, member.getTarget, ancestors + id))

    val unsupportedTraits = Set(
      "httpQueryParams",
      "httpPrefixHeaders",
      "httpResponseCode",
      "streaming",
      "sparse",
      "default",
      "timestampFormat",
      "mediaType",
      "length",
      "range",
      "pattern",
      "uniqueItems",
      "httpChecksumRequired",
      "enum"
    )

    def errors(
        original: Model,
        models: ModelSet[HttpMeta],
        resolutionModels: ModelSet[HttpMeta],
        services: List[ServiceModel[HttpServiceMeta, HttpOperationMeta]],
        conventions: Conventions
    ): List[InvalidHttpService] = {
      def error(message: String): InvalidHttpService =
        InvalidHttpService(ShapeId.from("smithplates.codegen.http#RustClient"), s"bundled Rust client: $message")

      val countErrors = Option
        .when(services.size != 1)(
          error(
            "select exactly one service per HTTP output entry; use separate output directories for multiple services"
          ))
        .toList ++ Option.when(models.all.isEmpty)(error("at least one input/output model is required")).toList
      val names       = models.all.map(model => conventions.className(model.id))
      val reserved    = Set("HttpProblem", "String", "Vec", "Option", "Result", "Self_")
      val nameErrors  = names
        .groupBy(identity)
        .collect {
          case (name, duplicates) if duplicates.size > 1 =>
            error(s"duplicate flat model name '$name'; rename the shapes")
        }
        .toList ++ names.filter(reserved.contains).map(name => error(s"reserved model name '$name'; rename the shape"))

      val shapeErrors     = services.flatMap { service =>
        new Walker(original)
          .walkShapes(original.expectShape(ShapeId.from(s"${service.id.namespace}#${service.id.name}")))
          .asScala
          .toList
          .filterNot(_.getId.getNamespace == "smithy.api")
          .flatMap { shape =>
            val typeErrors    = Option
              .when(
                Set(
                  "blob",
                  "timestamp",
                  "bigDecimal",
                  "bigInteger",
                  "float",
                  "double",
                  "intEnum",
                  "byte",
                  "short",
                  "set").contains(shape.getType.toString))(
                error(s"${shape.getId}: unsupported ${shape.getType}; use supported JSON types")
              )
              .toList
            val traitErrors   = shape.getAllTraits.keySet.asScala.toList
              .filter(id => id.getNamespace == "smithy.api" && unsupportedTraits.contains(id.getName))
              .map(id => error(s"${shape.getId}: unsupported @$id; remove it or use another target"))
            val payloadErrors = Option
              .when(shape.hasTrait("smithy.api#httpPayload") && !shape.hasTrait("smithy.api#nestedProperties"))(
                error(s"${shape.getId}: raw @httpPayload is unsupported; use @nestedProperties JSON structures")
              )
              .toList
            typeErrors ++ traitErrors ++ payloadErrors
          }
      }
      val modelErrors     = models.all.flatMap { model =>
        val members           = model.asStructure.toList.flatMap(_.fields.map(field => field.name -> field.tpe)) ++
          model.asUnion.toList.flatMap(_.members.map(member => member.name -> member.tpe))
        val collisions        = members
          .map { case (name, _) => conventions.memberName(name) }
          .groupBy(identity)
          .collect {
            case (name, duplicates) if duplicates.size > 1 => error(s"${model.id}: duplicate Rust member '$name'")
          }
          .toList
        val types             = members.map(_._2) ++ model.asAlias.map(_.underlying).toList ++ model.asEnum.map(_.base).toList
        val variantNames      =
          model.asUnion.toList.flatMap(_.members.map(_.name)) ++ model.asEnum.toList.flatMap(_.values.map(_.name))
        val variantCollisions = variantNames
          .map(name => conventions.className(com.jacoby6000.smithplates.codegen.core.ModelId("", name)))
          .groupBy(identity)
          .collect {
            case (name, duplicates) if duplicates.size > 1 || name == "Self_" =>
              error(s"${model.id}: colliding or reserved Rust variant '$name'")
          }
          .toList
        val problemFields     = model.meta.feature match {
          case HttpMeta.HttpResponseMeta(_, _, _, Some(_)) =>
            members
              .filter { case (name, _) =>
                Set("type", "title", "status", "detail", "instance", "http_problem")
                  .contains(name) || conventions.memberName(name) == "http_problem"
              }
              .map { case (name, _) =>
                error(
                  s"${model.id}: @httpProblem field '$name' conflicts with the shared problem model; use its generated http_problem field")
              }
          case _                                           => Nil
        }
        collisions ++ variantCollisions ++ problemFields ++ types
          .filterNot(supportedType(_, resolutionModels, Set(model.id)))
          .map(tpe => error(s"${model.id}: unsupported or recursive type $tpe"))
      }
      val operationErrors = services.flatMap { service =>
        val methodNames      = service.operations.map(op => conventions.functionName(op.id.name))
        val duplicateMethods = methodNames
          .groupBy(identity)
          .collect {
            case (name, duplicates) if duplicates.size > 1 => error(s"duplicate operation method '$name'")
          }
          .toList
        duplicateMethods ++ methodNames
          .filter(Set("new", "execute").contains)
          .map(name => error(s"reserved operation name '$name'")) ++
          service.operations.flatMap { operation =>
            val meta                  = operation.meta.feature
            val variants              = HttpNeutralServiceTemplateAttributes.internal.mergeResponseVariants(
              meta.responseVariants,
              service.meta.feature.serviceErrors)
            val ambiguous             = variants.groupBy(_.statusCode).exists { case (_, values) => values.size > 1 }
            val outputPayload         = meta.responseVariants
              .find(_.statusCode == meta.successStatus)
              .exists(variant => variant.modelShapeId != operation.output.map(_.id))
            val unsupported           = meta.websocket.isDefined || meta.uriPattern.contains("+}") || meta.uriPattern.contains(
              "?") || meta.uriPattern.contains("%") ||
              variants.exists(variant =>
                variant.mediaType.exists(value => value != "application/json" && value != "application/problem+json"))
            val headerErrors          = variants.flatMap { variant =>
              variant.modelShapeId
                .flatMap(resolutionModels.resolve)
                .flatMap(_.asStructure)
                .toList
                .flatMap(_.fields)
                .filter(field => variant.headerBindings.exists(_._1 == field.name))
                .filter(field => headerKind(field.tpe, resolutionModels).isEmpty)
                .map(field =>
                  error(s"${operation.id}.${field.name}: response headers must be string, boolean, integer or long"))
            }
            val reservedErrorVariants = variants
              .flatMap(_.modelShapeId)
              .filter(id => conventions.className(id) == "Client")
              .map(id => error(s"${operation.id}: error model '$id' conflicts with the reserved Client error variant"))
            headerErrors ++ reservedErrorVariants ++ Option
              .when(outputPayload)(
                error(s"${operation.id}: response payload wrappers are unsupported; use a JSON output structure"))
              .toList ++
              Option
                .when(unsupported)(error(
                  s"${operation.id}: WebSockets, greedy labels, URI queries/percent escapes and non-JSON media types are unsupported"))
                .toList ++
              Option
                .when(ambiguous)(error(s"${operation.id}: response statuses must uniquely identify a response model"))
                .toList ++
              meta.inputMembers
                .filter(member =>
                  member.binding != HttpInputMemberBindingMeta.Payload && !scalarBindingType(
                    member.typeName,
                    member.binding))
                .map(member =>
                  error(
                    s"${operation.id}.${member.name}: only scalar labels/headers and scalar or repeated scalar queries are supported"))
          }
      }
      countErrors ++ nameErrors ++ shapeErrors ++ modelErrors ++ operationErrors
    }

    def scalarBindingType(name: String, binding: HttpInputMemberBindingMeta): Boolean =
      Set("String", "Integer", "Long", "Boolean")
        .contains(name) || (binding.isInstanceOf[HttpInputMemberBindingMeta.Query] &&
        name.startsWith("List[") && scalarBindingType(
          name.substring(5, name.length - 1),
          HttpInputMemberBindingMeta.PathLabel))

    def headerKind(tpe: NeutralType, models: ModelSet[HttpMeta]): String = tpe match {
      case OptionalT(inner) => headerKind(inner, models)
      case StringT          => "string"
      case BooleanT         => "boolean"
      case IntegerT         => "integer"
      case LongT            => "long"
      case ModelRef(id)     =>
        models.resolve(id).flatMap(_.asAlias).map(alias => headerKind(alias.underlying, models)).getOrElse("")
      case _                => ""
    }

    def supportedType(
        tpe: NeutralType,
        models: ModelSet[HttpMeta],
        seen: Set[com.jacoby6000.smithplates.codegen.core.ModelId]): Boolean =
      tpe match {
        case StringT | BooleanT | IntegerT | LongT | DocumentT => true
        case OptionalT(inner)                                  => supportedType(inner, models, seen)
        case ListT(inner)                                      => supportedType(inner, models, seen)
        case MapT(StringT, value)                              => supportedType(value, models, seen)
        case ModelRef(id) if !seen.contains(id)                =>
          models.resolve(id).exists { model =>
            val types = model.asStructure.toList.flatMap(_.fields.map(_.tpe)) ++ model.asUnion.toList.flatMap(
              _.members.map(_.tpe)) ++
              model.asAlias.map(_.underlying).toList ++ model.asEnum.map(_.base).toList
            types.forall(supportedType(_, models, seen + id))
          }
        case _                                                 => false
      }
  }
}
