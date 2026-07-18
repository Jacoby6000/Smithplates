package com.jacoby6000.smithplates.http

import com.jacoby6000.smithplates.http.model.HttpInputMemberBinding
import com.jacoby6000.smithplates.http.model.HttpOperationBodyBinding
import com.jacoby6000.smithplates.http.model.HttpOperationInputMember
import munit.FunSuite
import software.amazon.smithy.model.shapes.ShapeId

class HttpInputBodyBindingResolverSpec extends FunSuite {
  test("HttpInputBodyBindingResolver returns None for Unit input") {
    assertEquals(
      HttpInputBodyBindingResolver.resolve(HttpInputBodyBindingResolverSpec.internal.unitShapeId, Nil),
      HttpOperationBodyBinding.None
    )
  }

  test("HttpInputBodyBindingResolver returns Document when all input members are payload") {
    assertEquals(
      HttpInputBodyBindingResolver.resolve(
        HttpInputBodyBindingResolverSpec.internal.inputShapeId,
        List(HttpInputBodyBindingResolverSpec.internal.payloadMember)),
      HttpOperationBodyBinding.Document(HttpInputBodyBindingResolverSpec.internal.inputShapeId)
    )
  }

  test("HttpInputBodyBindingResolver returns Members when payload members coexist with route bindings") {
    assertEquals(
      HttpInputBodyBindingResolver.resolve(
        HttpInputBodyBindingResolverSpec.internal.inputShapeId,
        List(
          HttpInputBodyBindingResolverSpec.internal.pathMember,
          HttpInputBodyBindingResolverSpec.internal.payloadMember)
      ),
      HttpOperationBodyBinding.Members(List(HttpInputBodyBindingResolverSpec.internal.payloadMember))
    )
  }

  test("HttpInputBodyBindingResolver returns None when input has only route bindings") {
    assertEquals(
      HttpInputBodyBindingResolver.resolve(
        HttpInputBodyBindingResolverSpec.internal.inputShapeId,
        List(HttpInputBodyBindingResolverSpec.internal.pathMember)),
      HttpOperationBodyBinding.None
    )
  }

  test("HttpInputBodyBindingResolver returns NestedDocument for single @nestedProperties payload") {
    assertEquals(
      HttpInputBodyBindingResolver.resolve(
        HttpInputBodyBindingResolverSpec.internal.inputShapeId,
        List(HttpInputBodyBindingResolverSpec.internal.nestedPayloadMember)),
      HttpOperationBodyBinding.NestedDocument(
        HttpInputBodyBindingResolverSpec.internal.inputShapeId,
        HttpInputBodyBindingResolverSpec.internal.nestedPayloadMember
      )
    )
  }

  test("HttpInputBodyBindingResolver returns Members for @nestedProperties payload alongside route bindings") {
    assertEquals(
      HttpInputBodyBindingResolver.resolve(
        HttpInputBodyBindingResolverSpec.internal.inputShapeId,
        List(
          HttpInputBodyBindingResolverSpec.internal.pathMember,
          HttpInputBodyBindingResolverSpec.internal.nestedPayloadMember)
      ),
      HttpOperationBodyBinding.Members(List(HttpInputBodyBindingResolverSpec.internal.nestedPayloadMember))
    )
  }
}
object HttpInputBodyBindingResolverSpec {

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    val unitShapeId: ShapeId                          = ShapeId.from("smithy.api#Unit")
    val inputShapeId: ShapeId                         = ShapeId.from("example#CreateProjectInput")
    val payloadMember: HttpOperationInputMember       =
      HttpOperationInputMember(
        name = "name",
        targetShape = ShapeId.from("smithy.api#String"),
        typeName = "String",
        timestampFormat = None,
        required = true,
        binding = HttpInputMemberBinding.Payload(),
        resourceIdentifierName = None
      )
    val nestedPayloadMember: HttpOperationInputMember =
      HttpOperationInputMember(
        name = "body",
        targetShape = ShapeId.from("example#BodyShape"),
        typeName = "BodyShape",
        timestampFormat = None,
        required = true,
        binding = HttpInputMemberBinding.Payload(),
        resourceIdentifierName = None,
        nestedProperties = true
      )
    val pathMember: HttpOperationInputMember          =
      HttpOperationInputMember(
        name = "projectId",
        targetShape = ShapeId.from("smithy.api#String"),
        typeName = "String",
        timestampFormat = None,
        required = true,
        binding = HttpInputMemberBinding.PathLabel(),
        resourceIdentifierName = Some("projectId")
      )
  }
}
