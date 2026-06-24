package com.jacoby6000.smithplates.smithy.neutral

import cats.data.Validated
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
      structureId: String,
      memberName: String
  ): software.amazon.smithy.model.shapes.MemberShape =
    model
      .expectShape(ShapeId.from(structureId))
      .asStructureShape
      .get()
      .getMember(memberName)
      .get()
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
