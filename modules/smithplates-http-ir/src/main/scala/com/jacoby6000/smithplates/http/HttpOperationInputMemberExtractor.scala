package com.jacoby6000.smithplates.http

import cats.syntax.all.*
import com.jacoby6000.smithplates.http.SmithyHttpTraitAccess.*
import com.jacoby6000.smithplates.http.model.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.traits.HttpHeaderTrait
import software.amazon.smithy.model.traits.HttpLabelTrait
import software.amazon.smithy.model.traits.HttpPayloadTrait
import software.amazon.smithy.model.traits.HttpQueryTrait
import software.amazon.smithy.model.traits.ReferencesTrait
import software.amazon.smithy.model.traits.ResourceIdentifierTrait

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

private[http] object HttpOperationInputMemberExtractor {
  def inputBoundResource(
      model: Model,
      inputShapeId: ShapeId,
      operationId: ShapeId,
      serviceResources: List[HttpResource]
  ): Option[ShapeId] =
    structureBoundResource(model, inputShapeId)
      .orElse(operationBoundResource(operationId, serviceResources))

  def operationBoundResource(operationId: ShapeId, serviceResources: List[HttpResource]): Option[ShapeId] =
    serviceResources
      .find(resource => resource.operationIds.contains(operationId))
      .map(_.shapeId)

  private def structureBoundResource(model: Model, inputShapeId: ShapeId): Option[ShapeId] =
    if (inputShapeId == ShapeId.from("smithy.api#Unit")) {
      None
    } else {
      model.getShape(inputShapeId).toScala.flatMap(_.asStructureShape.toScala).flatMap(resolveBoundResource)
    }

  def extract(
      model: Model,
      serviceShape: ShapeId,
      operation: software.amazon.smithy.model.shapes.OperationShape,
      inputShapeId: ShapeId,
      serviceResources: List[HttpResource]
  ): HttpValidated[List[HttpOperationInputMember]] = {
    val operationName = operation.getId.getName
    if (inputShapeId == ShapeId.from("smithy.api#Unit")) {
      Nil.validNel
    } else {
      model.getShape(inputShapeId).toScala.flatMap(_.asStructureShape.toScala) match {
        case None            =>
          InvalidHttpOperation(
            serviceShape,
            operationName,
            s"input shape '${inputShapeId.toString}' must be a structure or Unit"
          ).invalidNel
        case Some(structure) =>
          val boundResource     =
            resolveBoundResource(structure).orElse(operationBoundResource(operation.getId, serviceResources))
          val resourceReference = resolveResourceReference(structure)
          validateBoundResource(serviceShape, operationName, boundResource, serviceResources).andThen { _ =>
            structure.getAllMembers.asScala.toList
              .traverse { case (memberName, member) =>
                extractMember(
                  model = model,
                  serviceShape = serviceShape,
                  operationName = operationName,
                  memberName = memberName,
                  member = member,
                  boundResource = boundResource,
                  resourceReference = resourceReference,
                  serviceResources = serviceResources
                )
              }
          }
      }
    }
  }

  private def resolveBoundResource(structure: StructureShape): Option[ShapeId] =
    resolveResourceReference(structure).map(_.getResource)

  private def resolveResourceReference(structure: StructureShape): Option[ReferencesTrait.Reference] =
    Option(structure.getTrait(classOf[ReferencesTrait]).orElse(null))
      .flatMap(referencesTrait => referencesTrait.getReferences.asScala.headOption)

  private def extractMember(
      model: Model,
      serviceShape: ShapeId,
      operationName: String,
      memberName: String,
      member: MemberShape,
      boundResource: Option[ShapeId],
      resourceReference: Option[ReferencesTrait.Reference],
      serviceResources: List[HttpResource]
  ): HttpValidated[HttpOperationInputMember] = {
    val resourceIdentifierName =
      resolveResourceIdentifierName(memberName, member, resourceReference, boundResource, serviceResources)
    val binding                = resolveBinding(member)
    (
      validateResourceIdentifier(
        serviceShape,
        operationName,
        memberName,
        boundResource,
        resourceIdentifierName,
        serviceResources
      ),
      HttpSmithyTypeResolver.resolveMemberType(model, serviceShape, operationName, memberName, member)
    ).mapN { (_, memberType) =>
      HttpOperationInputMember(
        name = memberName,
        targetShape = member.getTarget,
        typeName = memberType.typeName,
        timestampFormat = memberType.timestampFormat,
        required = member.requiredMember,
        binding = binding,
        resourceIdentifierName = resourceIdentifierName
      )
    }
  }

  private def resolveResourceIdentifierName(
      memberName: String,
      member: MemberShape,
      resourceReference: Option[ReferencesTrait.Reference],
      boundResource: Option[ShapeId],
      serviceResources: List[HttpResource]
  ): Option[String] = {
    val explicitIdentifier =
      Option(member.getTrait(classOf[ResourceIdentifierTrait]).orElse(null)).map { traitValue =>
        Option(traitValue.getValue).filter(_.nonEmpty).getOrElse(memberName)
      }
    val mappedIdentifier   =
      resourceReference.flatMap(reference =>
        reference.getIds.asScala.collectFirst {
          case (identifierName, mappedMemberName) if mappedMemberName == memberName =>
            identifierName
        })
    val matchedIdentifier  =
      boundResource.flatMap(resourceId =>
        serviceResources.find(_.shapeId == resourceId).flatMap { resource =>
          Option.when(resource.identifiers.contains(memberName))(memberName)
        })
    explicitIdentifier.orElse(mappedIdentifier).orElse(matchedIdentifier)
  }

  private def resolveBinding(member: MemberShape): HttpInputMemberBinding =
    if (Option(member.getTrait(classOf[HttpLabelTrait]).orElse(null)).isDefined) {
      HttpInputMemberBinding.PathLabel()
    } else if (Option(member.getTrait(classOf[HttpQueryTrait]).orElse(null)).isDefined) {
      val queryTrait =
        member.getTrait(classOf[HttpQueryTrait]).get()
      HttpInputMemberBinding.Query(
        queryName = Option(queryTrait.getValue).filter(_.nonEmpty).getOrElse(member.getMemberName)
      )
    } else if (Option(member.getTrait(classOf[HttpHeaderTrait]).orElse(null)).isDefined) {
      val headerTrait =
        member.getTrait(classOf[HttpHeaderTrait]).get()
      HttpInputMemberBinding.Header(
        headerName = Option(headerTrait.getValue).filter(_.nonEmpty).getOrElse(member.getMemberName)
      )
    } else if (Option(member.getTrait(classOf[HttpPayloadTrait]).orElse(null)).isDefined) {
      HttpInputMemberBinding.Payload()
    } else {
      HttpInputMemberBinding.Payload()
    }

  private def validateBoundResource(
      serviceShape: ShapeId,
      operationName: String,
      boundResource: Option[ShapeId],
      serviceResources: List[HttpResource]
  ): HttpValidated[Unit] =
    boundResource match {
      case None             =>
        ().validNel
      case Some(resourceId) =>
        if (serviceResources.exists(_.shapeId == resourceId)) {
          ().validNel
        } else {
          InvalidHttpOperation(
            serviceShape,
            operationName,
            s"input is bound to unknown resource '${resourceId.toString}'"
          ).invalidNel
        }
    }

  private def validateResourceIdentifier(
      serviceShape: ShapeId,
      operationName: String,
      memberName: String,
      boundResource: Option[ShapeId],
      resourceIdentifierName: Option[String],
      serviceResources: List[HttpResource]
  ): HttpValidated[Unit] =
    resourceIdentifierName match {
      case None                 =>
        ().validNel
      case Some(identifierName) =>
        boundResource match {
          case None             =>
            InvalidHttpOperation(
              serviceShape,
              operationName,
              s"input member '$memberName' is a resource identifier but input shape is not bound to a resource"
            ).invalidNel
          case Some(resourceId) =>
            serviceResources.find(_.shapeId == resourceId) match {
              case None           =>
                InvalidHttpOperation(
                  serviceShape,
                  operationName,
                  s"input is bound to unknown resource '${resourceId.toString}'"
                ).invalidNel
              case Some(resource) =>
                if (resource.identifiers.contains(identifierName)) {
                  ().validNel
                } else {
                  InvalidHttpOperation(
                    serviceShape,
                    operationName,
                    s"input member '$memberName' references resource identifier '$identifierName' " +
                      s"but resource '${resource.name}' declares identifiers: ${resource.identifiers.mkString(", ")}"
                  ).invalidNel
                }
            }
        }
    }
}
