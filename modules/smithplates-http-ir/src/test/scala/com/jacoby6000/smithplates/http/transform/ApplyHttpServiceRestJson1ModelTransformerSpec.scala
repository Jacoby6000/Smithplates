package com.jacoby6000.smithplates.http.transform

import com.jacoby6000.smithplates.http.HttpTestModelLoader
import com.jacoby6000.smithplates.http.traits.HttpServiceTrait
import munit.FunSuite
import software.amazon.smithy.aws.traits.protocols.RestJson1Trait
import software.amazon.smithy.build.ProjectionTransformer
import software.amazon.smithy.model.shapes.ShapeId

import scala.jdk.OptionConverters.*

class ApplyHttpServiceRestJson1ModelTransformerSpec extends FunSuite {
  test("SPI registers applyHttpServiceRestJson1 transform") {
    val factory = ProjectionTransformer.createServiceFactory(getClass.getClassLoader)
    assert(factory.apply(ApplyHttpServiceRestJson1Transformer.Name).isPresent)
  }

  test("adds @restJson1 to @httpService services") {
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

    val transformed = ApplyHttpServiceRestJson1ModelTransformer.transform(model)
    val serviceId   = ShapeId.fromParts("example", "Demo")
    val service     = transformed.getShape(serviceId).toScala.get

    assert(service.getTrait(classOf[RestJson1Trait]).isPresent)
    assert(service.getTrait(classOf[HttpServiceTrait]).isPresent)
  }

  test("is idempotent when @restJson1 is already present") {
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

    val once  = ApplyHttpServiceRestJson1ModelTransformer.transform(model)
    val twice = ApplyHttpServiceRestJson1ModelTransformer.transform(once)
    assertEquals(twice, once)
  }
}
