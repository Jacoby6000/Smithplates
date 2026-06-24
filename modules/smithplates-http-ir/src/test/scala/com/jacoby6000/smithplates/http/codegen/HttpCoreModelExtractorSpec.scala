package com.jacoby6000.smithplates.http.codegen

import com.jacoby6000.smithplates.codegen.core.*
import com.jacoby6000.smithplates.codegen.core.NeutralType.*
import com.jacoby6000.smithplates.http.HttpTestModelLoader
import munit.FunSuite

class HttpCoreModelExtractorSpec extends FunSuite {
  test("HttpCoreModelExtractor produces structures with NeutralType members and alias closure") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithplates.codegen.http#httpService
          |use smithy.api#http
          |use smithy.api#pattern
          |use smithy.api#tags
          |
          |@pattern("^[a-z0-9-]+$")
          |string WidgetId
          |
          |@httpService
          |service WidgetApi {
          |    version: "1"
          |    operations: [GetWidget]
          |}
          |
          |@tags(["v1_widgets"])
          |@http(method: "GET", uri: "/v1/widgets/{id}", code: 200)
          |operation GetWidget {
          |    input: GetWidgetInput
          |    output: WidgetOutput
          |}
          |
          |structure GetWidgetInput {
          |    @required
          |    @httpLabel
          |    id: WidgetId
          |}
          |
          |structure WidgetOutput {
          |    @required
          |    id: WidgetId
          |}
          |""".stripMargin
    )

    HttpCoreModelExtractor
      .extractAndValidate(model)
      .fold(
        errors => fail(errors.toList.map(_.message).mkString("; ")),
        { case (modelSet, services) =>
          assertEquals(services.size, 1)
          assertEquals(services.head.operations.map(_.id.name), List("GetWidget"))

          val widgetId = modelSet.aliases.find(_.id.name == "WidgetId").getOrElse {
            fail("expected WidgetId alias in model set closure")
          }
          assert(widgetId.underlying == StringT, "WidgetId alias should target String")

          val input = modelSet.structures.find(_.id.name == "GetWidgetInput").getOrElse {
            fail("expected GetWidgetInput structure")
          }
          assertEquals(input.fields.map(_.name), List("id"))
          assert(input.fields.head.tpe == ModelRef(widgetId.id), "input id member should reference WidgetId")

          val operation = services.head.operations.head
          assert(operation.input.contains(ModelRef(input.id)), "operation input should reference GetWidgetInput")
        }
      )
  }

  test("SystemValidator rejects unresolved operation refs") {
    val modelSet = ModelSet[HttpMeta](Nil)
    val service  = ServiceModel(
      id = ModelId("example", "Broken"),
      meta = ServiceMeta(None, Nil, HttpServiceMeta()),
      operations = List(
        OperationModel(
          id = ModelId("example", "Op"),
          meta = OperationMeta(None, Nil, HttpOperationMeta("GET", "/x", 200)),
          input = Some(ModelRef(ModelId("example", "Missing"))),
          output = None,
          errors = Nil
        )
      )
    )

    given ModelMetaValidator[HttpMeta]                              = ModelMetaValidator.noop
    given OperationMetaValidator[HttpOperationMeta]                 = OperationMetaValidator.noop
    given ServiceMetaValidator[HttpServiceMeta]                     = ServiceMetaValidator.noop
    given ModelValidator[HttpMeta]                                  = ModelValidator.default
    given OperationValidator[HttpOperationMeta]                     = OperationValidator.default
    given ServiceModelValidator[HttpServiceMeta, HttpOperationMeta] = ServiceModelValidator.default
    given ModelSetValidator[HttpMeta]                               = ModelSetValidator.default

    SystemValidator
      .default[HttpMeta, HttpServiceMeta, HttpOperationMeta]
      .validate(modelSet, service)
      .fold(
        errors => assert(errors.exists(_.isInstanceOf[UnresolvedModelRef]), "expected unresolved operation ref"),
        _ => fail("expected unresolved operation ref validation error")
      )
  }
}
