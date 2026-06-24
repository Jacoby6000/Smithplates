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

          val operation = services.head.operations.head
          assertEquals(operation.input, Some(ModelRef(input.id)))
          assertEquals(
            operation.meta.feature,
            HttpOperationMeta(method = "GET", uriPattern = "/v1/widgets/{id}", successStatus = 200)
          )

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
