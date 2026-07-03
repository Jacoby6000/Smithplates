package com.jacoby6000.smithplates.http

import cats.syntax.all.*
import com.jacoby6000.smithplates.http.SmithyHttpTraitAccess.*
import com.jacoby6000.smithplates.http.model.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ServiceShape

import scala.jdk.CollectionConverters.*

private[http] object HttpServiceExtractor {
  def extract(model: Model): HttpValidated[(List[HttpService], List[HttpSchemaWarning])] =
    model.getServiceShapes.asScala.toList
      .filter(_.httpService.isDefined)
      .traverse(service => internal.extractService(model, service))
      .map { services =>
        (services.map(_._1), services.flatMap(_._2))
      }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def extractService(model: Model, service: ServiceShape): HttpValidated[(HttpService, List[HttpSchemaWarning])] = {
      val serviceShape = service.getId
      (
        requireServiceVersion(service),
        requireSupportedSerialization(service)
      ).mapN { (version, serialization) =>
        HttpServiceErrorExtractor
          .extract(model, serviceShape, service.getErrorsSet.asScala.toList)
          .andThen { case (serviceErrors, serviceErrorWarnings) =>
            extractResources(model, service).andThen { resources =>
              val operationIds =
                (service.getOperations.asScala.toList ++ HttpResourceExtractor.collectOperationIds(resources)).distinct
              operationIds
                .traverse(operationId =>
                  HttpOperationExtractor.extract(model, serviceShape, operationId, resources, serialization))
                .andThen { operationResults =>
                  val operations = operationResults.map(_._1)
                  val warnings   = serviceErrorWarnings ++ operationResults.flatMap(_._2)
                  if (operations.isEmpty) {
                    EmptyHttpService(serviceShape).invalidNel
                  } else {
                    HttpStructureExtractor
                      .extractForService(model, serviceShape, operations, serviceErrors)
                      .map { extractedShapes =>
                        val (stringEnums, intEnums) =
                          HttpEnumExtractor.extractReferenced(
                            model,
                            serviceShape,
                            extractedShapes.structures,
                            extractedShapes.unions,
                            operations
                          )
                        (
                          HttpService(
                            shapeId = serviceShape,
                            version = version,
                            serialization = serialization,
                            title = service.titleText,
                            documentation = service.documentationText,
                            serviceErrors = serviceErrors,
                            resources = resources,
                            routeGroups = HttpRouteGroupBuilder.build(operations),
                            structures = extractedShapes.structures,
                            unions = extractedShapes.unions,
                            stringEnums = stringEnums,
                            intEnums = intEnums
                          ),
                          warnings
                        )
                      }
                  }
                }
            }
          }
      }.andThen(identity)
    }

    def requireServiceVersion(service: ServiceShape): HttpValidated[String] =
      Option(service.getVersion).filter(_.nonEmpty) match {
        case Some(version) => version.validNel
        case None          => MissingHttpServiceVersion(service.getId).invalidNel
      }

    def requireSupportedSerialization(service: ServiceShape): HttpValidated[HttpSerialization] =
      service.httpService match {
        case None             =>
          InvalidHttpService(service.getId, "service is missing @httpService").invalidNel
        case Some(traitValue) =>
          HttpSerialization.fromTraitValue(traitValue.getSerialization) match {
            case Right(serialization) => serialization.validNel
            case Left(reason)         => InvalidHttpService(service.getId, reason).invalidNel
          }
      }

    def extractResources(model: Model, service: ServiceShape): HttpValidated[List[HttpResource]] =
      HttpResourceExtractor.extractAllForService(model, service)
  }
}
