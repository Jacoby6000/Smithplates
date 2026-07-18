package com.jacoby6000.smithplates.http.codegen

import cats.syntax.all.*
import com.jacoby6000.smithplates.codegen.core.CodegenValidated
import com.jacoby6000.smithplates.codegen.core.InvalidSmithyShape
import com.jacoby6000.smithplates.http.HttpStaticHeaderExtractor
import com.jacoby6000.smithplates.http.HttpStructureExtractor
import com.jacoby6000.smithplates.http.model.*
import com.jacoby6000.smithplates.smithy.neutral.ModelIds
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ShapeId

/** Derives [[HttpMeta]] request/response facts from legacy HTTP operation binding IR. */
object HttpCoreMetaBuilder {
  final case class OperationShapeMeta(
      requestMetaByShapeId: Map[ShapeId, HttpMeta.HttpRequestMeta],
      responseMetaByShapeId: Map[ShapeId, HttpMeta.HttpResponseMeta]
  )

  val emptyOperationShapeMeta: OperationShapeMeta =
    OperationShapeMeta(Map.empty, Map.empty)

  def buildOperationShapeMeta(
      model: Model,
      serviceShape: ShapeId,
      operations: List[HttpOperation]
  ): CodegenValidated[OperationShapeMeta] =
    buildRequestMetaByShapeId(model, serviceShape, operations).map { requestMetaByShapeId =>
      OperationShapeMeta(
        requestMetaByShapeId = requestMetaByShapeId,
        responseMetaByShapeId = buildSuccessResponseMetaByShapeId(operations)
      )
    }

  def structureFeature(shapeId: ShapeId, operationShapeMeta: OperationShapeMeta): HttpMeta =
    operationShapeMeta.responseMetaByShapeId
      .get(shapeId)
      .map(meta => meta: HttpMeta)
      .orElse(operationShapeMeta.requestMetaByShapeId.get(shapeId).map(meta => meta: HttpMeta))
      .getOrElse(HttpMeta.HttpNestedField)

  def buildRequestMetaByShapeId(
      model: Model,
      serviceShape: ShapeId,
      operations: List[HttpOperation]
  ): CodegenValidated[Map[ShapeId, HttpMeta.HttpRequestMeta]] = {
    val unitShapeId = HttpStructureExtractor.internal.UnitShapeId
    operations
      .filter(_.inputShape != unitShapeId)
      .distinctBy(_.inputShape)
      .traverse { operation =>
        HttpStaticHeaderExtractor
          .extract(model, serviceShape, operation.inputShape)
          .leftMap(_.map(error => InvalidSmithyShape(ModelIds.fromShapeId(serviceShape), error.message)))
          .map { staticHeaders =>
            operation.inputShape -> HttpMeta.HttpRequestMeta(
              staticHeaders = staticHeaders.toMap,
              dynamicHeaderFields = dynamicHeaderFields(operation.inputMembers)
            )
          }
      }
      .map(_.toMap)
  }

  def buildSuccessResponseMetaByShapeId(
      operations: List[HttpOperation]
  ): Map[ShapeId, HttpMeta.HttpResponseMeta] =
    operations.flatMap { operation =>
      operation.responseBinding.successVariant.toList.map { variant =>
        variant.modelShapeId -> HttpMeta.HttpResponseMeta(
          statusCode = variant.statusCode,
          staticHeaders = variant.staticHeaders.toMap,
          dynamicHeaderFields = variant.headerBindings.toMap
        )
      }
    }.toMap

  def dynamicHeaderFields(members: List[HttpOperationInputMember]): Map[String, String] =
    members.collect {
      case HttpOperationInputMember(name, _, _, _, _, HttpInputMemberBinding.Header(headerName), _, _) =>
        name -> headerName
    }.toMap
}
