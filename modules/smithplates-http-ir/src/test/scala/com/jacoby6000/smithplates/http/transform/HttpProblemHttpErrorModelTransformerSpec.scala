package com.jacoby6000.smithplates.http.transform

import com.jacoby6000.smithplates.http.HttpTestModelLoader
import com.jacoby6000.smithplates.http.traits.HttpServiceTrait
import munit.FunSuite
import software.amazon.smithy.build.ProjectionTransformer
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.HttpErrorTrait

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

class HttpProblemHttpErrorModelTransformerSpec extends FunSuite {
  test("SPI registers applyHttpProblemHttpError transform") {
    val factory = ProjectionTransformer.createServiceFactory(getClass.getClassLoader)
    assert(factory.apply(ApplyHttpProblemHttpErrorTransformer.Name).isPresent)
  }

  test("adds @httpError from @httpProblem(code) when @httpError is absent") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithplates.codegen.http#httpProblem
          |use smithy.api#error
          |
          |@error("client")
          |@httpProblem(title: "Widget not found", code: 404)
          |structure NotFound {}
          |""".stripMargin
    )

    val transformed = HttpProblemHttpErrorModelTransformer.transform(model)
    val shapeId     = ShapeId.fromParts("example", "NotFound")
    val shape       = transformed.getShape(shapeId).toScala.get

    assertEquals(shape.getTrait(classOf[HttpErrorTrait]).toScala.map(_.getCode), Some(404))
  }

  test("leaves existing @httpError unchanged when @httpProblem(code) is also present") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithplates.codegen.http#httpProblem
          |use smithy.api#error
          |use smithy.api#httpError
          |
          |@error("client")
          |@httpProblem(title: "Widget not found", code: 404)
          |@httpError(500)
          |structure NotFound {}
          |""".stripMargin
    )

    val transformed = HttpProblemHttpErrorModelTransformer.transform(model)
    val shapeId     = ShapeId.fromParts("example", "NotFound")
    val shape       = transformed.getShape(shapeId).toScala.get

    assertEquals(shape.getTrait(classOf[HttpErrorTrait]).toScala.map(_.getCode), Some(500))
  }

  test("does not add @httpError when @httpProblem omits code") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |use smithplates.codegen.http#httpProblem
          |use smithy.api#error
          |
          |@error("client")
          |@httpProblem(title: "Widget not found")
          |structure NotFound {}
          |""".stripMargin
    )

    val transformed = HttpProblemHttpErrorModelTransformer.transform(model)
    val shapeId     = ShapeId.fromParts("example", "NotFound")
    val shape       = transformed.getShape(shapeId).toScala.get

    assert(shape.getTrait(classOf[HttpErrorTrait]).isEmpty)
  }

  test("transform does not duplicate @httpService traits on services") {
    val model = HttpTestModelLoader.assemble(
      "example.smithy" ->
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
    )

    val transformed = HttpProblemHttpErrorModelTransformer.transform(model)
    val serviceId   = ShapeId.fromParts("example", "Demo")
    val service     = transformed.getShape(serviceId).toScala.get

    assertEquals(
      service.getAllTraits.values().stream().filter(_.toShapeId == HttpServiceTrait.ID).count(),
      1L
    )
  }

  test("re-importing sources after transform conflicts on unchanged @httpService traits") {
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

    val model         = HttpTestModelLoader.assemble("example.smithy" -> smithySource)
    val transformed   = HttpProblemHttpErrorModelTransformer.transform(model)
    val serviceBefore = model.getShape(ShapeId.fromParts("example", "Demo")).toScala.get
    val serviceAfter  = transformed.getShape(ShapeId.fromParts("example", "Demo")).toScala.get
    val traitBefore   = serviceBefore.getTrait(classOf[HttpServiceTrait]).toScala.get
    val traitAfter    = serviceAfter.getTrait(classOf[HttpServiceTrait]).toScala.get
    assertEquals(traitBefore.getSourceLocation, traitAfter.getSourceLocation)

    val mergeResult =
      Model.assembler().addModel(transformed).addUnparsedModel("example.smithy", smithySource).assemble()
    val messages    = mergeResult.getValidationEvents.asScala.map(_.getMessage).mkString("\n")
    assert(mergeResult.isBroken && messages.contains("Conflicting"), messages)
  }
}
