package com.jacoby6000.smithplates.http.service.renderer

import com.jacoby6000.smithplates.http.model.HttpInputMemberBinding
import com.jacoby6000.smithplates.http.model.HttpOperation
import com.jacoby6000.smithplates.http.model.HttpOperationInputMember

object HttpCodegenFastApiAttributes {
  def routeParameters(operation: HttpOperation): List[HttpOperationInputMember] =
    operation.inputMembers.filter(isRouteParameter)

  def pythonParameterType(member: HttpOperationInputMember): String =
    if (member.required) {
      member.pythonTypeName
    } else {
      s"${member.pythonTypeName} | None"
    }

  def fastapiBinding(member: HttpOperationInputMember): String =
    member.binding match {
      case HttpInputMemberBinding.PathLabel()        => "Path()"
      case HttpInputMemberBinding.Query(queryName)   =>
        s"""Query(alias="${queryName}")"""
      case HttpInputMemberBinding.Header(headerName) =>
        s"""Header(alias="${headerName}")"""
      case HttpInputMemberBinding.Payload()          => "Body()"
    }

  def parameterDefault(member: HttpOperationInputMember): String =
    if (member.required) {
      ""
    } else {
      " = None"
    }

  def operationCallArguments(operation: HttpOperation): String =
    routeParameters(operation)
      .map { member =>
        val parameterName = HttpCodegenTemplateAttributes.toSnakeCase(member.name)
        s"$parameterName=$parameterName"
      }
      .mkString(", ")

  def fastapiImports(operations: List[HttpOperation]): String = {
    val bindings = operations.flatMap(routeParameters).map(_.binding).toSet
    val symbols  =
      List("APIRouter", "Depends") ++
        Option.when(bindings.exists(_.isInstanceOf[HttpInputMemberBinding.PathLabel]))("Path") ++
        Option.when(bindings.exists(_.isInstanceOf[HttpInputMemberBinding.Query]))("Query") ++
        Option.when(bindings.exists(_.isInstanceOf[HttpInputMemberBinding.Header]))("Header")
    s"from fastapi import ${symbols.mkString(", ")}"
  }

  def typingImports(operations: List[HttpOperation]): List[String] = {
    val needsAny =
      operations.flatMap(_.inputMembers).exists(_.pythonTypeName == "Any")
    List("Annotated") ++ Option.when(needsAny)("Any")
  }

  def requiresDatetimeImport(operations: List[HttpOperation]): Boolean =
    operations.flatMap(_.inputMembers).exists(_.pythonTypeName == "datetime")

  private def isRouteParameter(member: HttpOperationInputMember): Boolean =
    member.binding match {
      case HttpInputMemberBinding.Payload() => false
      case _                                => true
    }
}
