package com.jacoby6000.smithplates.http.service.renderer

import com.jacoby6000.smithplates.codegen.core.ModelId
import com.jacoby6000.smithplates.codegen.core.OperationMeta
import com.jacoby6000.smithplates.codegen.core.OperationModel
import com.jacoby6000.smithplates.codegen.core.ServiceMeta
import com.jacoby6000.smithplates.codegen.core.ServiceModel
import com.jacoby6000.smithplates.codegen.core.planning.TemplateView
import com.jacoby6000.smithplates.codegen.core.strategy.Conventions
import com.jacoby6000.smithplates.codegen.core.strategy.NamingConvention
import com.jacoby6000.smithplates.codegen.core.strategy.NamingStrategy
import com.jacoby6000.smithplates.http.codegen.HttpOperationMeta
import com.jacoby6000.smithplates.http.codegen.HttpServiceMeta
import munit.FunSuite

class HttpNeutralServiceTemplateAttributesSpec extends FunSuite {
  private val conventions =
    Conventions.fromStrategy(
      NamingStrategy(
        fileNames = NamingConvention.SnakeCase.withSuffix(".py"),
        packageSeparator = ".",
        classNames = NamingConvention.Unchanged,
        packageNames = NamingConvention.Unchanged,
        valueNames = NamingConvention.SnakeCase,
        constantNames = NamingConvention.ScreamingSnakeCase,
        functionNames = NamingConvention.SnakeCase
      ),
      rootNamespace = Some("generated")
    )

  private def serviceView(
      operations: List[OperationModel[HttpOperationMeta]]): HttpNeutralServiceTemplateAttributes.ServiceView =
    TemplateView(
      subject = ServiceModel(
        id = ModelId("example", "AssetApi"),
        meta = ServiceMeta(None, Nil, HttpServiceMeta()),
        operations = operations
      ),
      usedTypes = Nil,
      conventions = conventions
    )

  test("routeGroupTags groups operations by their first Smithy tag") {
    val view =
      serviceView(
        List(
          operation("ListAssets", List("assets")),
          operation("GetWidget", List("widgets")),
          operation("Health", Nil)
        )
      )
    assertEquals(
      HttpNeutralServiceTemplateAttributes.routeGroupTags(view),
      List("assets", "default", "widgets")
    )
  }

  test("naming helpers match HTTP preamble conventions") {
    assertEquals(HttpNeutralServiceTemplateAttributes.apiModuleName("v1_widgets"), "v1_widgets_api")
    assertEquals(
      HttpNeutralServiceTemplateAttributes.protocolClassName("v1_widgets"),
      "V1WidgetsApiServiceProtocol"
    )
    assertEquals(HttpNeutralServiceTemplateAttributes.clientModuleName("v1_widgets"), "v1_widgets_client")
    assertEquals(HttpNeutralServiceTemplateAttributes.clientClassName("v1_widgets"), "V1WidgetsApiClient")
  }

  private def operation(name: String, tags: List[String]): OperationModel[HttpOperationMeta] =
    OperationModel(
      id = ModelId("example", name),
      meta = OperationMeta(None, tags, HttpOperationMeta(method = "GET", uriPattern = "/x", successStatus = 200)),
      input = None,
      output = None,
      errors = Nil
    )
}
