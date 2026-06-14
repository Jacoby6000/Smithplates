package com.jacoby6000.smithplates.http

import cats.syntax.all.*
import com.jacoby6000.smithplates.http.SmithyHttpTraitAccess.*
import com.jacoby6000.smithplates.http.model.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.traits.HttpHeaderTrait
import software.amazon.smithy.model.traits.HttpPayloadTrait

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

private[http] object HttpOperationOutputMemberExtractor {
  private val UnitShapeId: ShapeId = ShapeId.from("smithy.api#Unit")

  def extract(
      model: Model,
      serviceShape: ShapeId,
      operationName: String,
      outputShapeId: Option[ShapeId]
  ): HttpValidated[List[HttpOperationOutputMember]] =
    outputShapeId.filter(_ != UnitShapeId) match {
      case None          => Nil.validNel
      case Some(shapeId) =>
        extractFromStructure(model, serviceShape, operationName, shapeId)
    }

  def extractFromStructure(
      model: Model,
      serviceShape: ShapeId,
      contextName: String,
      structureShapeId: ShapeId
  ): HttpValidated[List[HttpOperationOutputMember]] =
    model.getShape(structureShapeId).toScala.flatMap(_.asStructureShape.toScala) match {
      case None            =>
        InvalidHttpOperation(
          serviceShape,
          contextName,
          s"output shape '${structureShapeId.toString}' must be a structure or Unit"
        ).invalidNel
      case Some(structure) =>
        structure.getAllMembers.asScala.toList.traverse { case (memberName, member) =>
          extractMember(model, serviceShape, contextName, memberName, member)
        }
    }

  private def extractMember(
      model: Model,
      serviceShape: ShapeId,
      contextName: String,
      memberName: String,
      member: MemberShape
  ): HttpValidated[HttpOperationOutputMember] =
    HttpSmithyTypeResolver
      .resolveMemberType(model, serviceShape, contextName, memberName, member)
      .map { memberType =>
        HttpOperationOutputMember(
          name = memberName,
          targetShape = member.getTarget,
          typeName = memberType.typeName,
          timestampFormat = memberType.timestampFormat,
          required = member.requiredMember,
          binding = resolveBinding(member)
        )
      }

  private def resolveBinding(member: MemberShape): HttpOutputMemberBinding =
    if (Option(member.getTrait(classOf[HttpHeaderTrait]).orElse(null)).isDefined) {
      val headerTrait =
        member.getTrait(classOf[HttpHeaderTrait]).get()
      HttpOutputMemberBinding.Header(
        headerName = Option(headerTrait.getValue).filter(_.nonEmpty).getOrElse(member.getMemberName)
      )
    } else if (Option(member.getTrait(classOf[HttpPayloadTrait]).orElse(null)).isDefined) {
      HttpOutputMemberBinding.Payload(explicitBinding = true)
    } else {
      HttpOutputMemberBinding.Payload(explicitBinding = false)
    }
}
