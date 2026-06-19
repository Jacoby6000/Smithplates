package com.jacoby6000.smithplates.http

import cats.syntax.all.*
import com.jacoby6000.smithplates.http.model.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ShapeId

private[http] object HttpResponseVariantResolver {
  def resolveOperationBinding(
      model: Model,
      serviceShape: ShapeId,
      operationName: String,
      successStatusCode: Int,
      outputShape: Option[ShapeId],
      outputMembers: List[HttpOperationOutputMember],
      operationErrors: List[HttpOperationError],
      serialization: HttpSerialization
  ): HttpValidated[HttpOperationResponseBinding] =
    (
      internal.resolveSuccessVariant(
        model = model,
        serviceShape = serviceShape,
        successStatusCode = successStatusCode,
        outputShape = outputShape,
        outputMembers = outputMembers,
        serialization = serialization
      ),
      operationErrors.traverse(internal.resolveErrorVariant(model, serviceShape, operationName, _, serialization))
    ).mapN(HttpOperationResponseBinding.apply)

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    val UnitShapeId: ShapeId = ShapeId.from("smithy.api#Unit")

    def resolveSuccessVariant(
        model: Model,
        serviceShape: ShapeId,
        successStatusCode: Int,
        outputShape: Option[ShapeId],
        outputMembers: List[HttpOperationOutputMember],
        serialization: HttpSerialization
    ): HttpValidated[Option[HttpResponseVariant]] =
      outputShape.filter(_ != UnitShapeId) match {
        case None          =>
          HttpResponseVariant(
            variantTypeName = "__empty__",
            statusCode = successStatusCode,
            mediaType = None,
            headerBindings = Nil,
            staticHeaders = Nil,
            modelShapeId = UnitShapeId
          ).validNel.map(Some(_))
        case Some(shapeId) =>
          resolveStructureVariant(
            model = model,
            serviceShape = serviceShape,
            structureName = shapeId.getName,
            structureShapeId = shapeId,
            statusCode = successStatusCode,
            members = outputMembers,
            serialization = serialization,
            errorVariant = false
          ).map(Some(_))
      }

    def resolveErrorVariant(
        model: Model,
        serviceShape: ShapeId,
        operationName: String,
        operationError: HttpOperationError,
        serialization: HttpSerialization
    ): HttpValidated[HttpResponseVariant] =
      HttpOperationOutputMemberExtractor
        .extractFromStructure(model, serviceShape, operationName, operationError.shapeId)
        .andThen { members =>
          resolveStructureVariant(
            model = model,
            serviceShape = serviceShape,
            structureName = operationError.name,
            structureShapeId = operationError.shapeId,
            statusCode = operationError.statusCode,
            members = members,
            serialization = serialization,
            errorVariant = true
          )
        }

    def resolveStructureVariant(
        model: Model,
        serviceShape: ShapeId,
        structureName: String,
        structureShapeId: ShapeId,
        statusCode: Int,
        members: List[HttpOperationOutputMember],
        serialization: HttpSerialization,
        errorVariant: Boolean
    ): HttpValidated[HttpResponseVariant] = {
      val headerMembers          = members.collect {
        case member @ HttpOperationOutputMember(_, _, _, _, _, HttpOutputMemberBinding.Header(_)) =>
          member
      }
      val explicitPayloadMembers = members.collect {
        case member @ HttpOperationOutputMember(_, _, _, _, _, HttpOutputMemberBinding.Payload(true)) =>
          member
      }
      val headerBindings         = headerMembers.collect {
        case HttpOperationOutputMember(name, _, _, _, _, HttpOutputMemberBinding.Header(headerName)) =>
          (name, headerName)
      }
      val staticHeaderShapeId    =
        if (explicitPayloadMembers.size == 1 && headerMembers.isEmpty) {
          explicitPayloadMembers.head.targetShape
        } else {
          structureShapeId
        }

      val relatedShapeIds =
        if (staticHeaderShapeId != structureShapeId) {
          List(structureShapeId)
        } else {
          Nil
        }

      HttpStaticHeaderExtractor
        .extract(model, serviceShape, staticHeaderShapeId, relatedShapeIds)
        .map { staticHeaders =>
          if (explicitPayloadMembers.size == 1 && headerMembers.isEmpty) {
            val payload = explicitPayloadMembers.head
            HttpResponseVariant(
              variantTypeName = if (errorVariant) {
                structureName
              } else {
                payload.typeName
              },
              statusCode = statusCode,
              mediaType = mediaTypeForSerialization(serialization),
              headerBindings = Nil,
              staticHeaders = staticHeaders,
              modelShapeId = if (errorVariant) {
                structureShapeId
              } else {
                payload.targetShape
              }
            )
          } else if (headerMembers.nonEmpty && headerMembers.size == members.size) {
            HttpResponseVariant(
              variantTypeName = structureName,
              statusCode = statusCode,
              mediaType = None,
              headerBindings = headerBindings,
              staticHeaders = staticHeaders,
              modelShapeId = structureShapeId
            )
          } else {
            HttpResponseVariant(
              variantTypeName = structureName,
              statusCode = statusCode,
              mediaType = mediaTypeForSerialization(serialization),
              headerBindings = headerBindings,
              staticHeaders = staticHeaders,
              modelShapeId = structureShapeId
            )
          }
        }
    }

    def mediaTypeForSerialization(serialization: HttpSerialization): Option[String] =
      serialization match {
        case HttpSerialization.Json => Some("application/json")
      }
  }
}
