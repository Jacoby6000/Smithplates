package com.jacoby6000.smithplates.http

import cats.syntax.all.*
import com.jacoby6000.smithplates.http.model.*
import com.jacoby6000.smithplates.http.traits.HttpCookieAuthTrait
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.AuthDefinitionTrait
import software.amazon.smithy.model.traits.AuthTrait
import software.amazon.smithy.model.traits.HttpApiKeyAuthTrait
import software.amazon.smithy.model.traits.HttpBearerAuthTrait
import software.amazon.smithy.model.traits.OptionalAuthTrait

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

private[http] object HttpAuthExtractor {
  final case class ServiceAuth(
      schemes: List[HttpAuthScheme],
      effectiveAlternatives: List[HttpAuthAlternative]
  ) {
    val schemeIds: Set[ShapeId] = schemes.map(_.id).toSet
  }

  def extractService(model: Model, service: ServiceShape): HttpValidated[ServiceAuth] = {
    val configuredTraits = service.getAllTraits.asScala.toList
      .filter { case (id, _) => internal.isAuthDefinition(model, id) }
      .sortBy(_._1.toString)
    val configuredIds    = configuredTraits.map(_._1).toSet

    internal.authValues(service) match {
      case Some(values) =>
        values
          .traverse(id => internal.validateServiceReference(model, service, configuredIds, id))
          .andThen { _ =>
            val orderedIds = values ++ configuredTraits.map(_._1).filterNot(values.contains)
            orderedIds.traverse(id => internal.extractScheme(service.getId, id, service))
          }
          .map { schemes =>
            ServiceAuth(schemes, internal.alternativesOrNoAuth(values))
          }
      case None         =>
        configuredTraits
          .traverse { case (id, _) => internal.extractScheme(service.getId, id, service) }
          .map { schemes =>
            ServiceAuth(schemes, internal.alternativesOrNoAuth(schemes.map(_.id)))
          }
    }
  }

  def extractOperation(
      model: Model,
      serviceShape: ShapeId,
      operation: OperationShape,
      serviceAuth: ServiceAuth
  ): HttpValidated[List[HttpAuthAlternative]] = {
    val operationValues = internal.authValues(operation)
    val effectiveIds    = operationValues.getOrElse(serviceAuth.effectiveAlternatives.map(_.schemeId))

    operationValues
      .getOrElse(Nil)
      .traverse(id => internal.validateOperationReference(model, serviceShape, operation, serviceAuth.schemeIds, id))
      .map { _ =>
        val alternatives = internal.alternativesOrNoAuth(effectiveIds)
        if (operation.hasTrait(classOf[OptionalAuthTrait]) && !alternatives.contains(HttpAuthAlternative.NoAuth)) {
          alternatives :+ HttpAuthAlternative.NoAuth
        } else {
          alternatives
        }
      }
  }

  def validateOperationBindings(
      serviceShape: ShapeId,
      operation: OperationShape,
      serviceAuth: ServiceAuth,
      alternatives: List[HttpAuthAlternative],
      inputMembers: List[HttpOperationInputMember]
  ): HttpValidated[Unit] = {
    val activeSchemeIds = alternatives.map(_.schemeId).filterNot(_ == HttpAuthAlternative.NoAuth.schemeId).toSet
    val wireBindings    = serviceAuth.schemes
      .filter(scheme => activeSchemeIds.contains(scheme.id))
      .map(internal.wireBinding)
    val duplicateAuth   = wireBindings
      .groupBy(binding => (binding._1, internal.normalizedWireName(binding._1, binding._2)))
      .collectFirst {
        case ((location, name), bindings) if bindings.size > 1 && !internal.haveDistinctPrefixes(bindings.map(_._3)) =>
          s"authentication schemes collide on $location '$name'"
      }
    val memberCollision = wireBindings.collectFirst {
      case ("header", name, _) if inputMembers.exists(internal.headerBindingMatches(_, name)) =>
        s"authentication conflicts with input member bound to header '$name'"
      case ("query", name, _) if inputMembers.exists(internal.queryBindingMatches(_, name))   =>
        s"authentication conflicts with input member bound to query parameter '$name'"
    }

    duplicateAuth.orElse(memberCollision) match {
      case Some(reason) => InvalidHttpOperation(serviceShape, operation.getId.getName, reason).invalidNel
      case None         => ().validNel
    }
  }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def authValues(shape: software.amazon.smithy.model.shapes.Shape): Option[List[ShapeId]] =
      shape.getTrait(classOf[AuthTrait]).toScala.map(_.getValueSet.asScala.toList)

    def isAuthDefinition(model: Model, id: ShapeId): Boolean =
      model.getShape(id).toScala.exists(_.hasTrait(classOf[AuthDefinitionTrait]))

    def alternativesOrNoAuth(ids: List[ShapeId]): List[HttpAuthAlternative] =
      if (ids.isEmpty) List(HttpAuthAlternative.NoAuth) else ids.map(HttpAuthAlternative.apply)

    def wireBinding(scheme: HttpAuthScheme): (String, String, Option[String]) =
      scheme match {
        case HttpAuthScheme.Bearer(_)                         => ("header", "Authorization", Some("Bearer"))
        case HttpAuthScheme.ApiKey(_, name, location, scheme) =>
          location match {
            case HttpApiKeyLocation.Header => ("header", name, scheme)
            case HttpApiKeyLocation.Query  => ("query", name, None)
          }
        case HttpAuthScheme.Cookie(_, _)                      => ("header", "Cookie", None)
      }

    def haveDistinctPrefixes(prefixes: List[Option[String]]): Boolean = {
      val normalized = prefixes.flatten.map(_.toLowerCase(java.util.Locale.ROOT))
      normalized.size == prefixes.size && normalized.distinct.size == normalized.size
    }

    def normalizedWireName(location: String, name: String): String =
      if (location == "header") name.toLowerCase(java.util.Locale.ROOT) else name

    def headerBindingMatches(member: HttpOperationInputMember, name: String): Boolean =
      member.binding match {
        case HttpInputMemberBinding.Header(headerName) => headerName.equalsIgnoreCase(name)
        case _                                         => false
      }

    def queryBindingMatches(member: HttpOperationInputMember, name: String): Boolean =
      member.binding match {
        case HttpInputMemberBinding.Query(queryName) => queryName == name
        case _                                       => false
      }

    def validateServiceReference(
        model: Model,
        service: ServiceShape,
        configuredIds: Set[ShapeId],
        id: ShapeId
    ): HttpValidated[ShapeId] =
      if (!isAuthDefinition(model, id)) {
        InvalidHttpService(
          service.getId,
          s"@auth references '$id', which is not a defined authentication scheme").invalidNel
      } else if (!configuredIds.contains(id)) {
        InvalidHttpService(
          service.getId,
          s"@auth references '$id', but that scheme is not configured on the service").invalidNel
      } else {
        id.validNel
      }

    def validateOperationReference(
        model: Model,
        serviceShape: ShapeId,
        operation: OperationShape,
        configuredIds: Set[ShapeId],
        id: ShapeId
    ): HttpValidated[ShapeId] =
      if (!isAuthDefinition(model, id)) {
        InvalidHttpOperation(
          serviceShape,
          operation.getId.getName,
          s"@auth references '$id', which is not a defined authentication scheme"
        ).invalidNel
      } else if (!configuredIds.contains(id)) {
        InvalidHttpOperation(
          serviceShape,
          operation.getId.getName,
          s"@auth references '$id', but that scheme is not configured on the service"
        ).invalidNel
      } else {
        id.validNel
      }

    def extractScheme(
        serviceShape: ShapeId,
        id: ShapeId,
        service: ServiceShape
    ): HttpValidated[HttpAuthScheme] =
      if (id == HttpBearerAuthTrait.ID) {
        HttpAuthScheme.Bearer(id).validNel
      } else if (id == HttpApiKeyAuthTrait.ID) {
        service.getTrait(classOf[HttpApiKeyAuthTrait]).toScala match {
          case Some(apiKey) if apiKey.getName.isEmpty                                                           =>
            InvalidHttpService(serviceShape, "@httpApiKeyAuth name must not be empty").invalidNel
          case Some(apiKey) if apiKey.getIn == HttpApiKeyAuthTrait.Location.QUERY && apiKey.getScheme.isPresent =>
            InvalidHttpService(
              serviceShape,
              "@httpApiKeyAuth scheme is only supported when in is 'header'"
            ).invalidNel
          case Some(apiKey)                                                                                     =>
            val location = apiKey.getIn match {
              case HttpApiKeyAuthTrait.Location.HEADER => HttpApiKeyLocation.Header
              case HttpApiKeyAuthTrait.Location.QUERY  => HttpApiKeyLocation.Query
            }
            HttpAuthScheme.ApiKey(id, apiKey.getName, location, apiKey.getScheme.toScala).validNel
          case None                                                                                             =>
            InvalidHttpService(serviceShape, s"authentication scheme '$id' has no service configuration").invalidNel
        }
      } else if (id == HttpCookieAuthTrait.ID) {
        service.getTrait(classOf[HttpCookieAuthTrait]).toScala match {
          case Some(cookie) if !cookie.getName.matches("^[!#$%&'*+.^_`|~A-Za-z0-9-]+$") =>
            InvalidHttpService(serviceShape, "@httpCookieAuth name must be a valid HTTP cookie name").invalidNel
          case Some(cookie)                                                             =>
            HttpAuthScheme.Cookie(id, cookie.getName).validNel
          case None                                                                     =>
            InvalidHttpService(serviceShape, s"authentication scheme '$id' has no service configuration").invalidNel
        }
      } else {
        InvalidHttpService(serviceShape, s"authentication scheme '$id' is not supported").invalidNel
      }
  }
}
