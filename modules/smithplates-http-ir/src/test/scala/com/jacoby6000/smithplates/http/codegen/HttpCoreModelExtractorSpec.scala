package com.jacoby6000.smithplates.http.codegen

import com.jacoby6000.smithplates.codegen.core.*
import com.jacoby6000.smithplates.codegen.core.NeutralType.*
import com.jacoby6000.smithplates.http.*
import com.jacoby6000.smithplates.http.model.*
import com.jacoby6000.smithplates.smithy.neutral.LegacyNeutralTypeEquivalence
import com.jacoby6000.smithplates.smithy.neutral.ModelSetClosureAssertions
import munit.FunSuite

import java.nio.file.Files
import java.nio.file.Paths

class HttpCoreModelExtractorSpec extends FunSuite {
  test("HttpCoreModelExtractor produces structures with NeutralType members and alias closure") {
    val model = widgetServiceModel(includeOptionalNote = false)

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
          assertEquals(input.meta.feature, HttpMeta.HttpRequestMeta())

          val output = modelSet.structures.find(_.id.name == "WidgetOutput").getOrElse {
            fail("expected WidgetOutput structure")
          }
          assertEquals(
            output.meta.feature,
            HttpMeta.HttpResponseMeta(statusCode = 200)
          )

          val operation = services.head.operations.head
          assertEquals(operation.input, Some(ModelRef(input.id)))
          assertEquals(operation.meta.feature.method, "GET")
          assertEquals(operation.meta.feature.uriPattern, "/v1/widgets/{id}")
          assertEquals(operation.meta.feature.successStatus, 200)
          assert(operation.meta.feature.responseVariants.nonEmpty)

          ModelSetClosureAssertions.assertAllModelRefsResolved(modelSet, services)
        }
      )
  }

  test("core extraction member types match legacy HttpStructure IR") {
    val model = widgetServiceModel(includeOptionalNote = true)

    assertLegacyParity(model) { (legacyService, modelSet) =>
      legacyService.structures.foreach { legacyStructure =>
        CoreLegacyParity.assertStructureEquivalent(legacyStructure, modelSet)
      }
    }
  }

  test("core extraction includes unions, enums, and error response metadata") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithplates.codegen.http#httpService
          |use smithy.api#error
          |use smithy.api#http
          |use smithy.api#httpError
          |use smithy.api#httpPayload
          |use smithy.api#readonly
          |use smithy.api#tags
          |
          |enum WidgetState {
          |    ACTIVE = "active"
          |    INACTIVE = "inactive"
          |}
          |
          |intEnum WidgetPriority {
          |    LOW = 1
          |    HIGH = 2
          |}
          |
          |@httpService
          |service WidgetApi {
          |    version: "1"
          |    operations: [ListWidgets, GetWidget]
          |    errors: [WidgetNotFound]
          |}
          |
          |@error("client")
          |@httpError(404)
          |structure WidgetNotFound {
          |    @required
          |    message: String
          |}
          |
          |@tags(["v1_widgets"])
          |@http(method: "GET", uri: "/v1/widgets", code: 200)
          |@readonly
          |operation ListWidgets {
          |    input: Unit
          |    output: WidgetListOutput
          |}
          |
          |@tags(["v1_widgets"])
          |@http(method: "GET", uri: "/v1/widgets/{id}", code: 200)
          |operation GetWidget {
          |    input: GetWidgetInput
          |    output: GetWidget200
          |    errors: [GetWidget404]
          |}
          |
          |structure GetWidgetInput {
          |    @required
          |    @httpLabel
          |    id: String
          |}
          |
          |structure GetWidget200 {
          |    @httpPayload
          |    @required
          |    body: WidgetOutput
          |}
          |
          |structure WidgetOutput {
          |    @required
          |    id: String
          |    state: WidgetState
          |    priority: WidgetPriority
          |}
          |
          |structure WidgetListOutput {
          |    @required
          |    items: WidgetListItems
          |}
          |
          |list WidgetListItems {
          |    member: WidgetVariant
          |}
          |
          |union WidgetVariant {
          |    found: WidgetOutput
          |    missing: WidgetMissing
          |}
          |
          |structure WidgetMissing {
          |    @required
          |    id: String
          |}
          |
          |@error("client")
          |@httpError(404)
          |structure GetWidget404 {
          |    @required
          |    message: String
          |}
          |""".stripMargin
    )

    assertLegacyParity(model) { (legacyService, modelSet) =>
      legacyService.structures.foreach { legacyStructure =>
        CoreLegacyParity.assertStructureEquivalent(legacyStructure, modelSet)
      }

      legacyService.unions.foreach { legacyUnion =>
        val coreUnion = modelSet.unions.find(_.id.name == legacyUnion.name).getOrElse {
          fail(s"expected core union ${legacyUnion.name}")
        }
        legacyUnion.members.zip(coreUnion.members).foreach { case (legacyMember, coreMember) =>
          LegacyNeutralTypeEquivalence.assertEquivalentWithAliases(
            legacyTypeName = legacyMember.typeName,
            legacyOptional = false,
            coreType = coreMember.tpe,
            aliases = modelSet.aliases,
            legacyTimestampFormat = legacyMember.timestampFormat.map(CoreLegacyParity.coreTimestampFormat)
          )
        }
      }

      legacyService.stringEnums.foreach { legacyEnum =>
        val coreEnum = modelSet.enums.find(_.id.name == legacyEnum.name).getOrElse {
          fail(s"expected core string enum ${legacyEnum.name}")
        }
        assertEquals(coreEnum.base, StringT)
        assertEquals(coreEnum.values.map(_.name), legacyEnum.members.map(_.name))
        assertEquals(
          coreEnum.values.map(_.value),
          legacyEnum.members.map(member => PrimitiveLiteral.StringValue(member.value))
        )
      }

      legacyService.intEnums.foreach { legacyEnum =>
        val coreEnum = modelSet.enums.find(_.id.name == legacyEnum.name).getOrElse {
          fail(s"expected core int enum ${legacyEnum.name}")
        }
        assertEquals(coreEnum.base, IntegerT)
        assertEquals(coreEnum.values.map(_.name), legacyEnum.members.map(_.name))
        assertEquals(
          coreEnum.values.map(_.value),
          legacyEnum.members.map(member => PrimitiveLiteral.IntValue(member.value.toLong))
        )
      }

      legacyService.serviceErrors.foreach { legacyError =>
        val coreError = modelSet.structures.find(_.id.name == legacyError.name).getOrElse {
          fail(s"expected core service error structure ${legacyError.name}")
        }
        assertEquals(coreError.meta.feature, HttpMeta.HttpResponseMeta(statusCode = legacyError.statusCode))
      }

      val getWidget404 = modelSet.structures.find(_.id.name == "GetWidget404").getOrElse {
        fail("expected GetWidget404 operation error structure")
      }
      assertEquals(getWidget404.meta.feature, HttpMeta.HttpResponseMeta(statusCode = 404))

      val widgetOutput = modelSet.structures.find(_.id.name == "WidgetOutput").getOrElse {
        fail("expected WidgetOutput payload structure")
      }
      assertEquals(widgetOutput.meta.feature, HttpMeta.HttpResponseMeta(statusCode = 200))

      val getWidgetInput = modelSet.structures.find(_.id.name == "GetWidgetInput").getOrElse {
        fail("expected GetWidgetInput structure")
      }
      assertEquals(getWidgetInput.meta.feature, HttpMeta.HttpRequestMeta())
    }

    HttpCoreModelExtractor
      .extractAndValidate(model)
      .fold(
        errors => fail(errors.toList.map(_.message).mkString("; ")),
        { case (modelSet, services) =>
          val listWidgets = services.head.operations.find(_.id.name == "ListWidgets").getOrElse {
            fail("expected ListWidgets operation")
          }
          assertEquals(listWidgets.input, None)

          val getWidget = services.head.operations.find(_.id.name == "GetWidget").getOrElse {
            fail("expected GetWidget operation")
          }
          assertEquals(
            getWidget.errors.map(_.id.name),
            List("GetWidget404")
          )
          assert(
            getWidget.meta.feature.responseVariants.exists(variant =>
              variant.variantTypeName == "WidgetOutput" && variant.statusCode == 200)
          )
          assert(
            getWidget.meta.feature.responseVariants.exists(variant =>
              variant.variantTypeName == "GetWidget404" && variant.statusCode == 404)
          )

          ModelSetClosureAssertions.assertAllModelRefsResolved(modelSet, services)
        }
      )
  }

  test("core extraction closes aliases referenced only through list members") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithplates.codegen.http#httpService
          |use smithy.api#http
          |use smithy.api#tags
          |
          |@pattern("^[a-z0-9-]+$")
          |string Tag
          |
          |list Tags {
          |    member: Tag
          |}
          |
          |@httpService
          |service WidgetApi {
          |    version: "1"
          |    operations: [ListWidgets]
          |}
          |
          |@tags(["v1_widgets"])
          |@http(method: "GET", uri: "/v1/widgets", code: 200)
          |operation ListWidgets {
          |    input: Unit
          |    output: WidgetListOutput
          |}
          |
          |structure WidgetListOutput {
          |    @required
          |    tags: Tags
          |}
          |""".stripMargin
    )

    HttpCoreModelExtractor
      .extractAndValidate(model)
      .fold(
        errors => fail(errors.toList.map(_.message).mkString("; ")),
        { case (modelSet, services) =>
          val tagAlias = modelSet.aliases.find(_.id.name == "Tag").getOrElse {
            fail("expected Tag alias in model set closure")
          }
          assertEquals(tagAlias.underlying, StringT)

          val output = modelSet.structures.find(_.id.name == "WidgetListOutput").getOrElse {
            fail("expected WidgetListOutput structure")
          }
          assertEquals(output.fields.head.tpe, ListT(ModelRef(tagAlias.id)))

          ModelSetClosureAssertions.assertAllModelRefsResolved(modelSet, services)
        }
      )
  }

  test("core extraction matches legacy IR for golden operation-errors fixture") {
    val smithySource =
      Files.readString(
        Paths
          .get(sys.props.getOrElse("user.dir", "."))
          .resolve("templates/python/tests/http-fastapi-operation-errors-api/smithy/smithy-files.smithy")
      )
    val model        = HttpTestModelLoader.assemble("golden.smithy" -> smithySource)

    assertLegacyParity(model) { (legacyService, modelSet) =>
      legacyService.structures.foreach { legacyStructure =>
        CoreLegacyParity.assertStructureEquivalent(legacyStructure, modelSet)
      }

      val getWidget404 = modelSet.structures.find(_.id.name == "GetWidget404").getOrElse {
        fail("expected GetWidget404 in golden fixture model set")
      }
      assertEquals(getWidget404.meta.feature, HttpMeta.HttpResponseMeta(statusCode = 404))
    }
  }

  test("extract runs the same validation pipeline as extractAndValidate") {
    val model     = widgetServiceModel(includeOptionalNote = false)
    val validated =
      HttpCoreModelExtractor
        .extractAndValidate(model)
        .fold(errors => fail(errors.toList.map(_.message).mkString("; ")), identity)
    val extracted =
      HttpCoreModelExtractor
        .extract(model)
        .fold(errors => fail(errors.toList.map(_.message).mkString("; ")), identity)
    assertEquals(extracted, validated)
  }

  test("extract rejects invalid HTTP status codes from service error shapes") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithplates.codegen.http#httpService
          |use smithy.api#error
          |use smithy.api#http
          |use smithy.api#httpError
          |use smithy.api#tags
          |
          |@httpService
          |service WidgetApi {
          |    version: "1"
          |    operations: [GetWidget]
          |    errors: [BadError]
          |}
          |
          |@error("client")
          |@httpError(99)
          |structure BadError {
          |    @required
          |    message: String
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
          |    id: String
          |}
          |
          |structure WidgetOutput {
          |    @required
          |    id: String
          |}
          |""".stripMargin
    )

    HttpCoreModelExtractor
      .extract(model)
      .fold(
        errors => assert(errors.exists(_.isInstanceOf[InvalidModelMeta])),
        _ => fail("expected invalid HTTP status validation failure")
      )
  }

  test("core extraction closes aliases reachable only from input-only operation structures") {
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
          |string RequestId
          |
          |@httpService
          |service SearchApi {
          |    version: "1"
          |    operations: [Search]
          |}
          |
          |@tags(["search"])
          |@http(method: "GET", uri: "/search/{id}", code: 204)
          |operation Search {
          |    input: SearchInput
          |    output: Unit
          |}
          |
          |structure SearchInput {
          |    @required
          |    @httpLabel
          |    id: RequestId
          |}
          |""".stripMargin
    )

    HttpCoreModelExtractor
      .extract(model)
      .fold(
        errors => fail(errors.toList.map(_.message).mkString("; ")),
        { case (modelSet, services) =>
          val requestId = modelSet.aliases.find(_.id.name == "RequestId").getOrElse {
            fail("expected RequestId alias in model set closure")
          }
          assertEquals(requestId.underlying, StringT)

          val searchInput = modelSet.structures.find(_.id.name == "SearchInput").getOrElse {
            fail("expected SearchInput structure")
          }
          assertEquals(searchInput.meta.feature, HttpMeta.HttpRequestMeta())
          assertEquals(searchInput.fields.head.tpe, ModelRef(requestId.id))
          assertEquals(services.head.meta.feature.modelNamespaces.get("SearchInput"), Some("example"))

          ModelSetClosureAssertions.assertAllModelRefsResolved(modelSet, services)
        }
      )
  }

  test("core extraction maps sparse list members to ListT(OptionalT(...))") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithplates.codegen.http#httpService
          |use smithy.api#http
          |use smithy.api#sparse
          |use smithy.api#tags
          |
          |@sparse
          |list SparseTags {
          |    member: String
          |}
          |
          |@httpService
          |service WidgetApi {
          |    version: "1"
          |    operations: [ListWidgets]
          |}
          |
          |@tags(["v1_widgets"])
          |@http(method: "GET", uri: "/v1/widgets", code: 200)
          |operation ListWidgets {
          |    input: Unit
          |    output: WidgetListOutput
          |}
          |
          |structure WidgetListOutput {
          |    @required
          |    tags: SparseTags
          |}
          |""".stripMargin
    )

    HttpCoreModelExtractor
      .extract(model)
      .fold(
        errors => fail(errors.toList.map(_.message).mkString("; ")),
        { case (modelSet, _) =>
          val output = modelSet.structures.find(_.id.name == "WidgetListOutput").getOrElse {
            fail("expected WidgetListOutput structure")
          }
          assertEquals(output.fields.head.tpe, ListT(OptionalT(StringT)))
        }
      )
  }

  test("core extraction preserves timestamp formats on members") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithplates.codegen.http#httpService
          |use smithy.api#http
          |use smithy.api#tags
          |use smithy.api#timestampFormat
          |
          |@timestampFormat("epoch-seconds")
          |timestamp EpochTs
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
          |    id: String
          |}
          |
          |structure WidgetOutput {
          |    @required
          |    @timestampFormat("date-time")
          |    createdAt: Timestamp
          |
          |    @required
          |    updatedAt: EpochTs
          |}
          |""".stripMargin
    )

    HttpCoreModelExtractor
      .extract(model)
      .fold(
        errors => fail(errors.toList.map(_.message).mkString("; ")),
        { case (modelSet, _) =>
          val output    = modelSet.structures.find(_.id.name == "WidgetOutput").getOrElse {
            fail("expected WidgetOutput structure")
          }
          val createdAt = output.fields.find(_.name == "createdAt").getOrElse {
            fail("expected createdAt field")
          }
          val updatedAt = output.fields.find(_.name == "updatedAt").getOrElse {
            fail("expected updatedAt field")
          }
          assertEquals(LegacyNeutralTypeEquivalence.timestampFormat(createdAt.tpe), Some(TimestampFormat.DateTime))
          assertEquals(LegacyNeutralTypeEquivalence.timestampFormat(updatedAt.tpe), Some(TimestampFormat.EpochSeconds))
        }
      )
  }

  test("core extraction assigns response header bindings to output response meta") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithplates.codegen.http#httpService
          |use smithy.api#http
          |use smithy.api#httpHeader
          |use smithy.api#tags
          |
          |@httpService
          |service AssetApi {
          |    version: "1"
          |    operations: [GetAssetContent]
          |}
          |
          |@tags(["assets"])
          |@http(method: "GET", uri: "/assets/{id}/content", code: 302)
          |operation GetAssetContent {
          |    input: GetAssetContentInput
          |    output: Redirect
          |}
          |
          |structure GetAssetContentInput {
          |    @required
          |    @httpLabel
          |    id: String
          |}
          |
          |structure Redirect {
          |    @httpHeader("Location")
          |    @required
          |    url: String
          |}
          |""".stripMargin
    )

    HttpCoreModelExtractor
      .extract(model)
      .fold(
        errors => fail(errors.toList.map(_.message).mkString("; ")),
        { case (modelSet, _) =>
          val redirect = modelSet.structures.find(_.id.name == "Redirect").getOrElse {
            fail("expected Redirect output structure")
          }
          assertEquals(
            redirect.meta.feature,
            HttpMeta.HttpResponseMeta(
              statusCode = 302,
              dynamicHeaderFields = Map("url" -> "Location")
            )
          )

          val input = modelSet.structures.find(_.id.name == "GetAssetContentInput").getOrElse {
            fail("expected GetAssetContentInput structure")
          }
          assertEquals(input.meta.feature, HttpMeta.HttpRequestMeta())
        }
      )
  }

  test("core extraction maps @default members to OptionalT") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithplates.codegen.http#httpService
          |use smithy.api#http
          |use smithy.api#tags
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
          |    id: String
          |
          |    @default("anonymous")
          |    label: String
          |}
          |
          |structure WidgetOutput {
          |    @required
          |    id: String
          |}
          |""".stripMargin
    )

    HttpCoreModelExtractor
      .extract(model)
      .fold(
        errors => fail(errors.toList.map(_.message).mkString("; ")),
        { case (modelSet, services) =>
          val coreInput = modelSet.structures.find(_.id.name == "GetWidgetInput").getOrElse {
            fail("expected core GetWidgetInput structure")
          }
          val coreLabel = coreInput.fields.find(_.name == "label").getOrElse {
            fail("expected core label field")
          }
          assertEquals(coreLabel.tpe, OptionalT(StringT))
          ModelSetClosureAssertions.assertAllModelRefsResolved(modelSet, services)
        }
      )
  }

  test("core extraction assigns @httpProblem bindings to operation error response meta") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithplates.codegen.http#httpService
          |use smithplates.codegen.http#httpProblem
          |use smithy.api#error
          |use smithy.api#http
          |use smithy.api#httpError
          |use smithy.api#tags
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
          |    errors: [GetWidget404]
          |}
          |
          |structure GetWidgetInput {
          |    @required
          |    @httpLabel
          |    id: String
          |}
          |
          |structure WidgetOutput {
          |    @required
          |    id: String
          |}
          |
          |@httpProblem(
          |    type: "https://example.com/errors/widget-not-found"
          |    title: "Widget not found"
          |)
          |@error("client")
          |@httpError(404)
          |structure GetWidget404 {
          |    @required
          |    message: String
          |}
          |""".stripMargin
    )

    HttpCoreModelExtractor
      .extract(model)
      .fold(
        errors => fail(errors.toList.map(_.message).mkString("; ")),
        { case (modelSet, _) =>
          val errorStructure = modelSet.structures.find(_.id.name == "GetWidget404").getOrElse {
            fail("expected GetWidget404 structure")
          }
          assertEquals(
            errorStructure.meta.feature,
            HttpMeta.HttpResponseMeta(
              statusCode = 404,
              staticHeaders = Map("Content-Type" -> "application/problem+json"),
              error = Some(
                HttpErrorMeta(
                  problemType = Some("https://example.com/errors/widget-not-found"),
                  title = Some("Widget not found")
                )
              )
            )
          )
        }
      )
  }

  test("core extraction assigns request static headers to HttpRequestMeta") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithplates.codegen.http#httpService
          |use smithplates.codegen.http#httpStaticHeader
          |use smithy.api#http
          |use smithy.api#tags
          |
          |@httpStaticHeader(name: "X-Request-Kind", value: "widget-read")
          |structure GetWidgetInput {
          |    @required
          |    @httpLabel
          |    id: String
          |}
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
          |structure WidgetOutput {
          |    @required
          |    id: String
          |}
          |""".stripMargin
    )

    HttpCoreModelExtractor
      .extract(model)
      .fold(
        errors => fail(errors.toList.map(_.message).mkString("; ")),
        { case (modelSet, _) =>
          val input = modelSet.structures.find(_.id.name == "GetWidgetInput").getOrElse {
            fail("expected GetWidgetInput structure")
          }
          assertEquals(
            input.meta.feature,
            HttpMeta.HttpRequestMeta(staticHeaders = Map("X-Request-Kind" -> "widget-read"))
          )
        }
      )
  }

  test("HttpCoreMetaValidator rejects invalid HTTP status codes on response models") {
    import HttpCoreMetaValidator.given
    import HttpCoreModelExtractor.given_ModelSetValidator_HttpMeta
    import HttpCoreModelExtractor.given_ServiceModelValidator_HttpServiceMeta_HttpOperationMeta

    val invalidModel: Model.Structure[HttpMeta]                   = Model.Structure(
      id = ModelId("example", "Bad"),
      meta = ModelMeta(
        documentation = None,
        tags = Nil,
        feature = HttpMeta.HttpResponseMeta(statusCode = 99)
      ),
      fields = Nil
    )
    val modelSet: ModelSet[HttpMeta]                              = ModelSet(List(invalidModel))
    val service: ServiceModel[HttpServiceMeta, HttpOperationMeta] =
      ServiceModel(
        id = ModelId("example", "Api"),
        meta = ServiceMeta(documentation = None, tags = Nil, feature = HttpServiceMeta()),
        operations = Nil
      )

    summon[ModelMetaValidator[HttpMeta]]
      .validate(invalidModel)
      .fold(
        errors => assert(errors.exists(_.isInstanceOf[InvalidModelMeta])),
        _ => fail("expected invalid model meta")
      )

    SystemValidator
      .default[HttpMeta, HttpServiceMeta, HttpOperationMeta]
      .validate(modelSet, service)
      .fold(
        errors => assert(errors.exists(_.isInstanceOf[InvalidModelMeta])),
        _ => fail("expected system validation failure")
      )
  }

  test("extract propagates HttpServiceExtractor failures as InvalidSmithyShape") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithplates.codegen.http#httpService
          |use smithy.api#error
          |use smithy.api#http
          |use smithy.api#tags
          |
          |@httpService
          |service WidgetApi {
          |    version: "1"
          |    operations: [GetWidget]
          |    errors: [WidgetNotFound]
          |}
          |
          |@error("client")
          |structure WidgetNotFound {
          |    @required
          |    message: String
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
          |    id: String
          |}
          |
          |structure WidgetOutput {
          |    @required
          |    id: String
          |}
          |""".stripMargin
    )

    HttpCoreModelExtractor
      .extract(model)
      .fold(
        errors => assert(errors.exists(_.isInstanceOf[InvalidSmithyShape])),
        _ => fail("expected HttpServiceExtractor failure")
      )
  }

  private def widgetServiceModel(includeOptionalNote: Boolean): software.amazon.smithy.model.Model =
    if (includeOptionalNote) {
      HttpTestModelLoader.assemble(
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
            |
            |    note: String
            |}
            |
            |structure WidgetOutput {
            |    @required
            |    id: WidgetId
            |}
            |""".stripMargin
      )
    } else {
      HttpTestModelLoader.assemble(
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
    }

  private def assertLegacyParity(
      model: software.amazon.smithy.model.Model
  )(assertions: (HttpService, ModelSet[HttpMeta]) => Unit): Unit = {
    val legacyService =
      HttpIrExtractor
        .extract(model)
        .fold(errors => fail(errors.toList.map(_.message).mkString("; ")), _.services.head)

    HttpCoreModelExtractor
      .extractAndValidate(model)
      .fold(
        errors => fail(errors.toList.map(_.message).mkString("; ")),
        { case (modelSet, services) =>
          assertions(legacyService, modelSet)
          val serviceMeta = services.head.meta.feature
          assertEquals(serviceMeta.version, legacyService.version)
          assertEquals(serviceMeta.title, legacyService.title)
          assertEquals(
            serviceMeta.serviceErrors.map(error => (error.name, error.statusCode)),
            legacyService.serviceErrors.map(error => (error.name, error.statusCode))
          )
          ModelSetClosureAssertions.assertAllModelRefsResolved(modelSet, services)
        }
      )
  }
}

/** Shared helpers for comparing legacy HTTP IR to core extraction output. */
private object CoreLegacyParity extends munit.Assertions {
  def coreTimestampFormat(format: HttpTimestampFormat): TimestampFormat =
    format match {
      case HttpTimestampFormat.DateTime     => TimestampFormat.DateTime
      case HttpTimestampFormat.EpochSeconds => TimestampFormat.EpochSeconds
      case HttpTimestampFormat.HttpDate     => TimestampFormat.DateTime
      case HttpTimestampFormat.Default      => TimestampFormat.DateTime
    }

  def assertStructureEquivalent(legacyStructure: HttpStructure, modelSet: ModelSet[HttpMeta]): Unit = {
    val coreStructure = modelSet.structures.find(_.id.name == legacyStructure.name).getOrElse {
      fail(s"expected core structure ${legacyStructure.name}")
    }
    legacyStructure.members.zip(coreStructure.fields).foreach { case (legacyMember, coreField) =>
      LegacyNeutralTypeEquivalence.assertEquivalentWithAliases(
        legacyTypeName = legacyMember.typeName,
        legacyOptional = !legacyMember.required,
        coreType = coreField.tpe,
        aliases = modelSet.aliases,
        legacyTimestampFormat = legacyMember.timestampFormat.map(coreTimestampFormat)
      )
    }
  }
}
