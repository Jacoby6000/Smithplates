package com.jacoby6000.smithplates.http.service.renderer

import com.jacoby6000.smithplates.http.model.HttpInputMemberBinding
import com.jacoby6000.smithplates.http.model.HttpOperation
import com.jacoby6000.smithplates.http.model.HttpOperationInputMember

object HttpCodegenFastApiAttributes {
  def routeParameters(operation: HttpOperation): List[HttpOperationInputMember] =
    operation.inputMembers.filter(isRouteParameter)

  def routerImportAlias(apiModuleName: String): String =
    apiModuleName
      .split("_")
      .filter(_.nonEmpty)
      .map(segment => s"${segment.head.toUpper}${segment.tail}")
      .mkString + "Router"

  def pythonParameterType(member: HttpOperationInputMember): String =
    if (member.required) {
      member.pythonTypeName
    } else {
      s"${member.pythonTypeName} | None"
    }

  def endpointArgumentDefinition(member: HttpOperationInputMember): String = {
    val parameterName = HttpCodegenTemplateAttributes.toSnakeCase(member.name)
    val binding       = fastapiBindingExpression(member)
    s"$parameterName: ${pythonParameterType(member)} = $binding"
  }

  def implArgumentDefinition(member: HttpOperationInputMember): String = {
    val parameterName = HttpCodegenTemplateAttributes.toSnakeCase(member.name)
    s"$parameterName: ${pythonParameterType(member)}"
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
      List(
        "APIRouter",
        "Body",
        "Depends",
        "Header",
        "Path",
        "Query"
      ).filter { symbol =>
        symbol match {
          case "Body"   => bindings.exists(_.isInstanceOf[HttpInputMemberBinding.Payload])
          case "Header" => bindings.exists(_.isInstanceOf[HttpInputMemberBinding.Header])
          case "Path"   => bindings.exists(_.isInstanceOf[HttpInputMemberBinding.PathLabel])
          case "Query"  => bindings.exists(_.isInstanceOf[HttpInputMemberBinding.Query])
          case _        => true
        }
      }
    s"""from fastapi import (  # noqa: F401
    ${symbols.mkString(",\n    ")},
)"""
  }

  def typingImports(operations: List[HttpOperation]): List[String] = {
    val needsAny =
      operations.flatMap(_.inputMembers).exists(_.pythonTypeName == "Any")
    List("Annotated") ++ Option.when(needsAny)("Any")
  }

  def requiresDatetimeImport(operations: List[HttpOperation]): Boolean =
    operations.flatMap(_.inputMembers).exists(_.pythonTypeName == "datetime")

  def responseTypeNames(operations: List[HttpOperation]): List[String] =
    operations
      .map(HttpOperationBindingAttributes.responseTypeAnnotation)
      .filter(_ != "None")
      .distinct
      .sorted

  def modelImportLine(modelsPackageName: String, typeName: String): String = {
    val moduleName = HttpCodegenTemplateAttributes.toSnakeCase(typeName)
    s"from $modelsPackageName.$moduleName import $typeName"
  }

  private def fastapiBindingExpression(member: HttpOperationInputMember): String =
    member.binding match {
      case HttpInputMemberBinding.PathLabel()        =>
        if (member.required) {
          "Path(...)"
        } else {
          "Path(None)"
        }
      case HttpInputMemberBinding.Query(queryName)   =>
        if (member.required) {
          s"""Query(..., alias="${queryName}")"""
        } else {
          s"""Query(None, alias="${queryName}")"""
        }
      case HttpInputMemberBinding.Header(headerName) =>
        if (member.required) {
          s"""Header(..., alias="${headerName}")"""
        } else {
          s"""Header(None, alias="${headerName}")"""
        }
      case HttpInputMemberBinding.Payload()          =>
        if (member.required) {
          "Body(...)"
        } else {
          "Body(None)"
        }
    }

  private def isRouteParameter(member: HttpOperationInputMember): Boolean =
    member.binding match {
      case HttpInputMemberBinding.Payload() => false
      case _                                => true
    }
}
