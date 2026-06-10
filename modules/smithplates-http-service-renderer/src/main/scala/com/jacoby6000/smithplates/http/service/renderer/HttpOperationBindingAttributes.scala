package com.jacoby6000.smithplates.http.service.renderer

import com.jacoby6000.smithplates.http.model.HttpOperation
import software.amazon.smithy.model.shapes.ShapeId

object HttpOperationBindingAttributes {
  private val UnitShapeId: ShapeId = ShapeId.from("smithy.api#Unit")
  private val EmptyResponseType    = "__empty__"

  final case class OperationBindingView(
      operationName: String,
      pythonName: String,
      variants: List[ResponseVariantView]
  )

  final case class ResponseVariantView(
      typeName: String,
      statusCode: Int,
      mediaTypeLiteral: String,
      headerBindingsLiteral: String = "()"
  )

  def bindingsForService(service: HttpCodegenServiceContext): List[OperationBindingView] =
    service.routeGroups.flatMap(_.operations).map(bindingForOperation).distinctBy(_.operationName)

  def bindingForOperation(operation: HttpOperation): OperationBindingView =
    OperationBindingView(
      operationName = operation.name,
      pythonName = HttpCodegenTemplateAttributes.toOperationSnakeCase(operation.name),
      variants = List(successVariant(operation))
    )

  def responseTypeAnnotation(operation: HttpOperation): String =
    responseTypeName(operation).getOrElse("None")

  private def successVariant(operation: HttpOperation): ResponseVariantView =
    ResponseVariantView(
      typeName = responseTypeName(operation).getOrElse(EmptyResponseType),
      statusCode = operation.successStatusCode,
      mediaTypeLiteral = responseTypeName(operation).map(_ => "\"application/json\"").getOrElse("None")
    )

  private def responseTypeName(operation: HttpOperation): Option[String] =
    operation.outputShape.filter(_ != UnitShapeId).map(_.getName)
}
