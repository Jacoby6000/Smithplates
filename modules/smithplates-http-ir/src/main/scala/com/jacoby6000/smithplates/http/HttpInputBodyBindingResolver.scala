package com.jacoby6000.smithplates.http

import com.jacoby6000.smithplates.http.model.HttpInputMemberBinding
import com.jacoby6000.smithplates.http.model.HttpOperationBodyBinding
import com.jacoby6000.smithplates.http.model.HttpOperationInputMember
import software.amazon.smithy.model.shapes.ShapeId

private[http] object HttpInputBodyBindingResolver {
  def resolve(
      inputShape: ShapeId,
      members: List[HttpOperationInputMember]
  ): HttpOperationBodyBinding =
    if (inputShape == internal.UnitShapeId) {
      HttpOperationBodyBinding.None
    } else {
      val payloadMembers = members.filter {
        case HttpOperationInputMember(_, _, _, _, _, HttpInputMemberBinding.Payload(), _) => true
        case _                                                                            => false
      }
      if (payloadMembers.isEmpty) {
        HttpOperationBodyBinding.None
      } else {
        val hasRouteMembers = members.exists {
          case HttpOperationInputMember(_, _, _, _, _, HttpInputMemberBinding.Payload(), _) => false
          case _                                                                            => true
        }
        if (hasRouteMembers) {
          HttpOperationBodyBinding.Members(payloadMembers)
        } else {
          HttpOperationBodyBinding.Document(inputShape)
        }
      }
    }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    val UnitShapeId: ShapeId = ShapeId.from("smithy.api#Unit")
  }
}
