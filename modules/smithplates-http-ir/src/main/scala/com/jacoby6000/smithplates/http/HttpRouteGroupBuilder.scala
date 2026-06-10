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
        val apiModuleName     = s"${tag}_api"
        val protocolClassName = s"${toPascalCase(tag)}ApiServiceProtocol"
        HttpRouteGroup(
          tag = tag,
          apiModuleName = apiModuleName,
          protocolClassName = protocolClassName,
          operations = groupedOperations.sortBy(_.name)
        )
      }

  private def toPascalCase(value: String): String =
    value
      .split("[_\\-]+")
      .filter(_.nonEmpty)
      .map(segment => s"${segment.head.toUpper}${segment.tail}")
      .mkString
}
