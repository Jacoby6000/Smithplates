package com.jacoby6000.smithplates.http.transform

import com.jacoby6000.smithplates.http.HttpTestModelLoader
import com.jacoby6000.smithplates.http.traits.HttpProblemTrait
import com.jacoby6000.smithplates.http.traits.HttpServiceTrait
import munit.FunSuite
import software.amazon.smithy.build.ProjectionTransformer
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.HttpErrorTrait

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

class StripSmithplatesHttpCodegenTraitsModelTransformerSpec extends FunSuite {
  test("SPI registers stripSmithplatesHttpCodegenTraits transform") {
    val factory = ProjectionTransformer.createServiceFactory(getClass.getClassLoader)
    assert(factory.apply(StripSmithplatesHttpCodegenTraitsTransformer.Name).isPresent)
  }

  test("removes smithplates HTTP codegen traits after implied traits are materialized") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithplates.codegen.http#httpProblem
          |use smithplates.codegen.http#httpService
          |use smithy.api#error
          |use smithy.api#http
          |
          |@error("client")
          |@httpProblem(title: "Widget not found", code: 404)
          |structure NotFound {}
          |
          |@http(method: "GET", uri: "/")
          |operation Get {
          |    input: Unit
          |    output: Unit
          |    errors: [NotFound]
          |}
          |
          |@httpService
          |service Demo {
          |    version: "1"
          |    operations: [Get]
          |}
          |""".stripMargin
    )

    val transformed =
      StripSmithplatesHttpCodegenTraitsModelTransformer.transform(
        HttpProblemHttpErrorModelTransformer.transform(model)
      )

    val serviceId = ShapeId.fromParts("example", "Demo")
    val service   = transformed.getShape(serviceId).toScala.get
    val notFound  = transformed.getShape(ShapeId.fromParts("example", "NotFound")).toScala.get

    assert(service.getTrait(classOf[HttpServiceTrait]).isEmpty)
    assert(notFound.getTrait(classOf[HttpProblemTrait]).isEmpty)
    assertEquals(notFound.getTrait(classOf[HttpErrorTrait]).toScala.map(_.getCode), Some(404))
  }

  test("openapi projection transform chain merges cleanly with original sources") {
    val smithySource =
      """$version: "2.0"
        |namespace example
        |
        |use smithplates.codegen.http#httpService
        |use smithy.api#http
        |
        |@http(method: "GET", uri: "/")
        |operation Get {
        |    input: Unit
        |    output: Unit
        |}
        |
        |@httpService
        |service Demo {
        |    version: "1"
        |    operations: [Get]
        |}
        |""".stripMargin

    val model       = HttpTestModelLoader.assemble("example.smithy" -> smithySource)
    val transformed =
      StripSmithplatesHttpCodegenTraitsModelTransformer.transform(
        ApplyHttpServiceRestJson1ModelTransformer.transform(
          HttpProblemHttpErrorModelTransformer.transform(model)
        )
      )

    val mergeResult =
      Model
        .assembler(getClass.getClassLoader)
        .discoverModels(getClass.getClassLoader)
        .addModel(transformed)
        .addUnparsedModel("example.smithy", smithySource)
        .assemble()
    val messages    = mergeResult.getValidationEvents.asScala.map(_.getMessage).mkString("\n")
    assert(!mergeResult.isBroken, messages)
  }
}
