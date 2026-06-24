package com.jacoby6000.smithplates.smithy.neutral

import cats.data.Validated
import com.jacoby6000.smithplates.codegen.core.InvalidSmithyShape
import com.jacoby6000.smithplates.codegen.core.NeutralType.*
import com.jacoby6000.smithplates.codegen.core.TimestampFormat
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

    val expected = List(
      ShapeId.from("smithy.api#String")    -> StringT,
      ShapeId.from("smithy.api#Integer")   -> IntegerT,
      ShapeId.from("smithy.api#Long")      -> LongT,
      ShapeId.from("smithy.api#Boolean")   -> BooleanT,
      ShapeId.from("smithy.api#Blob")      -> BytesT,
      ShapeId.from("smithy.api#Document")  -> DocumentT,
      ShapeId.from("smithy.api#Timestamp") -> TimestampT(TimestampFormat.DateTime)
    )

    expected.foreach { case (shapeId, neutralType) =>
      val shape = model.expectShape(shapeId)
      assertEquals(
        SmithyNeutralTypeResolver.resolveShapeType(model, shape),
        Validated.validNel(neutralType),
        s"prelude primitive ${shapeId.getName}"
      )
    }
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

  test("resolveShapeType maps sparse list shapes to ListT(OptionalT(...))") {
    val model = SmithyTestModels.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |@sparse
          |list SparseStrings {
          |    member: String
          |}
          |""".stripMargin
    )

    val listShape = model.expectShape(ShapeId.from("example#SparseStrings"))
    assertEquals(
      SmithyNeutralTypeResolver.resolveShapeType(model, listShape),
      Validated.validNel(ListT(OptionalT(StringT)))
    )
  }

  test("resolveMemberType maps sparse list targets to ListT(OptionalT(...))") {
    val model = SmithyTestModels.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |@sparse
          |list Names {
          |    member: String
          |}
          |
          |structure Payload {
          |    @required
          |    tags: Names
          |}
          |""".stripMargin
    )

    val member = memberShape(model, "example#Payload", "tags")
    assertEquals(
      SmithyNeutralTypeResolver.resolveMemberType(
        model,
        member,
        SmithyNeutralTypeResolver.memberOptionalByRequiredTrait,
        SmithyTimestampFormats.defaultFormat,
        SmithyNeutralTypeResolver.MemberContext(
          shapeId = ShapeId.from("example#Payload"),
          memberName = "tags",
          role = "structure"
        )
      ),
      Validated.validNel(ListT(OptionalT(StringT)))
    )
  }

  test("resolveMemberType applies RequiredTrait and DefaultTrait optionality") {
    val model = SmithyTestModels.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |structure Payload {
          |    @required
          |    requiredName: String
          |
          |    @default("anonymous")
          |    defaultedName: String
          |
          |    optionalName: String
          |}
          |""".stripMargin
    )

    val context = SmithyNeutralTypeResolver.MemberContext(
      shapeId = ShapeId.from("example#Payload"),
      memberName = "ignored",
      role = "structure"
    )

    val requiredMember = memberShape(model, "example#Payload", "requiredName")
    assertEquals(
      SmithyNeutralTypeResolver.resolveMemberType(
        model,
        requiredMember,
        SmithyNeutralTypeResolver.memberOptionalByRequiredTrait,
        SmithyTimestampFormats.defaultFormat,
        context.copy(memberName = "requiredName")
      ),
      Validated.validNel(StringT)
    )

    val defaultedMember = memberShape(model, "example#Payload", "defaultedName")
    assertEquals(
      SmithyNeutralTypeResolver.resolveMemberType(
        model,
        defaultedMember,
        SmithyNeutralTypeResolver.memberOptionalByRequiredTrait,
        SmithyTimestampFormats.defaultFormat,
        context.copy(memberName = "defaultedName")
      ),
      Validated.validNel(OptionalT(StringT))
    )

    val optionalMember = memberShape(model, "example#Payload", "optionalName")
    assertEquals(
      SmithyNeutralTypeResolver.resolveMemberType(
        model,
        optionalMember,
        SmithyNeutralTypeResolver.memberOptionalByRequiredTrait,
        SmithyTimestampFormats.defaultFormat,
        context.copy(memberName = "optionalName")
      ),
      Validated.validNel(OptionalT(StringT))
    )
  }

  test("resolveMemberType resolves timestamp formats from member and target traits") {
    val model = SmithyTestModels.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |@timestampFormat("epoch-seconds")
          |timestamp EpochTs
          |
          |structure Payload {
          |    @timestampFormat("date-time")
          |    explicit: Timestamp
          |
          |    inherited: EpochTs
          |}
          |""".stripMargin
    )

    val context = SmithyNeutralTypeResolver.MemberContext(
      shapeId = ShapeId.from("example#Payload"),
      memberName = "ignored",
      role = "structure"
    )

    val explicitMember = memberShape(model, "example#Payload", "explicit")
    assertEquals(
      SmithyNeutralTypeResolver.resolveMemberType(
        model,
        explicitMember,
        SmithyNeutralTypeResolver.memberOptionalByRequiredTrait,
        SmithyTimestampFormats.defaultFormat,
        context.copy(memberName = "explicit")
      ),
      Validated.validNel(OptionalT(TimestampT(TimestampFormat.DateTime)))
    )

    val inheritedMember = memberShape(model, "example#Payload", "inherited")
    assertEquals(
      SmithyNeutralTypeResolver.resolveMemberType(
        model,
        inheritedMember,
        SmithyNeutralTypeResolver.memberOptionalByRequiredTrait,
        SmithyTimestampFormats.defaultFormat,
        context.copy(memberName = "inherited")
      ),
      Validated.validNel(OptionalT(TimestampT(TimestampFormat.EpochSeconds)))
    )
  }

  test("resolveShapeType maps map shapes to MapT(StringT, valueType)") {
    val model = SmithyTestModels.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |map StringMap {
          |    key: String
          |    value: Integer
          |}
          |""".stripMargin
    )

    val mapShape = model.expectShape(ShapeId.from("example#StringMap"))
    assertEquals(
      SmithyNeutralTypeResolver.resolveShapeType(model, mapShape),
      Validated.validNel(MapT(StringT, IntegerT))
    )
  }

  test("resolveShapeType maps aggregate shapes to ModelRef") {
    val model = SmithyTestModels.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |enum Status {
          |    OPEN = "open"
          |}
          |
          |intEnum Priority {
          |    LOW = 1
          |}
          |
          |structure Payload {
          |    status: Status
          |    priority: Priority
          |}
          |
          |union Event {
          |    opened: Payload
          |}
          |""".stripMargin
    )

    val refs = List(
      "example#Payload"  -> ModelRef(ModelIds.fromShapeId(ShapeId.from("example#Payload"))),
      "example#Status"   -> ModelRef(ModelIds.fromShapeId(ShapeId.from("example#Status"))),
      "example#Priority" -> ModelRef(ModelIds.fromShapeId(ShapeId.from("example#Priority"))),
      "example#Event"    -> ModelRef(ModelIds.fromShapeId(ShapeId.from("example#Event")))
    )

    refs.foreach { case (shapeId, expected) =>
      val shape = model.expectShape(ShapeId.from(shapeId))
      assertEquals(
        SmithyNeutralTypeResolver.resolveShapeType(model, shape),
        Validated.validNel(expected),
        shapeId
      )
    }
  }

  test("aliasUnderlying resolves string aliases") {
    val model = SmithyTestModels.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |string WidgetId
          |""".stripMargin
    )

    assertEquals(
      SmithyNeutralTypeResolver.aliasUnderlying(model, ShapeId.from("example#WidgetId")),
      Validated.validNel(StringT)
    )
  }

  test("aliasUnderlying resolves user integer shapes") {
    val model = SmithyTestModels.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |integer Count
          |""".stripMargin
    )

    assertEquals(
      SmithyNeutralTypeResolver.aliasUnderlying(model, ShapeId.from("example#Count")),
      Validated.validNel(IntegerT)
    )
  }

  test("resolveShapeType maps user integer shapes to ModelRef") {
    val model = SmithyTestModels.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |integer Count
          |
          |structure Payload {
          |    @required
          |    total: Count
          |}
          |""".stripMargin
    )

    val countShape = model.expectShape(ShapeId.from("example#Count"))
    assertEquals(
      SmithyNeutralTypeResolver.resolveShapeType(model, countShape),
      Validated.validNel(ModelRef(ModelIds.fromShapeId(ShapeId.from("example#Count"))))
    )
  }

  test("aliasUnderlying rejects non-primitive alias targets") {
    val model = SmithyTestModels.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |structure Payload {
          |    id: String
          |}
          |""".stripMargin
    )

    SmithyNeutralTypeResolver.aliasUnderlying(model, ShapeId.from("example#Payload")) match {
      case Validated.Invalid(errors) =>
        assert(errors.head.isInstanceOf[InvalidSmithyShape])
      case Validated.Valid(_)        =>
        fail("expected invalid alias underlying")
    }
  }

  test("resolveMemberType keeps union members required without RequiredTrait") {
    val model = SmithyTestModels.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |structure Payload {
          |    id: String
          |}
          |
          |union Event {
          |    opened: Payload
          |}
          |""".stripMargin
    )

    val member = memberShape(model, "example#Event", "opened")
    assertEquals(
      SmithyNeutralTypeResolver.resolveMemberType(
        model,
        member,
        _ => false,
        SmithyTimestampFormats.defaultFormat,
        SmithyNeutralTypeResolver.MemberContext(
          shapeId = ShapeId.from("example#Event"),
          memberName = "opened",
          role = "union"
        )
      ),
      Validated.validNel(ModelRef(ModelIds.fromShapeId(ShapeId.from("example#Payload"))))
    )
  }

  test("resolveShapeType maps string enums to ModelRef not StringT") {
    val model = SmithyTestModels.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |enum Status {
          |    OPEN = "open"
          |}
          |""".stripMargin
    )

    val enumShape = model.expectShape(ShapeId.from("example#Status"))
    assert(enumShape.isEnumShape)
    assertEquals(
      SmithyNeutralTypeResolver.resolveShapeType(model, enumShape),
      Validated.validNel(ModelRef(ModelIds.fromShapeId(ShapeId.from("example#Status"))))
    )
  }

  test("SmithyTimestampFormats maps http-date to DateTime") {
    val model = SmithyTestModels.assemble(
      "example.smithy" ->
        """$version: "2.0"
          |namespace example
          |
          |structure Payload {
          |    @timestampFormat("http-date")
          |    when: Timestamp
          |}
          |""".stripMargin
    )

    val member = memberShape(model, "example#Payload", "when")
    assertEquals(
      SmithyTimestampFormats.defaultFormat(model, member),
      Validated.validNel(TimestampFormat.DateTime)
    )
  }

  private def memberShape(
      model: software.amazon.smithy.model.Model,
      containerId: String,
      memberName: String
  ): software.amazon.smithy.model.shapes.MemberShape = {
    val shape = model.expectShape(ShapeId.from(containerId))
    if (shape.isUnionShape) {
      shape.asUnionShape.get().getMember(memberName).get()
    } else {
      shape.asStructureShape.get().getMember(memberName).get()
    }
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
