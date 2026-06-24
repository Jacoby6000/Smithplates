package com.jacoby6000.smithplates.codegen.core.strategy.config

import com.jacoby6000.smithplates.codegen.core.*
import com.jacoby6000.smithplates.codegen.core.NeutralType.*
import com.jacoby6000.smithplates.codegen.core.strategy.RenderContext
import munit.FunSuite

import java.nio.file.Paths

class LanguageBaseConfigSpec extends FunSuite {
  private lazy val pythonConfig =
    LanguageBaseConfigLoader
      .loadLanguageTemplateRoot(pythonTemplateRoot)
      .fold(errors => fail(errors.toList.map(_.message).mkString("; ")), identity)

  private lazy val pythonTemplateRoot =
    Paths.get(System.getProperty("user.dir")).resolve("templates/python")

  private lazy val ctx =
    RenderContext(
      typeResolver = TypeResolver.fromModelSet(ModelSet(Nil)),
      conventions = pythonConfig.conventions(Some("generated"))
    )

  test("bundled python base_config.json loads successfully") {
    assertEquals(pythonConfig.namingStrategy.fileNames.suffix, ".py")
    assertEquals(pythonConfig.typeSyntax.primitives("string"), "str")
  }

  test("python naming conventions from base_config.json") {
    val conventions = pythonConfig.conventions(Some("generated"))
    assertEquals(conventions.fileName(ModelId("example", "WidgetRepository")), "widget_repository.py")
    assertEquals(conventions.memberName("parentNodeId"), "parent_node_id")
    assertEquals(conventions.functionName("GetWidget"), "get_widget")
    assertEquals(conventions.memberName("class"), "class_")
    assertEquals(conventions.memberName("from"), "from_")
    assertEquals(conventions.memberName("id"), "id_")
    assertEquals(conventions.className(ModelId("example", "WidgetOutput")), "WidgetOutput")
    assertEquals(conventions.packageName("example.api"), "generated.example.api")
  }

  test("python type renderer from base_config.json matches bundled SSP pythonTypeName helper") {
    val renderer = pythonConfig.typeRenderer
    assertEquals(renderer.render(StringT, ctx), "str")
    assertEquals(renderer.render(IntegerT, ctx), "int")
    assertEquals(renderer.render(LongT, ctx), "int")
    assertEquals(renderer.render(BigIntegerT, ctx), "int")
    assertEquals(renderer.render(FloatT, ctx), "float")
    assertEquals(renderer.render(DoubleT, ctx), "float")
    assertEquals(renderer.render(BigDecimalT, ctx), "Decimal")
    assertEquals(renderer.render(BooleanT, ctx), "bool")
    assertEquals(renderer.render(BytesT, ctx), "bytes")
    assertEquals(renderer.render(DocumentT, ctx), "object")
    assertEquals(renderer.render(TimestampT(TimestampFormat.DateTime), ctx), "datetime")
    assertEquals(renderer.render(TimestampT(TimestampFormat.EpochSeconds), ctx), "float")
    assertEquals(renderer.render(OptionalT(StringT), ctx), "str | None")
    assertEquals(renderer.render(ListT(StringT), ctx), "list[str]")
    assertEquals(renderer.render(MapT(StringT, IntegerT), ctx), "dict[str, int]")
    assertEquals(renderer.render(ListT(OptionalT(StringT)), ctx), "list[str | None]")
    assertEquals(renderer.render(ModelRef(ModelId("example", "WidgetOutput")), ctx), "WidgetOutput")
  }

  test("legacy string type names round-trip through configured type rendering") {
    val renderer = pythonConfig.typeRenderer
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
        renderer.render(neutralType, ctx),
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
