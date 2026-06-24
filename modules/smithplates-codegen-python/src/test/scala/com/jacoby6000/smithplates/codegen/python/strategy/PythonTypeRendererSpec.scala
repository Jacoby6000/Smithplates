package com.jacoby6000.smithplates.codegen.python.strategy

import com.jacoby6000.smithplates.codegen.core.*
import com.jacoby6000.smithplates.codegen.core.NeutralType.*
import com.jacoby6000.smithplates.codegen.core.strategy.RenderContext
import munit.FunSuite

class PythonTypeRendererSpec extends FunSuite {
  private val ctx =
    RenderContext(
      typeResolver = TypeResolver.fromModelSet(ModelSet(Nil)),
      conventions = PythonConventions.default()
    )

  test("primitives match bundled SSP pythonTypeName helper") {
    assertEquals(PythonTypeRenderer.render(StringT, ctx), "str")
    assertEquals(PythonTypeRenderer.render(IntegerT, ctx), "int")
    assertEquals(PythonTypeRenderer.render(LongT, ctx), "int")
    assertEquals(PythonTypeRenderer.render(BigIntegerT, ctx), "int")
    assertEquals(PythonTypeRenderer.render(FloatT, ctx), "float")
    assertEquals(PythonTypeRenderer.render(DoubleT, ctx), "float")
    assertEquals(PythonTypeRenderer.render(BigDecimalT, ctx), "Decimal")
    assertEquals(PythonTypeRenderer.render(BooleanT, ctx), "bool")
    assertEquals(PythonTypeRenderer.render(BytesT, ctx), "bytes")
    assertEquals(PythonTypeRenderer.render(DocumentT, ctx), "object")
    assertEquals(PythonTypeRenderer.render(TimestampT(TimestampFormat.DateTime), ctx), "datetime")
  }

  test("epoch timestamps render as float") {
    assertEquals(PythonTypeRenderer.render(TimestampT(TimestampFormat.EpochSeconds), ctx), "float")
  }

  test("optional, list, and map composites") {
    assertEquals(PythonTypeRenderer.render(OptionalT(StringT), ctx), "str | None")
    assertEquals(PythonTypeRenderer.render(ListT(StringT), ctx), "list[str]")
    assertEquals(PythonTypeRenderer.render(MapT(StringT, IntegerT), ctx), "dict[str, int]")
    assertEquals(PythonTypeRenderer.render(ListT(OptionalT(StringT)), ctx), "list[str | None]")
  }

  test("model refs render as Smithy shape names") {
    val widgetRef = ModelRef(ModelId("example", "WidgetOutput"))
    assertEquals(PythonTypeRenderer.render(widgetRef, ctx), "WidgetOutput")
  }

  test("legacy string type names round-trip through NeutralType rendering") {
    val examples = List(
      "String"                    -> StringT,
      "Integer"                   -> IntegerT,
      "Long"                      -> LongT,
      "BigInteger"                -> BigIntegerT,
      "Float"                     -> FloatT,
      "Double"                    -> DoubleT,
      "BigDecimal"                -> BigDecimalT,
      "Boolean"                   -> BooleanT,
      "Blob"                      -> BytesT,
      "Timestamp"                 -> TimestampT(TimestampFormat.DateTime),
      "Document"                  -> DocumentT,
      "List[String]"              -> ListT(StringT),
      "List[WidgetOutput]"        -> ListT(ModelRef(ModelId("example", "WidgetOutput"))),
      "Map[String, Integer]"      -> MapT(StringT, IntegerT),
      "Map[String, WidgetOutput]" -> MapT(StringT, ModelRef(ModelId("example", "WidgetOutput")))
    )

    examples.foreach { case (legacyTypeName, neutralType) =>
      assertEquals(
        PythonTypeRenderer.render(neutralType, ctx),
        LegacyPythonTypeNames.fromLegacyTypeName(legacyTypeName),
        s"legacy type name $legacyTypeName"
      )
    }
  }
}

/** Mirrors `templates/python/src/common/fragments/preamble.ssp` `pythonTypeName` for equivalence tests. */
object LegacyPythonTypeNames {
  def fromLegacyTypeName(typeName: String): String =
    if (typeName.startsWith("List[")) {
      val inner = typeName.substring(5, typeName.length - 1)
      s"list[${fromLegacyTypeName(inner)}]"
    } else if (typeName.startsWith("Map[String, ")) {
      val inner = typeName.substring(12, typeName.length - 1)
      s"dict[str, ${fromLegacyTypeName(inner)}]"
    } else {
      typeName match {
        case "String"                          => "str"
        case "Integer" | "Long" | "BigInteger" => "int"
        case "Float" | "Double"                => "float"
        case "BigDecimal"                      => "Decimal"
        case "Boolean"                         => "bool"
        case "Blob"                            => "bytes"
        case "Timestamp"                       => "datetime"
        case "Document"                        => "object"
        case "Unit"                            => "None"
        case other                             => other
      }
    }
}
