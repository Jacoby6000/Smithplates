package com.jacoby6000.smithplates.smithy.neutral

import cats.data.Validated
import com.jacoby6000.smithplates.codegen.core.NeutralType.*
import munit.FunSuite
import software.amazon.smithy.model.shapes.ShapeId

class SmithyNeutralTypeResolverSpec extends FunSuite {
  test("resolveShapeType maps prelude primitives") {
    val model = SmithyTestModels.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |string Name
          |""".stripMargin
    )

    val stringShape = model.expectShape(ShapeId.from("smithy.api#String"))
    assertEquals(
      SmithyNeutralTypeResolver.resolveShapeType(model, stringShape),
      Validated.validNel(StringT)
    )
  }

  test("resolveShapeType maps user string shapes to ModelRef") {
    val model = SmithyTestModels.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |string WidgetId
          |""".stripMargin
    )

    val widgetIdShape = model.expectShape(ShapeId.from("example#WidgetId"))
    assertEquals(
      SmithyNeutralTypeResolver.resolveShapeType(model, widgetIdShape),
      Validated.validNel(ModelRef(ModelIds.fromShapeId(ShapeId.from("example#WidgetId"))))
    )
  }
}

/** Minimal Smithy model loader for smithy-neutral unit tests. */
object SmithyTestModels {
  def assemble(files: (String, String)*): software.amazon.smithy.model.Model = {
    val assembler = software.amazon.smithy.model.Model.assembler()
    files.foreach { case (filename, contents) =>
      assembler.addUnparsedModel(filename, contents)
    }
    assembler
      .assemble()
      .unwrap()
  }
}
