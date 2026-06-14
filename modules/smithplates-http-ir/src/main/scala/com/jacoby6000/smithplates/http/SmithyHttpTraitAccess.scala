package com.jacoby6000.smithplates.http

import com.jacoby6000.smithplates.http.traits.HttpServiceTrait
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ResourceShape
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.traits.DocumentationTrait
import software.amazon.smithy.model.traits.HttpTrait
import software.amazon.smithy.model.traits.ReadonlyTrait
import software.amazon.smithy.model.traits.RequiredTrait
import software.amazon.smithy.model.traits.TagsTrait
import software.amazon.smithy.model.traits.TitleTrait

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

object SmithyHttpTraitAccess {
  extension (shape: Shape) {
    def httpBinding: Option[HttpTrait] =
      Option(shape.getTrait(classOf[HttpTrait]).orElse(null))

    def tags: List[String] =
      Option(shape.getTrait(classOf[TagsTrait]).orElse(null))
        .map(_.getValues.asScala.toList)
        .getOrElse(Nil)

    def documentationText: Option[String] =
      Option(shape.getTrait(classOf[DocumentationTrait]).orElse(null)).map(_.getValue)

    def titleText: Option[String] =
      Option(shape.getTrait(classOf[TitleTrait]).orElse(null)).map(_.getValue)
  }

  extension (operation: OperationShape) {
    def readonlyOperation: Boolean =
      Option(operation.getTrait(classOf[ReadonlyTrait]).orElse(null)).isDefined
  }

  extension (member: MemberShape) {
    def requiredMember: Boolean =
      Option(member.getTrait(classOf[RequiredTrait]).orElse(null)).isDefined
  }

  extension (service: ServiceShape) {
    def httpService: Option[HttpServiceTrait] =
      Option(service.getTrait(classOf[HttpServiceTrait]).orElse(null))
  }

  extension (resource: ResourceShape) {
    def createOperationId: Option[software.amazon.smithy.model.shapes.ShapeId] =
      resource.getCreate.toScala

    def readOperationId: Option[software.amazon.smithy.model.shapes.ShapeId] =
      resource.getRead.toScala

    def listOperationId: Option[software.amazon.smithy.model.shapes.ShapeId] =
      resource.getList.toScala

    def updateOperationId: Option[software.amazon.smithy.model.shapes.ShapeId] =
      resource.getUpdate.toScala

    def deleteOperationId: Option[software.amazon.smithy.model.shapes.ShapeId] =
      resource.getDelete.toScala
  }
}
