package com.jacoby6000.smithplates.http

import com.jacoby6000.smithplates.http.model.HttpInputMemberBinding
import com.jacoby6000.smithplates.http.model.HttpInputMemberOrderWarning
import com.jacoby6000.smithplates.http.model.HttpOperationInputMember
import munit.FunSuite
import software.amazon.smithy.model.shapes.ShapeId

class HttpInputMemberOrderingSpec extends FunSuite {
  test("orderInputMembers sorts headers, URI path labels, then queries") {
    val members =
      List(
        HttpInputMemberOrderingSpec.internal.queryMember("since"),
        HttpInputMemberOrderingSpec.internal.pathMember("id"),
        HttpInputMemberOrderingSpec.internal.headerMember("region"),
        HttpInputMemberOrderingSpec.internal.queryMember("category")
      )
    val ordered =
      HttpInputMemberOrdering.orderInputMembers("/v1/widgets/{id}", members).map(_.name)
    assertEquals(ordered, List("region", "id", "since", "category"))
  }

  test("orderInputMembers sorts path labels by URI appearance order") {
    val members =
      List(
        HttpInputMemberOrderingSpec.internal.pathMember("taskId"),
        HttpInputMemberOrderingSpec.internal.pathMember("projectId")
      )
    val ordered =
      HttpInputMemberOrdering
        .orderInputMembers("/projects/{projectId}/tasks/{taskId}", members)
        .map(_.name)
    assertEquals(ordered, List("projectId", "taskId"))
  }

  test("lintInputMemberOrder warns when Smithy declaration order differs from canonical order") {
    val members =
      List(
        HttpInputMemberOrderingSpec.internal.queryMember("category"),
        HttpInputMemberOrderingSpec.internal.pathMember("id"),
        HttpInputMemberOrderingSpec.internal.headerMember("region")
      )
    val warning =
      HttpInputMemberOrdering
        .lintInputMemberOrder(
          ShapeId.from("example#WidgetApi"),
          "InspectWidget",
          ShapeId.from("example#InspectWidgetInput"),
          "/v1/widgets/{id}",
          members
        )
        .get
        .asInstanceOf[HttpInputMemberOrderWarning]
    assertEquals(warning.declaredOrder, List("category", "id", "region"))
    assertEquals(warning.expectedOrder, List("region", "id", "category"))
  }
}
object HttpInputMemberOrderingSpec {

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    val stringShape                                                                     = ShapeId.from("smithy.api#String")
    def headerMember(name: String): HttpOperationInputMember                            =
      member(name, HttpInputMemberBinding.Header(name))
    def pathMember(name: String): HttpOperationInputMember                              =
      member(name, HttpInputMemberBinding.PathLabel())
    def queryMember(name: String): HttpOperationInputMember                             =
      member(name, HttpInputMemberBinding.Query(name))
    def member(name: String, binding: HttpInputMemberBinding): HttpOperationInputMember =
      HttpOperationInputMember(
        name = name,
        targetShape = stringShape,
        typeName = "String",
        timestampFormat = None,
        required = true,
        binding = binding,
        resourceIdentifierName = None
      )
  }
}
