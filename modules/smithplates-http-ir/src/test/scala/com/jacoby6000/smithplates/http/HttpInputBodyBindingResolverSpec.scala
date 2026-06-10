package com.jacoby6000.smithplates.http

import com.jacoby6000.smithplates.http.model.HttpInputMemberBinding
import com.jacoby6000.smithplates.http.model.HttpOperationBodyBinding
import com.jacoby6000.smithplates.http.model.HttpOperationInputMember
import munit.FunSuite
import software.amazon.smithy.model.shapes.ShapeId

class HttpInputBodyBindingResolverSpec extends FunSuite {
  private val unitShapeId: ShapeId                    = ShapeId.from("smithy.api#Unit")
  private val inputShapeId: ShapeId                   = ShapeId.from("example#CreateProjectInput")
  private val payloadMember: HttpOperationInputMember =
    HttpOperationInputMember(
      name = "name",
      targetShape = ShapeId.from("smithy.api#String"),
      typeName = "String",
      timestampFormat = None,
      required = true,
      binding = HttpInputMemberBinding.Payload(),
      resourceIdentifierName = None
    )
  private val pathMember: HttpOperationInputMember    =
    HttpOperationInputMember(
      name = "projectId",
      targetShape = ShapeId.from("smithy.api#String"),
      typeName = "String",
      timestampFormat = None,
      required = true,
      binding = HttpInputMemberBinding.PathLabel(),
      resourceIdentifierName = Some("projectId")
    )

  test("HttpInputBodyBindingResolver returns None for Unit input") {
    assertEquals(
      HttpInputBodyBindingResolver.resolve(unitShapeId, Nil),
      HttpOperationBodyBinding.None
    )
  }

  test("HttpInputBodyBindingResolver returns Document when all input members are payload") {
    assertEquals(
      HttpInputBodyBindingResolver.resolve(inputShapeId, List(payloadMember)),
      HttpOperationBodyBinding.Document(inputShapeId)
    )
  }

  test("HttpInputBodyBindingResolver returns Members when payload members coexist with route bindings") {
    assertEquals(
      HttpInputBodyBindingResolver.resolve(inputShapeId, List(pathMember, payloadMember)),
      HttpOperationBodyBinding.Members(List(payloadMember))
    )
  }

  test("HttpInputBodyBindingResolver returns None when input has only route bindings") {
    assertEquals(
      HttpInputBodyBindingResolver.resolve(inputShapeId, List(pathMember)),
      HttpOperationBodyBinding.None
    )
  }
}
