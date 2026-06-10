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
      .filter(_.restJson1Protocol)
      .traverse(extractService(model, _))

  private def extractService(model: Model, service: ServiceShape): HttpValidated[HttpService] = {
    val serviceShape = service.getId
    requireServiceVersion(service).andThen { version =>
      extractResources(model, service).andThen { resources =>
        val operationIds =
          (service.getOperations.asScala.toList ++ HttpResourceExtractor.collectOperationIds(resources)).distinct
        operationIds
          .traverse(operationId => HttpOperationExtractor.extract(model, serviceShape, operationId))
          .andThen { operations =>
            if (operations.isEmpty) {
              EmptyHttpService(serviceShape).invalidNel
            } else {
              HttpService(
                shapeId = serviceShape,
                version = version,
                title = service.titleText,
                documentation = service.documentationText,
                serviceErrors = service.getErrorsSet.asScala.toList,
                resources = resources,
                routeGroups = HttpRouteGroupBuilder.build(operations)
              ).validNel
            }
          }
      }
    }
  }

  private def requireServiceVersion(service: ServiceShape): HttpValidated[String] =
    Option(service.getVersion).filter(_.nonEmpty) match {
      case Some(version) => version.validNel
      case None          => MissingHttpServiceVersion(service.getId).invalidNel
    }

  private def extractResources(model: Model, service: ServiceShape): HttpValidated[List[HttpResource]] =
    service.getResources.asScala.toList
      .traverse(resourceId => HttpResourceExtractor.extract(model, resourceId))
}
