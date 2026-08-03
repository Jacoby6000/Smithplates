package com.jacoby6000.smithplates.http.service.renderer

import com.jacoby6000.smithplates.codegen.core.Model
import com.jacoby6000.smithplates.codegen.core.ModelId
import com.jacoby6000.smithplates.codegen.core.ModelMeta
import com.jacoby6000.smithplates.codegen.core.NeutralType.StringT
import com.jacoby6000.smithplates.codegen.core.OperationMeta
import com.jacoby6000.smithplates.codegen.core.OperationModel
import com.jacoby6000.smithplates.codegen.core.ServiceMeta
import com.jacoby6000.smithplates.codegen.core.ServiceModel
import com.jacoby6000.smithplates.codegen.core.planning.CodegenPlanner
import com.jacoby6000.smithplates.codegen.core.planning.TemplateView
import com.jacoby6000.smithplates.codegen.core.strategy.Conventions
import com.jacoby6000.smithplates.codegen.core.strategy.NamingConvention
import com.jacoby6000.smithplates.codegen.core.strategy.NamingStrategy
import com.jacoby6000.smithplates.http.codegen.HttpInputMemberBindingMeta
import com.jacoby6000.smithplates.http.codegen.HttpMeta
import com.jacoby6000.smithplates.http.codegen.HttpOperationBodyBindingMeta
import com.jacoby6000.smithplates.http.codegen.HttpOperationInputMemberMeta
import com.jacoby6000.smithplates.http.codegen.HttpOperationMeta
import com.jacoby6000.smithplates.http.codegen.HttpResponseVariantMeta
import com.jacoby6000.smithplates.http.codegen.HttpServiceMeta
import com.jacoby6000.smithplates.http.model.HttpTimestampFormat
import munit.FunSuite

class HttpNeutralRouteGroupTemplateAttributesSpec extends FunSuite {
  private val R = HttpNeutralRouteGroupTemplateAttributes

  private val conventions =
    Conventions.fromStrategy(
      NamingStrategy(
        fileNames = NamingConvention.SnakeCase.withSuffix(".py"),
        packageSeparator = ".",
        classNames = NamingConvention.Unchanged,
        packageNames = NamingConvention.Unchanged,
        valueNames = NamingConvention.SnakeCase,
        constantNames = NamingConvention.ScreamingSnakeCase,
        functionNames = NamingConvention.SnakeCase,
        reservedKeywordRemaps = Map("id" -> "id_")
      ),
      rootNamespace = Some("generated")
    )

  private def modelMeta: ModelMeta[HttpMeta] = ModelMeta(None, Nil, HttpMeta.HttpNestedField)

  private def routeGroupView(
      operations: List[OperationModel[HttpOperationMeta]],
      usedTypes: List[Model[HttpMeta]] = Nil,
      modelNamespaces: Map[String, String] = Map.empty
  ): R.RouteGroupView =
    TemplateView(
      subject = CodegenPlanner.internal.OperationGroupSubject(
        tag = "v1_widgets",
        service = ServiceModel(
          id = ModelId("example", "WidgetApi"),
          meta = ServiceMeta(
            None,
            Nil,
            HttpServiceMeta(version = "1", modelNamespaces = modelNamespaces)
          ),
          operations = operations
        ),
        operations = operations
      ),
      usedTypes = usedTypes,
      conventions = conventions
    )

  private def operation(
      name: String,
      feature: HttpOperationMeta
  ): OperationModel[HttpOperationMeta] =
    OperationModel(
      id = ModelId("example", name),
      meta = OperationMeta(None, List("v1_widgets"), feature),
      input = None,
      output = None,
      errors = Nil
    )

  test("routeGroupNeedsDatetimeImport is true only for DateTime timestamp members") {
    val withDatetime =
      operation(
        "Inspect",
        HttpOperationMeta(
          method = "GET",
          uriPattern = "/widgets",
          successStatus = 200,
          inputMembers = List(
            HttpOperationInputMemberMeta(
              "since",
              "Timestamp",
              Some(HttpTimestampFormat.DateTime),
              required = false,
              HttpInputMemberBindingMeta.Query("since")
            )
          )
        )
      )
    val withEpoch    =
      operation(
        "Inspect",
        HttpOperationMeta(
          method = "GET",
          uriPattern = "/widgets",
          successStatus = 200,
          inputMembers = List(
            HttpOperationInputMemberMeta(
              "since",
              "Timestamp",
              Some(HttpTimestampFormat.EpochSeconds),
              required = false,
              HttpInputMemberBindingMeta.Query("since")
            )
          )
        )
      )

    assert(R.routeGroupNeedsDatetimeImport(routeGroupView(List(withDatetime))))
    assert(!R.routeGroupNeedsDatetimeImport(routeGroupView(List(withEpoch))))
  }

  test("operationImportedModelNames collects enums, nested lists, and response variants") {
    val widgetStatus: Model[HttpMeta] = Model.EnumModel(ModelId("example", "WidgetStatus"), modelMeta, StringT, Nil)
    val widgetOutput: Model[HttpMeta] = Model.Structure(ModelId("example", "WidgetOutput"), modelMeta, Nil)
    val imageAsset: Model[HttpMeta]   = Model.Structure(ModelId("media", "ImageAsset"), modelMeta, Nil)
    val inspect                       =
      operation(
        "InspectWidget",
        HttpOperationMeta(
          method = "GET",
          uriPattern = "/widgets/{id}",
          successStatus = 200,
          inputMembers = List(
            HttpOperationInputMemberMeta(
              "status",
              "WidgetStatus",
              None,
              required = false,
              HttpInputMemberBindingMeta.Query("status")
            ),
            HttpOperationInputMemberMeta(
              "attachments",
              "List[ImageAsset]",
              None,
              required = false,
              HttpInputMemberBindingMeta.Query("attachments")
            )
          ),
          responseVariants = List(
            HttpResponseVariantMeta("WidgetOutput", 200),
            HttpResponseVariantMeta("WidgetNotFound", 404)
          )
        )
      )
    val view                          =
      routeGroupView(
        List(inspect),
        usedTypes = List(widgetStatus, widgetOutput, imageAsset),
        modelNamespaces = Map("WidgetStatus" -> "example")
      )

    assertEquals(
      R.operationImportedModelNames(view, inspect),
      List("ImageAsset", "WidgetNotFound", "WidgetOutput", "WidgetStatus")
    )
  }

  test("modelTypeImportModule resolves usedTypes, modelNamespaces, and package fallback") {
    val asset: Model[HttpMeta] = Model.Structure(ModelId("other", "Asset"), modelMeta, Nil)
    val view                   =
      routeGroupView(
        Nil,
        usedTypes = List(asset),
        modelNamespaces = Map("WidgetStatus" -> "example")
      )

    assertEquals(R.modelTypeImportModule(view, "Asset"), "generated.other.asset")
    assertEquals(R.modelTypeImportModule(view, "WidgetStatus"), "generated.example.widget_status")
    assertEquals(R.modelTypeImportModule(view, "UnknownShape"), "generated.example.unknown_shape")
  }

  test("modelTypeImportModule resolves operation-only shapes from modelNamespaces") {
    val view =
      routeGroupView(
        Nil,
        usedTypes = Nil,
        modelNamespaces = Map("SearchInput" -> "example")
      )

    assertEquals(R.modelTypeImportModule(view, "SearchInput"), "generated.example.search_input")
  }

  test("route parameter naming follows language conventions including reserved keywords") {
    val view = routeGroupView(Nil)

    assertEquals(R.routeParameterName(view, "traceId"), "trace_id")
    assertEquals(R.routeParameterName(view, "id"), "id_")
    assertEquals(R.operationMethodName(view, "CreateWidget"), "create_widget")
  }

  test("response and protocol return types handle empty success variants") {
    val deleteWidget =
      operation(
        "DeleteWidget",
        HttpOperationMeta(
          method = "DELETE",
          uriPattern = "/widgets/{id}",
          successStatus = 204,
          responseVariants = List(
            HttpResponseVariantMeta("__empty__", 204),
            HttpResponseVariantMeta("WidgetNotFound", 404)
          )
        )
      )

    assertEquals(R.responseTypeName(deleteWidget), "None")
    assertEquals(R.operationProtocolReturnType(deleteWidget), "WidgetNotFound | None")
    assertEquals(R.clientMethodReturnType(deleteWidget), "WidgetNotFound | None")
  }

  test("client request helpers build URL, headers, and JSON arguments") {
    val createWidget =
      operation(
        "CreateWidget",
        HttpOperationMeta(
          method = "POST",
          uriPattern = "/widgets/{region}",
          successStatus = 201,
          inputMembers = List(
            HttpOperationInputMemberMeta(
              "region",
              "String",
              None,
              required = true,
              HttpInputMemberBindingMeta.PathLabel
            ),
            HttpOperationInputMemberMeta(
              "traceId",
              "String",
              None,
              required = false,
              HttpInputMemberBindingMeta.Header("X-Trace")
            ),
            HttpOperationInputMemberMeta(
              "tenantId",
              "String",
              None,
              required = true,
              HttpInputMemberBindingMeta.Query("tenant")
            ),
            HttpOperationInputMemberMeta(
              "preview",
              "Boolean",
              None,
              required = false,
              HttpInputMemberBindingMeta.Query("preview")
            )
          ),
          bodyBinding = HttpOperationBodyBindingMeta.Document("CreateWidgetInput"),
          responseVariants = List(HttpResponseVariantMeta("WidgetOutput", 201))
        )
      )
    val view         = routeGroupView(List(createWidget))

    assertEquals(
      R.clientRequestUrlExpression(view, createWidget),
      "f\"{self._base_url}/widgets/{_encoded_region}\""
    )
    assertEquals(
      R.clientRequestPathLabelsBlock(view, createWidget),
      "        _encoded_region = _encode_path_label(region)")
    assert(
      R.clientRequestHeadersBlock(view, createWidget)
        .contains(
          """if trace_id is not None:
            |            headers["X-Trace"] = str(trace_id)""".stripMargin
        )
    )
    assertEquals(
      R.clientRequestJsonArgument(view, createWidget),
      """, json=create_widget_input.model_dump(mode="json", exclude_none=True)"""
    )
    assertEquals(
      R.clientRequestQueryParamsBlock(view, createWidget),
      """        query_params: list[tuple[str, str]] = []
        |        query_params.append(
        |            ("tenant", _serialize_query_value(tenant_id))
        |        )
        |        if preview is not None:
        |            query_params.append(
        |                ("preview", _serialize_query_value(preview))
        |            )
        |        query_string = urlencode(query_params, quote_via=quote)
        |        request_url = f"{self._base_url}/widgets/{_encoded_region}"
        |        if query_string:
        |            request_url = f"{request_url}?{query_string}"""".stripMargin
    )
  }

  test("operationServiceCallExpression formats multi-argument service calls") {
    val createWidget =
      operation(
        "CreateWidget",
        HttpOperationMeta(
          method = "POST",
          uriPattern = "/widgets",
          successStatus = 201,
          inputMembers = List(
            HttpOperationInputMemberMeta("a", "String", None, required = true, HttpInputMemberBindingMeta.Query("a")),
            HttpOperationInputMemberMeta("b", "String", None, required = true, HttpInputMemberBindingMeta.Query("b")),
            HttpOperationInputMemberMeta("c", "String", None, required = true, HttpInputMemberBindingMeta.Query("c"))
          ),
          bodyBinding = HttpOperationBodyBindingMeta.Document("CreateWidgetInput")
        )
      )
    val view         = routeGroupView(List(createWidget))
    val expression   = R.operationServiceCallExpression(view, createWidget)

    assert(expression.contains("await services.v1_widgets_api.create_widget("))
    assert(expression.contains("a=a"))
    assert(expression.contains("create_widget_input=create_widget_input"))
  }

  test("NestedDocument body binding flattens payload target as body and wraps in input shape") {
    val createWidget =
      operation(
        "CreateWidget",
        HttpOperationMeta(
          method = "POST",
          uriPattern = "/widgets",
          successStatus = 201,
          bodyBinding = HttpOperationBodyBindingMeta.NestedDocument(
            inputShapeName = "CreateWidgetInput",
            payloadMemberName = "body",
            payloadTargetShapeName = "WidgetCreateRequest"
          ),
          responseVariants = List(HttpResponseVariantMeta("WidgetSummary", 201))
        )
      )
    val view         = routeGroupView(List(createWidget))

    assertEquals(R.documentBodyParameter(view, createWidget), Some(("body", "WidgetCreateRequest")))
    assertEquals(R.documentBodyInputShape(createWidget), Some("CreateWidgetInput"))
    assertEquals(
      R.operationImportedModelNames(view, createWidget).sorted,
      List("CreateWidgetInput", "WidgetCreateRequest", "WidgetSummary")
    )

    val expr = R.operationServiceCallExpression(view, createWidget)
    assert(expr.contains("create_widget_input=CreateWidgetInput(body=body)"))

    val params = R.clientMethodParameters(view, createWidget)
    assertEquals(params, List("body: WidgetCreateRequest"))

    assertEquals(
      R.clientRequestJsonArgument(view, createWidget),
      """, json=body.model_dump(mode="json", exclude_none=True)"""
    )
  }

  test("binding presence helpers reflect mixed route input members") {
    val mixed =
      operation(
        "Mixed",
        HttpOperationMeta(
          method = "POST",
          uriPattern = "/widgets/{id}",
          successStatus = 200,
          inputMembers = List(
            HttpOperationInputMemberMeta("id", "String", None, required = true, HttpInputMemberBindingMeta.PathLabel),
            HttpOperationInputMemberMeta("q", "String", None, required = false, HttpInputMemberBindingMeta.Query("q")),
            HttpOperationInputMemberMeta("h", "String", None, required = false, HttpInputMemberBindingMeta.Header("H"))
          ),
          bodyBinding = HttpOperationBodyBindingMeta.Members(
            List(
              HttpOperationInputMemberMeta(
                "payload",
                "String",
                None,
                required = true,
                HttpInputMemberBindingMeta.Payload
              )
            )
          )
        )
      )
    val view  = routeGroupView(List(mixed))

    assert(R.routeGroupUsesPathBinding(view))
    assert(R.routeGroupUsesQueryBinding(view))
    assert(R.routeGroupUsesHeaderBinding(view))
    assert(R.routeGroupHasBody(view))
  }
}
