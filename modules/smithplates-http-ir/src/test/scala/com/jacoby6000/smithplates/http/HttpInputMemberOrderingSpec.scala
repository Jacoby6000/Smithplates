package com.jacoby6000.smithplates.http

import com.jacoby6000.smithplates.http.model.HttpInputMemberBinding
import com.jacoby6000.smithplates.http.model.HttpInputMemberOrderWarning
import com.jacoby6000.smithplates.http.model.HttpOperationInputMember
import munit.FunSuite
import software.amazon.smithy.model.shapes.ShapeId

class HttpInputMemberOrderingSpec extends FunSuite {
  private val stringShape = ShapeId.from("smithy.api#String")

  test("orderInputMembers sorts headers, URI path labels, then queries") {
    val members =
      List(
        queryMember("since"),
        pathMember("id"),
        headerMember("region"),
        queryMember("category")
      )
    val ordered =
      HttpInputMemberOrdering.orderInputMembers("/v1/widgets/{id}", members).map(_.name)
    assertEquals(ordered, List("region", "id", "since", "category"))
  }

  test("orderInputMembers sorts path labels by URI appearance order") {
    val members =
      List(
        pathMember("taskId"),
        pathMember("projectId")
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
        queryMember("category"),
        pathMember("id"),
        headerMember("region")
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

  private def headerMember(name: String): HttpOperationInputMember =
    member(name, HttpInputMemberBinding.Header(name))

  private def pathMember(name: String): HttpOperationInputMember =
    member(name, HttpInputMemberBinding.PathLabel())

  private def queryMember(name: String): HttpOperationInputMember =
    member(name, HttpInputMemberBinding.Query(name))

  private def member(name: String, binding: HttpInputMemberBinding): HttpOperationInputMember =
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
