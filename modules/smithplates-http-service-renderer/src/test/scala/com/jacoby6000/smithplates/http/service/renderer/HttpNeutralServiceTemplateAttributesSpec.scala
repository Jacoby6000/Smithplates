package com.jacoby6000.smithplates.http.service.renderer

import com.jacoby6000.smithplates.codegen.core.ModelId
import com.jacoby6000.smithplates.codegen.core.NeutralType.ModelRef
import com.jacoby6000.smithplates.codegen.core.OperationMeta
import com.jacoby6000.smithplates.codegen.core.OperationModel
import com.jacoby6000.smithplates.codegen.core.ServiceMeta
import com.jacoby6000.smithplates.codegen.core.ServiceModel
import com.jacoby6000.smithplates.codegen.core.planning.TemplateView
import com.jacoby6000.smithplates.codegen.core.strategy.Conventions
import com.jacoby6000.smithplates.codegen.core.strategy.NamingConvention
import com.jacoby6000.smithplates.codegen.core.strategy.NamingStrategy
import com.jacoby6000.smithplates.http.codegen.HttpErrorMeta
import com.jacoby6000.smithplates.http.codegen.HttpOperationMeta
import com.jacoby6000.smithplates.http.codegen.HttpResponseVariantMeta
import com.jacoby6000.smithplates.http.codegen.HttpServiceErrorMeta
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
      operations: List[OperationModel[HttpOperationMeta]],
      serviceErrors: List[HttpServiceErrorMeta] = Nil): HttpNeutralServiceTemplateAttributes.ServiceView =
    TemplateView(
      subject = ServiceModel(
        id = ModelId("example", "AssetApi"),
        meta = ServiceMeta(None, Nil, HttpServiceMeta(serviceErrors = serviceErrors)),
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

  test("operation binding helpers mirror HTTP preamble literals") {
    val getWidget =
      operation("GetWidget", List("v1_widgets")).copy(
        meta = OperationMeta(
          None,
          List("v1_widgets"),
          HttpOperationMeta(
            method = "GET",
            uriPattern = "/v1/widgets/{id}",
            successStatus = 200,
            responseVariants = List(
              HttpResponseVariantMeta(
                variantTypeName = "WidgetOutput",
                statusCode = 200,
                mediaType = Some("application/json"),
                headerBindings = List(("url", "Location")),
                staticHeaders = List(("X-Request-Id", "abc"))
              )
            )
          )
        )
      )
    val view      = serviceView(List(getWidget))
    assertEquals(
      HttpNeutralServiceTemplateAttributes.operationBindingKeys(view, getWidget),
      List("get_widget", "GetWidget")
    )
    assertEquals(
      HttpNeutralServiceTemplateAttributes.responseVariantMediaType(Some("application/json")),
      "'application/json'"
    )
  }

  test("service error helpers follow HTTP preamble naming") {
    val view =
      serviceView(
        Nil,
        List(
          HttpServiceErrorMeta(
            id = ModelId("example", "WidgetNotFound"),
            statusCode = 404,
            error = Some(HttpErrorMeta(problemType = Some("about:blank"), title = Some("Not found")))
          )
        )
      )
    assertEquals(HttpNeutralServiceTemplateAttributes.serviceErrors(view).map(_.name), List("WidgetNotFound"))
    assert(HttpNeutralServiceTemplateAttributes.serviceErrorsNeedProblemImport(view))
    assertEquals(
      HttpNeutralServiceTemplateAttributes.serviceErrorHandlerName(view, "WidgetNotFound"),
      "handle_widget_not_found_api_error"
    )
    assertEquals(
      HttpNeutralServiceTemplateAttributes.routerImportAlias("v1_widgets_api"),
      "V1WidgetsApiRouter"
    )
  }

  test("responseModelTypeNames collects operation output and error shapes") {
    val view =
      serviceView(
        List(
          operation("ListAssets", List("assets"))
            .copy(output = Some(ModelRef(ModelId("example", "AssetList")))),
          operation("GetAsset", List("assets"))
            .copy(
              output = Some(ModelRef(ModelId("example", "Asset"))),
              errors = List(ModelRef(ModelId("example", "NotFound"))))
        )
      )
    assertEquals(
      HttpNeutralServiceTemplateAttributes.responseModelTypeNames(view),
      List("Asset", "AssetList", "NotFound")
    )
    assertEquals(
      HttpNeutralServiceTemplateAttributes.modelTypeImportModule(view, "Asset"),
      "generated.example.asset"
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
