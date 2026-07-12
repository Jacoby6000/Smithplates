package com.jacoby6000.smithplates.http.service.renderer

import com.jacoby6000.smithplates.codegen.core.EnumValue
import com.jacoby6000.smithplates.codegen.core.Field
import com.jacoby6000.smithplates.codegen.core.Model
import com.jacoby6000.smithplates.codegen.core.ModelId
import com.jacoby6000.smithplates.codegen.core.ModelMeta
import com.jacoby6000.smithplates.codegen.core.NeutralType
import com.jacoby6000.smithplates.codegen.core.NeutralType.*
import com.jacoby6000.smithplates.codegen.core.PrimitiveLiteral
import com.jacoby6000.smithplates.codegen.core.TimestampFormat
import com.jacoby6000.smithplates.codegen.core.planning.TemplateView
import com.jacoby6000.smithplates.codegen.core.strategy.Conventions
import com.jacoby6000.smithplates.codegen.core.strategy.NamingConvention
import com.jacoby6000.smithplates.codegen.core.strategy.NamingStrategy
import com.jacoby6000.smithplates.codegen.core.strategy.TypeRenderer
import com.jacoby6000.smithplates.codegen.core.strategy.config.ConfigurableTypeRenderer
import com.jacoby6000.smithplates.codegen.core.strategy.config.TypeSyntaxConfig
import com.jacoby6000.smithplates.http.codegen.HttpErrorMeta
import com.jacoby6000.smithplates.http.codegen.HttpMeta
import munit.FunSuite

class HttpNeutralModelTemplateAttributesSpec extends FunSuite {
  private val H = HttpNeutralModelTemplateAttributes

  private val conventions: Conventions =
    Conventions.fromStrategy(
      NamingStrategy(
        fileNames = NamingConvention.SnakeCase.withSuffix(".py"),
        packageSeparator = ".",
        classNames = NamingConvention.Unchanged,
        packageNames = NamingConvention.Unchanged,
        valueNames = NamingConvention.SnakeCase,
        constantNames = NamingConvention.ScreamingSnakeCase,
        functionNames = NamingConvention.SnakeCase
      )
    )

  private def meta: ModelMeta[HttpMeta] =
    ModelMeta(None, Nil, HttpMeta.HttpNestedField)

  private def id(name: String): ModelId =
    ModelId("example", name)

  private val widget    = Model.Structure(id("Widget"), meta, Nil)
  private val itemId    = Model.Alias(id("ItemId"), meta, StringT)
  // Alias-to-alias chain is not producible from Smithy extraction, but the type
  // model allows it; this guards renderType's use of TypeResolver.underlying.
  private val uuid      = Model.Alias(id("Uuid"), meta, StringT)
  private val uuid2     = Model.Alias(id("Uuid2"), meta, ModelRef(id("Uuid")))
  private val usedTypes = List(widget, itemId, uuid, uuid2)

  private def pythonTypeRenderer: TypeRenderer =
    ConfigurableTypeRenderer(
      TypeSyntaxConfig(
        primitives = Map(
          "boolean"    -> "bool",
          "integer"    -> "int",
          "long"       -> "int",
          "bigInteger" -> "int",
          "float"      -> "float",
          "double"     -> "float",
          "bigDecimal" -> "Decimal",
          "string"     -> "str",
          "bytes"      -> "bytes",
          "document"   -> "object"
        ),
        timestamp = Map("dateTime" -> "datetime", "epochSeconds" -> "float"),
        optional = "{inner} | None",
        list = "list[{element}]",
        map = "dict[{key}, {value}]",
        modelRef = "{name}"
      )
    )

  private def view[S](subject: S): TemplateView[S, HttpMeta] =
    TemplateView(
      subject = subject,
      usedTypes = usedTypes,
      conventions = conventions,
      typeRenderer = pythonTypeRenderer
    )

  private def render(tpe: NeutralType): String =
    H.renderType(tpe, view(widget))

  test("renderType maps every primitive arm to its Python type") {
    assertEquals(render(BooleanT), "bool")
    assertEquals(render(IntegerT), "int")
    assertEquals(render(LongT), "int")
    assertEquals(render(BigIntegerT), "int")
    assertEquals(render(FloatT), "float")
    assertEquals(render(DoubleT), "float")
    assertEquals(render(BigDecimalT), "Decimal")
    assertEquals(render(StringT), "str")
    assertEquals(render(BytesT), "bytes")
    assertEquals(render(DocumentT), "object")
    assertEquals(render(TimestampT(TimestampFormat.EpochSeconds)), "float")
    assertEquals(render(TimestampT(TimestampFormat.DateTime)), "datetime")
  }

  test("renderType composes optional, list, and map wrappers") {
    assertEquals(render(OptionalT(StringT)), "str | None")
    assertEquals(render(ListT(IntegerT)), "list[int]")
    assertEquals(render(MapT(StringT, IntegerT)), "dict[str, int]")
    assertEquals(render(ListT(OptionalT(StringT))), "list[str | None]")
    assertEquals(render(OptionalT(ListT(MapT(StringT, DoubleT)))), "list[dict[str, float]] | None")
  }

  test("renderType resolves a model reference to its class name") {
    assertEquals(render(ModelRef(id("Widget"))), "Widget")
  }

  test("renderType renders a non-model reference to its class name when unresolved") {
    assertEquals(render(ModelRef(id("Unknown"))), "Unknown")
  }

  test("renderType follows a single-level alias to its underlying primitive") {
    assertEquals(render(ModelRef(id("ItemId"))), "str")
  }

  test("renderType follows an alias chain to the underlying primitive") {
    assertEquals(render(ModelRef(id("Uuid2"))), "str")
  }

  test("enumBaseClass distinguishes int enums from string enums") {
    val intEnum =
      Model.EnumModel(id("Priority"), meta, IntegerT, List(EnumValue("HIGH", PrimitiveLiteral.IntValue(1))))
    val strEnum =
      Model.EnumModel(id("Status"), meta, StringT, List(EnumValue("OPEN", PrimitiveLiteral.StringValue("open"))))

    assertEquals(H.enumBaseClass(view(intEnum)), "IntEnum")
    assertEquals(H.enumBaseClass(view(strEnum)), "StrEnum")
  }

  test("enumValueLiteral renders int literals verbatim and quotes string literals") {
    assertEquals(H.enumValueLiteral(EnumValue("HIGH", PrimitiveLiteral.IntValue(42))), "42")
    assertEquals(H.enumValueLiteral(EnumValue("NEG", PrimitiveLiteral.IntValue(-1))), "-1")
    assertEquals(H.enumValueLiteral(EnumValue("OPEN", PrimitiveLiteral.StringValue("open"))), "\"open\"")
  }

  test("enumValueLiteral escapes Python string metacharacters") {
    val raw      = "a\"b\\c\nd\re\tf"
    val expected = "\"a\\\"b\\\\c\\nd\\re\\tf\""
    assertEquals(H.enumValueLiteral(EnumValue("ODD", PrimitiveLiteral.StringValue(raw))), expected)
  }

  test("isOptional and fieldDefault track OptionalT wrapping") {
    assert(H.isOptional(OptionalT(StringT)))
    assert(!H.isOptional(StringT))
    assertEquals(H.fieldDefault(Field("a", OptionalT(StringT))), "default=None")
    assertEquals(H.fieldDefault(Field("b", StringT)), "...")
  }

  test("unionVariantTypeName combines the union class name with the capitalized member name") {
    val union = Model.Union(id("Payload"), meta, Nil)
    assertEquals(H.unionVariantTypeName(view(union), "text"), "PayloadText")
  }

  test("structureNeedsDatetimeImport walks NeutralType for DateTime timestamps only") {
    val withDatetime       = Model.Structure(
      id("Event"),
      meta,
      List(Field("at", TimestampT(TimestampFormat.DateTime)))
    )
    val withEpoch          = Model.Structure(
      id("Event"),
      meta,
      List(Field("at", TimestampT(TimestampFormat.EpochSeconds)))
    )
    val withNestedDatetime = Model.Structure(
      id("Event"),
      meta,
      List(Field("tags", ListT(OptionalT(TimestampT(TimestampFormat.DateTime)))))
    )
    val withModelRefOnly   = Model.Structure(
      id("Event"),
      meta,
      List(Field("widget", ModelRef(id("Widget"))))
    )

    assert(H.structureNeedsDatetimeImport(view(withDatetime)))
    assert(!H.structureNeedsDatetimeImport(view(withEpoch)))
    assert(H.structureNeedsDatetimeImport(view(withNestedDatetime)))
    assert(!H.structureNeedsDatetimeImport(view(withModelRefOnly)))
  }

  test("problemDefaultFields emits detail default from HttpErrorMeta") {
    val errorMeta                            = HttpMeta.HttpResponseMeta(
      statusCode = 404,
      staticHeaders = Map.empty,
      dynamicHeaderFields = Map.empty,
      error = Some(
        HttpErrorMeta(
          problemType = Some("https://example.com/errors/widget-not-found"),
          title = Some("Widget not found"),
          defaultDetail = Some("The requested widget does not exist.")
        )
      )
    )
    val structure: Model.Structure[HttpMeta] =
      Model.Structure(id("WidgetNotFound"), ModelMeta(None, Nil, errorMeta), Nil)

    assertEquals(
      H.problemDefaultFields(view(structure)),
      List(
        "type"   -> "\"https://example.com/errors/widget-not-found\"",
        "title"  -> "\"Widget not found\"",
        "detail" -> "\"The requested widget does not exist.\""
      )
    )
  }
}
