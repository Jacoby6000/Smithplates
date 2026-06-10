package com.jacoby6000.smithplates.http

import cats.syntax.all.*
import com.jacoby6000.smithplates.http.SmithyHttpTraitAccess.*
import com.jacoby6000.smithplates.http.model.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ServiceShape

import scala.jdk.CollectionConverters.*

private[http] object HttpServiceExtractor {
  def extract(model: Model): HttpValidated[List[HttpService]] =
    model.getServiceShapes.asScala.toList
      .filter(_.httpService.isDefined)
      .traverse(extractService(model, _))

  private def extractService(model: Model, service: ServiceShape): HttpValidated[HttpService] = {
    val serviceShape = service.getId
    (
      requireServiceVersion(service),
      requireSupportedSerialization(service)
    ).mapN { (version, serialization) =>
      extractResources(model, service).andThen { resources =>
        val operationIds =
          (service.getOperations.asScala.toList ++ HttpResourceExtractor.collectOperationIds(resources)).distinct
        operationIds
          .traverse(operationId => HttpOperationExtractor.extract(model, serviceShape, operationId, resources))
          .andThen { operations =>
            if (operations.isEmpty) {
              EmptyHttpService(serviceShape).invalidNel
            } else {
              HttpService(
                shapeId = serviceShape,
                version = version,
                serialization = serialization,
                title = service.titleText,
                documentation = service.documentationText,
                serviceErrors = service.getErrorsSet.asScala.toList,
                resources = resources,
                routeGroups = HttpRouteGroupBuilder.build(operations)
              ).validNel
            }
          }
      }
    }.andThen(identity)
  }

  private def requireServiceVersion(service: ServiceShape): HttpValidated[String] =
    Option(service.getVersion).filter(_.nonEmpty) match {
      case Some(version) => version.validNel
      case None          => MissingHttpServiceVersion(service.getId).invalidNel
    }

  private def requireSupportedSerialization(service: ServiceShape): HttpValidated[HttpSerialization] =
    service.httpService match {
      case None             =>
        InvalidHttpService(service.getId, "service is missing @httpService").invalidNel
      case Some(traitValue) =>
        HttpSerialization.fromTraitValue(traitValue.getSerialization) match {
          case Right(serialization) => serialization.validNel
          case Left(reason)         => InvalidHttpService(service.getId, reason).invalidNel
        }
    }

  private def extractResources(model: Model, service: ServiceShape): HttpValidated[List[HttpResource]] =
    HttpResourceExtractor.extractAllForService(model, service)
}
