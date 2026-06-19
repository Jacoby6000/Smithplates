package com.jacoby6000.smithplates.http

import cats.syntax.all.*
import com.jacoby6000.smithplates.http.SmithyHttpTraitAccess.*
import com.jacoby6000.smithplates.http.model.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ResourceShape
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.ShapeId

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

private[http] object HttpResourceExtractor {
  def extractAllForService(model: Model, service: ServiceShape): HttpValidated[List[HttpResource]] =
    service.getResources.asScala.toList
      .traverse(resourceId => internal.extractTree(model, resourceId))
      .map(_.flatten.distinctBy(_.shapeId))

  def extract(model: Model, resourceId: ShapeId): HttpValidated[HttpResource] =
    model.getShape(resourceId).toScala.flatMap(_.asResourceShape.toScala) match {
      case None           =>
        InvalidHttpService(
          resourceId,
          s"resource '${resourceId.toString}' is not defined in the model"
        ).invalidNel
      case Some(resource) =>
        internal.extractResource(resource)
    }

  def collectOperationIds(resources: List[HttpResource]): List[ShapeId] = {
    def walk(resource: HttpResource): List[ShapeId] =
      resource.operationIds ++ resource.childResourceIds.flatMap(childId =>
        resources.find(_.shapeId == childId).toList.flatMap(walk))

    resources.flatMap(walk).distinct
  }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def extractTree(model: Model, resourceId: ShapeId): HttpValidated[List[HttpResource]] =
      extract(model, resourceId).andThen { resource =>
        resource.childResourceIds
          .traverse(childId => extractTree(model, childId))
          .map(childResources => resource :: childResources.flatten)
      }

    def extractResource(resource: ResourceShape): HttpValidated[HttpResource] = {
      val identifiers =
        resource.getIdentifiers.asScala.toList.map { case (name, _) => name }
      val properties  =
        resource.getProperties.asScala.toList.map { case (name, _) => name }
      HttpResource(
        shapeId = resource.getId,
        name = resource.getId.getName,
        identifiers = identifiers,
        propertyNames = properties,
        createOperation = resource.createOperationId,
        readOperation = resource.readOperationId,
        listOperation = resource.listOperationId,
        updateOperation = resource.updateOperationId,
        deleteOperation = resource.deleteOperationId,
        operationIds = resource.allOperationIds,
        childResourceIds = resource.getResources.asScala.toList
      ).validNel
    }
  }
}
