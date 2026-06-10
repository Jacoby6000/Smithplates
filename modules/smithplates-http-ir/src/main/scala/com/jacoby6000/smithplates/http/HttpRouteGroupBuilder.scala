package com.jacoby6000.smithplates.http

import com.jacoby6000.smithplates.http.model.HttpOperation
import com.jacoby6000.smithplates.http.model.HttpRouteGroup

object HttpRouteGroupBuilder {
  def build(operations: List[HttpOperation]): List[HttpRouteGroup] =
    operations
      .groupBy(_.tags.headOption.getOrElse("default"))
      .toList
      .sortBy(_._1)
      .map { case (tag, groupedOperations) =>
        HttpRouteGroup(
          tag = tag,
          operations = groupedOperations.sortBy(_.name)
        )
      }
}
