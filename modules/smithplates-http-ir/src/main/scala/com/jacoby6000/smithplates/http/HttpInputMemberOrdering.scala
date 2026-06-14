package com.jacoby6000.smithplates.http

import com.jacoby6000.smithplates.http.model.HttpInputMemberBinding
import com.jacoby6000.smithplates.http.model.HttpInputMemberOrderWarning
import com.jacoby6000.smithplates.http.model.HttpOperationInputMember
import com.jacoby6000.smithplates.http.model.HttpSchemaWarning
import software.amazon.smithy.model.shapes.ShapeId

private[http] object HttpInputMemberOrdering {
  private val UriLabelPattern = """\{([^}]+)\}""".r

  def orderInputMembers(
      uri: String,
      members: List[HttpOperationInputMember]
  ): List[HttpOperationInputMember] = {
    val (headers, pathLabels, queries, payloads) = partitionByBinding(members)
    val uriLabels                                = uriLabelNames(uri)
    val orderedPathLabels                        = sortPathLabels(pathLabels, uriLabels)
    headers ++ orderedPathLabels ++ queries ++ payloads
  }

  def lintInputMemberOrder(
      serviceShape: ShapeId,
      operationName: String,
      inputShape: ShapeId,
      uri: String,
      members: List[HttpOperationInputMember]
  ): Option[HttpSchemaWarning] = {
    val routeMembers         = members.filter(isRouteParameter)
    val expectedRouteMembers = orderInputMembers(uri, members).filter(isRouteParameter)
    if (routeMembers.map(_.name) == expectedRouteMembers.map(_.name)) {
      None
    } else {
      Some(
        HttpInputMemberOrderWarning(
          serviceShape = serviceShape,
          operationName = operationName,
          inputShape = inputShape,
          declaredOrder = routeMembers.map(_.name),
          expectedOrder = expectedRouteMembers.map(_.name)
        )
      )
    }
  }

  private def isRouteParameter(member: HttpOperationInputMember): Boolean =
    member.binding match {
      case HttpInputMemberBinding.Payload() => false
      case _                                => true
    }

  private def uriLabelNames(uri: String): List[String] =
    UriLabelPattern.findAllMatchIn(uri).map(_.group(1)).toList

  private def partitionByBinding(
      members: List[HttpOperationInputMember]
  ): (
      List[HttpOperationInputMember],
      List[HttpOperationInputMember],
      List[HttpOperationInputMember],
      List[HttpOperationInputMember]) = {
    val headers    = List.newBuilder[HttpOperationInputMember]
    val pathLabels = List.newBuilder[HttpOperationInputMember]
    val queries    = List.newBuilder[HttpOperationInputMember]
    val payloads   = List.newBuilder[HttpOperationInputMember]
    members.foreach { member =>
      member.binding match {
        case HttpInputMemberBinding.Header(_)   => headers += member
        case HttpInputMemberBinding.PathLabel() => pathLabels += member
        case HttpInputMemberBinding.Query(_)    => queries += member
        case HttpInputMemberBinding.Payload()   => payloads += member
      }
    }
    (headers.result(), pathLabels.result(), queries.result(), payloads.result())
  }

  private def sortPathLabels(
      pathLabels: List[HttpOperationInputMember],
      uriLabels: List[String]
  ): List[HttpOperationInputMember] =
    pathLabels.zipWithIndex
      .sortBy { case (member, sourceIndex) =>
        val uriIndex = uriLabels.indexOf(member.name)
        if (uriIndex >= 0) {
          uriIndex
        } else {
          uriLabels.length + sourceIndex
        }
      }
      .map(_._1)
}
