package com.jacoby6000.smithplates.http.transform

import com.jacoby6000.smithplates.http.HttpTestModelLoader
import munit.FunSuite
import software.amazon.smithy.build.ProjectionTransformer
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.HttpErrorTrait

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
}
