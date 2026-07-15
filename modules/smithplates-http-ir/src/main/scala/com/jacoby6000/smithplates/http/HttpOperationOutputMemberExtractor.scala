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
  def extract(
      model: Model,
      serviceShape: ShapeId,
      operationName: String,
      outputShapeId: Option[ShapeId],
      isWebsocket: Boolean = false
  ): HttpValidated[List[HttpOperationOutputMember]] =
    if (isWebsocket) {
      Nil.validNel
    } else {
      outputShapeId.filter(_ != internal.UnitShapeId) match {
        case None          => Nil.validNel
        case Some(shapeId) =>
          extractFromStructure(model, serviceShape, operationName, shapeId)
      }
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
          internal.extractMember(model, serviceShape, contextName, memberName, member)
        }
    }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    val UnitShapeId: ShapeId = ShapeId.from("smithy.api#Unit")

    def extractMember(
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

    def resolveBinding(member: MemberShape): HttpOutputMemberBinding =
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
}
