package com.jacoby6000.smithplates.sql

import com.jacoby6000.smithplates.sql.model.*
import munit.Assertions
import munit.FunSuite
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ShapeId

final class SmithyColumnTypeConverterSpec extends FunSuite {
  test("Text - prelude String maps to Text") {
    val model = SmithyColumnTypeConverterSpec.internal.builder.assemble(
      """structure PreludeStringMember {
        |    value: String
        |}""".stripMargin
    )
    SmithyColumnTypeConverterSpec.internal.assertModelColumns(
      model,
      "PreludeStringMember",
      List("value" -> SqlColumnType.Text))
  }

  test("Text - string alias maps to Text") {
    val model = SmithyColumnTypeConverterSpec.internal.modelWithTypeAlias("string")
    SmithyColumnTypeConverterSpec.internal.assertModelColumns(
      model,
      SmithyColumnTypeConverterSpec.internal.AliasStructureName,
      List(SmithyColumnTypeConverterSpec.internal.AliasMemberName -> SqlColumnType.Text))
  }

  test("Uuid - prelude String with @sqlUuid maps to Uuid") {
    val model = SmithyColumnTypeConverterSpec.internal.builder.assemble(
      """use smithplates.codegen.sql#sqlUuid
        |
        |structure PreludeUuidMember {
        |    @sqlUuid
        |    id: String
        |}""".stripMargin
    )
    SmithyColumnTypeConverterSpec.internal.assertModelColumns(
      model,
      "PreludeUuidMember",
      List("id" -> SqlColumnType.Uuid))
  }

  test("Uuid - string alias with @sqlUuid maps to Uuid") {
    val model = SmithyColumnTypeConverterSpec.internal.modelWithTypeAlias("string", aliasTraits = List("@sqlUuid"))
    SmithyColumnTypeConverterSpec.internal.assertModelColumns(
      model,
      SmithyColumnTypeConverterSpec.internal.AliasStructureName,
      List(SmithyColumnTypeConverterSpec.internal.AliasMemberName -> SqlColumnType.Uuid))
  }

  test("Uuid - @sqlAutoUuid without @sqlUuid maps to Uuid") {
    val model = SmithyColumnTypeConverterSpec.internal.builder.assemble(
      """use smithplates.codegen.sql#sqlAutoUuid
        |
        |structure AutoUuidMember {
        |    @sqlAutoUuid
        |    id: String
        |}""".stripMargin
    )
    SmithyColumnTypeConverterSpec.internal.assertModelColumns(model, "AutoUuidMember", List("id" -> SqlColumnType.Uuid))
  }

  test("Uuid -  prelude Integer with @sqlUuid is unsupported") {
    val model = SmithyColumnTypeConverterSpec.internal.builder.assemble(
      """use smithplates.codegen.sql#sqlUuid
        |
        |structure IntegerUuidMember {
        |    @sqlUuid
        |    count: Integer
        |}""".stripMargin
    )
    SmithyColumnTypeConverterSpec.internal.assertUnsupportedMember(
      model,
      "IntegerUuidMember",
      "count",
      InvalidMemberColumnType.Kind.SqlUuid)
  }

  test("Integer - prelude Integer maps to Integer") {
    val model = SmithyColumnTypeConverterSpec.internal.builder.assemble(
      """structure PreludeIntegerMember {
        |    value: Integer
        |}""".stripMargin
    )
    SmithyColumnTypeConverterSpec.internal.assertModelColumns(
      model,
      "PreludeIntegerMember",
      List("value" -> SqlColumnType.Integer))
  }

  test("Integer - integer alias maps to Integer") {
    val model = SmithyColumnTypeConverterSpec.internal.modelWithTypeAlias("integer")
    SmithyColumnTypeConverterSpec.internal.assertModelColumns(
      model,
      SmithyColumnTypeConverterSpec.internal.AliasStructureName,
      List(SmithyColumnTypeConverterSpec.internal.AliasMemberName -> SqlColumnType.Integer))
  }

  test("BigInt - prelude Long maps to BigInt") {
    val model = SmithyColumnTypeConverterSpec.internal.builder.assemble(
      """structure PreludeLongMember {
        |    value: Long
        |}""".stripMargin
    )
    SmithyColumnTypeConverterSpec.internal.assertModelColumns(
      model,
      "PreludeLongMember",
      List("value" -> SqlColumnType.BigInt))
  }

  test("BigInt - long alias maps to BigInt") {
    val model = SmithyColumnTypeConverterSpec.internal.modelWithTypeAlias("long")
    SmithyColumnTypeConverterSpec.internal.assertModelColumns(
      model,
      SmithyColumnTypeConverterSpec.internal.AliasStructureName,
      List(SmithyColumnTypeConverterSpec.internal.AliasMemberName -> SqlColumnType.BigInt))
  }

  test("Timestamp - prelude Timestamp maps to Timestamp") {
    val model = SmithyColumnTypeConverterSpec.internal.builder.assemble(
      """structure PreludeTimestampMember {
        |    value: Timestamp
        |}""".stripMargin
    )
    SmithyColumnTypeConverterSpec.internal.assertModelColumns(
      model,
      "PreludeTimestampMember",
      List("value" -> SqlColumnType.Timestamp(SqlTimestampFormat.DateTime))
    )
  }

  test("Timestamp - timestamp alias maps to Timestamp") {
    val model = SmithyColumnTypeConverterSpec.internal.modelWithTypeAlias("timestamp")
    SmithyColumnTypeConverterSpec.internal.assertModelColumns(
      model,
      SmithyColumnTypeConverterSpec.internal.AliasStructureName,
      List(
        SmithyColumnTypeConverterSpec.internal.AliasMemberName -> SqlColumnType.Timestamp(SqlTimestampFormat.DateTime))
    )
  }

  test("Timestamp - @timestampFormat epoch-seconds on member") {
    val model = SmithyColumnTypeConverterSpec.internal.builder.assemble(
      """use smithy.api#timestampFormat

        |structure EpochSecondsMember {
        |    @timestampFormat("epoch-seconds")
        |    value: Timestamp
        |}""".stripMargin
    )
    SmithyColumnTypeConverterSpec.internal.assertModelColumns(
      model,
      "EpochSecondsMember",
      List("value" -> SqlColumnType.Timestamp(SqlTimestampFormat.EpochSeconds))
    )
  }

  test("Timestamp - @timestampFormat on target shape") {
    val model = SmithyColumnTypeConverterSpec.internal.builder.assemble(
      """use smithy.api#timestampFormat

        |@timestampFormat("epoch-seconds")
        |timestamp EpochSecondsTimestamp

        |structure EpochSecondsAliasMember {
        |    value: EpochSecondsTimestamp
        |}""".stripMargin
    )
    SmithyColumnTypeConverterSpec.internal.assertModelColumns(
      model,
      "EpochSecondsAliasMember",
      List("value" -> SqlColumnType.Timestamp(SqlTimestampFormat.EpochSeconds))
    )
  }

  test("Timestamp - rejects @timestampFormat http-date") {
    val model  = SmithyColumnTypeConverterSpec.internal.builder.assemble(
      """use smithy.api#timestampFormat

        |structure HttpDateMember {
        |    @timestampFormat("http-date")
        |    value: Timestamp
        |}""".stripMargin
    )
    val member = SmithyColumnTypeConverterSpec.internal.memberFromModel(model, "HttpDateMember", "value")
    assertEquals(
      SmithyColumnTypeConverterSpec.internal.converter.fromSmithyMember(model, member),
      Left(
        UnsupportedColumnType(
          member.getTarget,
          InvalidMemberColumnType.Kind.TimestampFormat,
          Some("@timestampFormat \"http-date\" is not supported for SQL columns")
        )
      )
    )
  }

  test("Boolean - prelude Boolean maps to Boolean") {
    val model = SmithyColumnTypeConverterSpec.internal.builder.assemble(
      """structure PreludeBooleanMember {
        |    value: Boolean
        |}""".stripMargin
    )
    SmithyColumnTypeConverterSpec.internal.assertModelColumns(
      model,
      "PreludeBooleanMember",
      List("value" -> SqlColumnType.Boolean))
  }

  test("Boolean - boolean alias maps to Boolean") {
    val model = SmithyColumnTypeConverterSpec.internal.modelWithTypeAlias("boolean")
    SmithyColumnTypeConverterSpec.internal.assertModelColumns(
      model,
      SmithyColumnTypeConverterSpec.internal.AliasStructureName,
      List(SmithyColumnTypeConverterSpec.internal.AliasMemberName -> SqlColumnType.Boolean))
  }

  test("Json - Document maps to Json") {
    val model = SmithyColumnTypeConverterSpec.internal.builder.assemble(
      """structure JsonMembers {
        |    payload: Document
        |}""".stripMargin
    )
    SmithyColumnTypeConverterSpec.internal.assertModelColumns(
      model,
      "JsonMembers",
      List("payload" -> SqlColumnType.Json))
  }

  test("Varchar - prelude String with @sqlVarchar maps to Varchar") {
    val model = SmithyColumnTypeConverterSpec.internal.builder.assemble(
      """use smithplates.codegen.sql#sqlVarchar
        |
        |structure PreludeVarcharMember {
        |    @sqlVarchar(maxLength: 128)
        |    name: String
        |}""".stripMargin
    )
    SmithyColumnTypeConverterSpec.internal.assertModelColumns(
      model,
      "PreludeVarcharMember",
      List("name" -> SqlColumnType.Varchar(128)))
  }

  test("Varchar - string alias with @sqlVarchar maps to Varchar") {
    val model = SmithyColumnTypeConverterSpec.internal.modelWithTypeAlias(
      "string",
      aliasTraits = List("@sqlVarchar(maxLength: 16)"))
    SmithyColumnTypeConverterSpec.internal.assertModelColumns(
      model,
      SmithyColumnTypeConverterSpec.internal.AliasStructureName,
      List(SmithyColumnTypeConverterSpec.internal.AliasMemberName -> SqlColumnType.Varchar(16))
    )
  }

  test("Text - String without @sqlVarchar maps to Text") {
    val model = SmithyColumnTypeConverterSpec.internal.builder.assemble(
      """structure PlainStringMember {
        |    name: String
        |}""".stripMargin
    )
    SmithyColumnTypeConverterSpec.internal.assertModelColumns(
      model,
      "PlainStringMember",
      List("name" -> SqlColumnType.Text))
  }

  test("Varchar - @sqlVarchar on string type alias maps to Varchar") {
    val model = SmithyColumnTypeConverterSpec.internal.modelWithTypeAlias(
      "string",
      aliasTraits = List("@sqlVarchar(maxLength: 36)"))
    SmithyColumnTypeConverterSpec.internal.assertModelColumns(
      model,
      SmithyColumnTypeConverterSpec.internal.AliasStructureName,
      List(SmithyColumnTypeConverterSpec.internal.AliasMemberName -> SqlColumnType.Varchar(36))
    )
  }

  test("Varchar - @sqlVarchar rejected on Integer") {
    val model = SmithyColumnTypeConverterSpec.internal.builder.assemble(
      """use smithplates.codegen.sql#sqlVarchar
        |
        |structure IntegerVarcharMember {
        |    @sqlVarchar(maxLength: 8)
        |    count: Integer
        |}""".stripMargin
    )
    SmithyColumnTypeConverterSpec.internal.assertUnsupportedMember(
      model,
      "IntegerVarcharMember",
      "count",
      InvalidMemberColumnType.Kind.SqlVarchar)
  }

  test("Varchar - @sqlVarchar rejected on Long") {
    val model = SmithyColumnTypeConverterSpec.internal.builder.assemble(
      """use smithplates.codegen.sql#sqlVarchar
        |
        |structure LongVarcharMember {
        |    @sqlVarchar(maxLength: 8)
        |    total: Long
        |}""".stripMargin
    )
    SmithyColumnTypeConverterSpec.internal.assertUnsupportedMember(
      model,
      "LongVarcharMember",
      "total",
      InvalidMemberColumnType.Kind.SqlVarchar)
  }

  test("Varchar - @sqlVarchar rejected on Timestamp") {
    val model = SmithyColumnTypeConverterSpec.internal.builder.assemble(
      """use smithplates.codegen.sql#sqlVarchar
        |
        |structure TimestampVarcharMember {
        |    @sqlVarchar(maxLength: 32)
        |    created_at: Timestamp
        |}""".stripMargin
    )
    SmithyColumnTypeConverterSpec.internal.assertUnsupportedMember(
      model,
      "TimestampVarcharMember",
      "created_at",
      InvalidMemberColumnType.Kind.SqlVarchar)
  }

  test("Varchar - @sqlVarchar rejected on Boolean") {
    val model = SmithyColumnTypeConverterSpec.internal.builder.assemble(
      """use smithplates.codegen.sql#sqlVarchar
        |
        |structure BooleanVarcharMember {
        |    @sqlVarchar(maxLength: 1)
        |    active: Boolean
        |}""".stripMargin
    )
    SmithyColumnTypeConverterSpec.internal.assertUnsupportedMember(
      model,
      "BooleanVarcharMember",
      "active",
      InvalidMemberColumnType.Kind.SqlVarchar)
  }

  test("Varchar - @sqlVarchar rejected on Document") {
    val model = SmithyColumnTypeConverterSpec.internal.builder.assemble(
      """use smithplates.codegen.sql#sqlVarchar
        |
        |structure DocumentVarcharMember {
        |    @sqlVarchar(maxLength: 1024)
        |    payload: Document
        |}""".stripMargin
    )
    SmithyColumnTypeConverterSpec.internal.assertUnsupportedMember(
      model,
      "DocumentVarcharMember",
      "payload",
      InvalidMemberColumnType.Kind.SqlVarchar)
  }

  test("List[String] - is unsupported without @sqlJson") {
    val model = SmithyColumnTypeConverterSpec.internal.builder.assemble(
      """list Names { member: String }
        |
        |structure ListMember {
        |    names: Names
        |}""".stripMargin
    )
    SmithyColumnTypeConverterSpec.internal.assertUnsupportedMember(model, "ListMember", "names")
  }

  test("List[String] - maps to Json with @sqlJson") {
    val model = SmithyColumnTypeConverterSpec.internal.builder.assemble(
      """use smithplates.codegen.sql#sqlJson
        |
        |list Names { member: String }
        |
        |structure ListMember {
        |    @sqlJson
        |    names: Names
        |}""".stripMargin
    )
    SmithyColumnTypeConverterSpec.internal.assertModelColumns(model, "ListMember", List("names" -> SqlColumnType.Json))
  }

  test("Map[String, String] - is unsupported without @sqlJson") {
    val model = SmithyColumnTypeConverterSpec.internal.builder.assemble(
      """map StringMap {
        |    key: String
        |    value: String
        |}
        |
        |structure MapMember {
        |    tags: StringMap
        |}""".stripMargin
    )
    SmithyColumnTypeConverterSpec.internal.assertUnsupportedMember(model, "MapMember", "tags")
  }

  test("Map[String, String] - maps to Json with @sqlJson") {
    val model = SmithyColumnTypeConverterSpec.internal.builder.assemble(
      """use smithplates.codegen.sql#sqlJson
        |
        |map StringMap {
        |    key: String
        |    value: String
        |}
        |
        |structure MapMember {
        |    @sqlJson
        |    tags: StringMap
        |}""".stripMargin
    )
    SmithyColumnTypeConverterSpec.internal.assertModelColumns(model, "MapMember", List("tags" -> SqlColumnType.Json))
  }

  test("Structure - nested structure is unsupported without @sqlJson") {
    val model = SmithyColumnTypeConverterSpec.internal.builder.assemble(
      """structure Nested {
        |    field: String
        |}
        |
        |structure StructureMember {
        |    nested: Nested
        |}""".stripMargin
    )
    SmithyColumnTypeConverterSpec.internal.assertUnsupportedMember(model, "StructureMember", "nested")
  }

  test("Structure - nested structure maps to Json with @sqlJson") {
    val model = SmithyColumnTypeConverterSpec.internal.builder.assemble(
      """use smithplates.codegen.sql#sqlJson
        |
        |structure Nested {
        |    field: String
        |}
        |
        |structure StructureMember {
        |    @sqlJson
        |    nested: Nested
        |}""".stripMargin
    )
    SmithyColumnTypeConverterSpec.internal.assertModelColumns(
      model,
      "StructureMember",
      List("nested" -> SqlColumnType.Json))
  }

  test("Blob - prelude Blob maps to Blob") {
    val model = SmithyColumnTypeConverterSpec.internal.builder.assemble(
      """structure BlobMember {
        |    data: Blob
        |}""".stripMargin
    )
    SmithyColumnTypeConverterSpec.internal.assertModelColumns(model, "BlobMember", List("data" -> SqlColumnType.Blob))
  }

  test("Json - @sqlJson rejected on String") {
    val model = SmithyColumnTypeConverterSpec.internal.builder.assemble(
      """use smithplates.codegen.sql#sqlJson
        |
        |structure StringJsonMember {
        |    @sqlJson
        |    name: String
        |}""".stripMargin
    )
    SmithyColumnTypeConverterSpec.internal.assertUnsupportedMember(
      model,
      "StringJsonMember",
      "name",
      InvalidMemberColumnType.Kind.SqlJson)
  }

  test("Union - maps to Json with @sqlJson") {
    val model = SmithyColumnTypeConverterSpec.internal.builder.assemble(
      """use smithplates.codegen.sql#sqlJson
        |use smithplates.codegen.sql#sqlPrimaryKey
        |use smithplates.codegen.sql#sqlTable
        |
        |union Measurement {
        |    string: String
        |    integer: Integer
        |}
        |
        |@sqlTable(name: "union_members")
        |structure UnionMember {
        |    @sqlPrimaryKey
        |    id: String
        |    @sqlJson
        |    value: Measurement
        |}""".stripMargin
    )
    SmithyColumnTypeConverterSpec.internal.assertModelColumns(
      model,
      "UnionMember",
      List("id" -> SqlColumnType.Text, "value" -> SqlColumnType.Json))
  }

  test("Union - is unsupported on @sqlTable structure without @sqlJson") {
    val model = SmithyColumnTypeConverterSpec.internal.builder.assemble(
      """use smithplates.codegen.sql#sqlPrimaryKey
        |use smithplates.codegen.sql#sqlTable
        |
        |union Measurement {
        |    string: String
        |    integer: Integer
        |}
        |
        |@sqlTable(name: "union_members")
        |structure UnionMember {
        |    @sqlPrimaryKey
        |    id: String
        |    value: Measurement
        |}""".stripMargin
    )

    val member = SmithyColumnTypeConverterSpec.internal.memberFromModel(model, "UnionMember", "value")
    assertEquals(
      member.getTarget,
      ShapeId.from("example#Measurement"),
      "expected member to target the union shape, not a @sqlTable structure"
    )
    SmithyColumnTypeConverterSpec.internal.assertUnsupportedMember(model, "UnionMember", "value")
  }

  test("Enum - enum without assignments maps to StringEnum") {
    val model = SmithyColumnTypeConverterSpec.internal.builder.assemble(
      """enum Direction {
        |    NORTH
        |    SOUTH
        |}
        |
        |structure EnumMembers {
        |    direction: Direction
        |}""".stripMargin
    )
    SmithyColumnTypeConverterSpec.internal.assertModelColumns(
      model,
      "EnumMembers",
      List(
        "direction" ->
          SqlColumnType.StringEnum(ShapeId.from("example#Direction"), "example_direction", List("NORTH", "SOUTH"))
      )
    )
  }

  test("Enum - enum with string assignments maps to StringEnum") {
    val model = SmithyColumnTypeConverterSpec.internal.builder.assemble(
      """enum LabeledDirection {
        |    NORTH = "north"
        |    SOUTH = "south"
        |}
        |
        |structure LabeledEnumMembers {
        |    direction: LabeledDirection
        |}""".stripMargin
    )
    SmithyColumnTypeConverterSpec.internal.assertModelColumns(
      model,
      "LabeledEnumMembers",
      List(
        "direction" ->
          SqlColumnType.StringEnum(
            ShapeId.from("example#LabeledDirection"),
            "example_labeleddirection",
            List("north", "south")
          )
      )
    )
  }

  test("Enum - intEnum maps to IntEnum") {
    val model = SmithyColumnTypeConverterSpec.internal.builder.assemble(
      """intEnum HttpStatus {
        |    OK = 200
        |    NOT_FOUND = 404
        |}
        |
        |structure StatusMember {
        |    status: HttpStatus
        |}""".stripMargin
    )
    SmithyColumnTypeConverterSpec.internal.assertModelColumns(
      model,
      "StatusMember",
      List("status" -> SqlColumnType.IntEnum("example_httpstatus", List(404, 200)))
    )
  }
}

object SmithyColumnTypeConverterSpec {

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    val converter: ColumnTypeConverter = SmithyColumnTypeConverter
    val builder                        = SqlTestModelBuilder

    val AliasStructureName = "AliasMember"
    val AliasMemberName    = "value"
    val AliasTypeName      = "TypeAlias"

    def modelWithTypeAlias(
        aliasType: String,
        aliasTraits: List[String] = Nil,
        structurePropertyTraits: List[String] = Nil
    ): Model = {
      val uses       = sqlTraitUses(aliasTraits ++ structurePropertyTraits).map(u => s"use $u").mkString("\n")
      val aliasLines = (aliasTraits :+ s"$aliasType $AliasTypeName").mkString("\n")

      builder.assemble(
        s"""$uses
         |
         |$aliasLines
         |
         |structure $AliasStructureName {
         |    ${structurePropertyTraits.map(line => s"$line").mkString("    \n")}
         |    $AliasMemberName: $AliasTypeName
         |}""".stripMargin.linesIterator.filter(_.nonEmpty).mkString("\n")
      )
    }

    def sqlTraitUses(traitLines: List[String]): List[String] = {
      val text = traitLines.mkString(" ")
      List(
        Option.when(text.contains("sqlUuid"))("smithplates.codegen.sql#sqlUuid"),
        Option.when(text.contains("sqlVarchar"))("smithplates.codegen.sql#sqlVarchar"),
        Option.when(text.contains("sqlJson"))("smithplates.codegen.sql#sqlJson")
      ).flatten
    }

    def memberFromModel(model: Model, structureName: String, memberName: String) =
      model
        .expectShape(ShapeId.from(builder.structureId(structureName)))
        .asStructureShape
        .get()
        .getMember(memberName)
        .get()

    def assertModelColumns(
        model: Model,
        structureName: String,
        expected: List[(String, SqlColumnType)]
    ): Unit =
      expected.foreach { case (memberName, expectedType) =>
        val member = memberFromModel(model, structureName, memberName)
        val actual = converter.fromSmithyMember(model, member)
        Assertions.assertEquals(
          actual,
          Right(expectedType),
          s"member '$memberName' on ${builder.structureId(structureName)}: expected Right($expectedType), got $actual"
        )
      }

    def assertUnsupportedMember(
        model: Model,
        structureName: String,
        memberName: String,
        kind: InvalidMemberColumnType.Kind = InvalidMemberColumnType.Kind.Unsupported
    ): Unit = {
      val member = memberFromModel(model, structureName, memberName)
      Assertions.assertEquals(
        converter.fromSmithyMember(model, member),
        Left(UnsupportedColumnType(member.getTarget, kind)),
        s"member '$memberName' on ${builder.structureId(structureName)}: expected unsupported target ${member.getTarget}"
      )
    }
  }
}
