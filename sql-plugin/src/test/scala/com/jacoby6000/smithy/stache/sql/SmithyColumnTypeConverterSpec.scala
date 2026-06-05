package com.jacoby6000.smithy.stache.sql

import munit.FunSuite
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ShapeId

final class SmithyColumnTypeConverterSpec extends FunSuite {
  private val converter: ColumnTypeConverter = SmithyColumnTypeConverter
  private val builder = SqlTestModelBuilder

  private val AliasStructureName = "AliasMember"
  private val AliasMemberName = "value"
  private val AliasTypeName = "TypeAlias"

  // scalafix:off NoDefaultParameters
  private def modelWithTypeAlias(
      aliasType: String,
      aliasTraits: List[String] = Nil,
      structurePropertyTraits: List[String] = Nil
  ): Model = {
    val uses = sqlTraitUses(aliasTraits ++ structurePropertyTraits).map(u => s"use $u").mkString("\n")
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
  // scalafix:on NoDefaultParameters

  private def sqlTraitUses(traitLines: List[String]): List[String] = {
    val text = traitLines.mkString(" ")
    List(
      Option.when(text.contains("sqlUuid"))("stache.codegen.sql#sqlUuid"),
      Option.when(text.contains("sqlVarchar"))("stache.codegen.sql#sqlVarchar"),
      Option.when(text.contains("sqlJson"))("stache.codegen.sql#sqlJson")
    ).flatten
  }

  private def memberFromModel(model: Model, structureName: String, memberName: String) =
    model
      .expectShape(ShapeId.from(builder.structureId(structureName)))
      .asStructureShape
      .get()
      .getMember(memberName)
      .get()

  private def assertModelColumns(
      model: Model,
      structureName: String,
      expected: List[(String, SqlColumnType)]
  ): Unit =
    expected.foreach { case (memberName, expectedType) =>
      val member = memberFromModel(model, structureName, memberName)
      val actual = converter.fromSmithyMember(model, member)
      assertEquals(
        actual,
        Right(expectedType),
        s"member '$memberName' on ${builder.structureId(structureName)}: expected Right($expectedType), got $actual"
      )
    }

  private def assertUnsupportedMember(
      model: Model,
      structureName: String,
      memberName: String,
      kind: InvalidMemberColumnType.Kind = InvalidMemberColumnType.Kind.Unsupported
  ): Unit = {
    val member = memberFromModel(model, structureName, memberName)
    assertEquals(
      converter.fromSmithyMember(model, member),
      Left(UnsupportedColumnType(member.getTarget, kind)),
      s"member '$memberName' on ${builder.structureId(structureName)}: expected unsupported target ${member.getTarget}"
    )
  }

  test("Text - prelude String maps to Text") {
    val model = builder.assemble(
      """structure PreludeStringMember {
        |    value: String
        |}""".stripMargin
    )
    assertModelColumns(model, "PreludeStringMember", List("value" -> SqlColumnType.Text))
  }

  test("Text - string alias maps to Text") {
    val model = modelWithTypeAlias("string")
    assertModelColumns(model, AliasStructureName, List(AliasMemberName -> SqlColumnType.Text))
  }

  test("Uuid - prelude String with @sqlUuid maps to Uuid") {
    val model = builder.assemble(
      """use stache.codegen.sql#sqlUuid
        |
        |structure PreludeUuidMember {
        |    @sqlUuid
        |    id: String
        |}""".stripMargin
    )
    assertModelColumns(model, "PreludeUuidMember", List("id" -> SqlColumnType.Uuid))
  }

  test("Uuid - string alias with @sqlUuid maps to Uuid") {
    val model = modelWithTypeAlias("string", aliasTraits = List("@sqlUuid"))
    assertModelColumns(model, AliasStructureName, List(AliasMemberName -> SqlColumnType.Uuid))
  }

  test("Uuid - @sqlAutoUuid without @sqlUuid maps to Uuid") {
    val model = builder.assemble(
      """use stache.codegen.sql#sqlAutoUuid
        |
        |structure AutoUuidMember {
        |    @sqlAutoUuid
        |    id: String
        |}""".stripMargin
    )
    assertModelColumns(model, "AutoUuidMember", List("id" -> SqlColumnType.Uuid))
  }

  test("Uuid -  prelude Integer with @sqlUuid is unsupported") {
    val model = builder.assemble(
      """use stache.codegen.sql#sqlUuid
        |
        |structure IntegerUuidMember {
        |    @sqlUuid
        |    count: Integer
        |}""".stripMargin
    )
    assertUnsupportedMember(model, "IntegerUuidMember", "count", InvalidMemberColumnType.Kind.SqlUuid)
  }

  test("Integer - prelude Integer maps to Integer") {
    val model = builder.assemble(
      """structure PreludeIntegerMember {
        |    value: Integer
        |}""".stripMargin
    )
    assertModelColumns(model, "PreludeIntegerMember", List("value" -> SqlColumnType.Integer))
  }

  test("Integer - integer alias maps to Integer") {
    val model = modelWithTypeAlias("integer")
    assertModelColumns(model, AliasStructureName, List(AliasMemberName -> SqlColumnType.Integer))
  }

  test("BigInt - prelude Long maps to BigInt") {
    val model = builder.assemble(
      """structure PreludeLongMember {
        |    value: Long
        |}""".stripMargin
    )
    assertModelColumns(model, "PreludeLongMember", List("value" -> SqlColumnType.BigInt))
  }

  test("BigInt - long alias maps to BigInt") {
    val model = modelWithTypeAlias("long")
    assertModelColumns(model, AliasStructureName, List(AliasMemberName -> SqlColumnType.BigInt))
  }

  test("Timestamp - prelude Timestamp maps to Timestamp") {
    val model = builder.assemble(
      """structure PreludeTimestampMember {
        |    value: Timestamp
        |}""".stripMargin
    )
    assertModelColumns(model, "PreludeTimestampMember", List("value" -> SqlColumnType.Timestamp))
  }

  test("Timestamp - timestamp alias maps to Timestamp") {
    val model = modelWithTypeAlias("timestamp")
    assertModelColumns(model, AliasStructureName, List(AliasMemberName -> SqlColumnType.Timestamp))
  }

  test("Boolean - prelude Boolean maps to Boolean") {
    val model = builder.assemble(
      """structure PreludeBooleanMember {
        |    value: Boolean
        |}""".stripMargin
    )
    assertModelColumns(model, "PreludeBooleanMember", List("value" -> SqlColumnType.Boolean))
  }

  test("Boolean - boolean alias maps to Boolean") {
    val model = modelWithTypeAlias("boolean")
    assertModelColumns(model, AliasStructureName, List(AliasMemberName -> SqlColumnType.Boolean))
  }

  test("Json - Document maps to Json") {
    val model = builder.assemble(
      """structure JsonMembers {
        |    payload: Document
        |}""".stripMargin
    )
    assertModelColumns(model, "JsonMembers", List("payload" -> SqlColumnType.Json))
  }

  test("Varchar - prelude String with @sqlVarchar maps to Varchar") {
    val model = builder.assemble(
      """use stache.codegen.sql#sqlVarchar
        |
        |structure PreludeVarcharMember {
        |    @sqlVarchar(maxLength: 128)
        |    name: String
        |}""".stripMargin
    )
    assertModelColumns(model, "PreludeVarcharMember", List("name" -> SqlColumnType.Varchar(128)))
  }

  test("Varchar - string alias with @sqlVarchar maps to Varchar") {
    val model = modelWithTypeAlias("string", aliasTraits = List("@sqlVarchar(maxLength: 16)"))
    assertModelColumns(model, AliasStructureName, List(AliasMemberName -> SqlColumnType.Varchar(16)))
  }

  test("Text - String without @sqlVarchar maps to Text") {
    val model = builder.assemble(
      """structure PlainStringMember {
        |    name: String
        |}""".stripMargin
    )
    assertModelColumns(model, "PlainStringMember", List("name" -> SqlColumnType.Text))
  }

  test("Varchar - @sqlVarchar on string type alias maps to Varchar") {
    val model = modelWithTypeAlias("string", aliasTraits = List("@sqlVarchar(maxLength: 36)"))
    assertModelColumns(model, AliasStructureName, List(AliasMemberName -> SqlColumnType.Varchar(36)))
  }

  test("Varchar - @sqlVarchar rejected on Integer") {
    val model = builder.assemble(
      """use stache.codegen.sql#sqlVarchar
        |
        |structure IntegerVarcharMember {
        |    @sqlVarchar(maxLength: 8)
        |    count: Integer
        |}""".stripMargin
    )
    assertUnsupportedMember(model, "IntegerVarcharMember", "count", InvalidMemberColumnType.Kind.SqlVarchar)
  }

  test("Varchar - @sqlVarchar rejected on Long") {
    val model = builder.assemble(
      """use stache.codegen.sql#sqlVarchar
        |
        |structure LongVarcharMember {
        |    @sqlVarchar(maxLength: 8)
        |    total: Long
        |}""".stripMargin
    )
    assertUnsupportedMember(model, "LongVarcharMember", "total", InvalidMemberColumnType.Kind.SqlVarchar)
  }

  test("Varchar - @sqlVarchar rejected on Timestamp") {
    val model = builder.assemble(
      """use stache.codegen.sql#sqlVarchar
        |
        |structure TimestampVarcharMember {
        |    @sqlVarchar(maxLength: 32)
        |    created_at: Timestamp
        |}""".stripMargin
    )
    assertUnsupportedMember(model, "TimestampVarcharMember", "created_at", InvalidMemberColumnType.Kind.SqlVarchar)
  }

  test("Varchar - @sqlVarchar rejected on Boolean") {
    val model = builder.assemble(
      """use stache.codegen.sql#sqlVarchar
        |
        |structure BooleanVarcharMember {
        |    @sqlVarchar(maxLength: 1)
        |    active: Boolean
        |}""".stripMargin
    )
    assertUnsupportedMember(model, "BooleanVarcharMember", "active", InvalidMemberColumnType.Kind.SqlVarchar)
  }

  test("Varchar - @sqlVarchar rejected on Document") {
    val model = builder.assemble(
      """use stache.codegen.sql#sqlVarchar
        |
        |structure DocumentVarcharMember {
        |    @sqlVarchar(maxLength: 1024)
        |    payload: Document
        |}""".stripMargin
    )
    assertUnsupportedMember(model, "DocumentVarcharMember", "payload", InvalidMemberColumnType.Kind.SqlVarchar)
  }

  test("List[String] - is unsupported without @sqlJson") {
    val model = builder.assemble(
      """list Names { member: String }
        |
        |structure ListMember {
        |    names: Names
        |}""".stripMargin
    )
    assertUnsupportedMember(model, "ListMember", "names")
  }

  test("List[String] - maps to Json with @sqlJson") {
    val model = builder.assemble(
      """use stache.codegen.sql#sqlJson
        |
        |list Names { member: String }
        |
        |structure ListMember {
        |    @sqlJson
        |    names: Names
        |}""".stripMargin
    )
    assertModelColumns(model, "ListMember", List("names" -> SqlColumnType.Json))
  }

  test("Map[String, String] - is unsupported without @sqlJson") {
    val model = builder.assemble(
      """map StringMap {
        |    key: String
        |    value: String
        |}
        |
        |structure MapMember {
        |    tags: StringMap
        |}""".stripMargin
    )
    assertUnsupportedMember(model, "MapMember", "tags")
  }

  test("Map[String, String] - maps to Json with @sqlJson") {
    val model = builder.assemble(
      """use stache.codegen.sql#sqlJson
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
    assertModelColumns(model, "MapMember", List("tags" -> SqlColumnType.Json))
  }

  test("Structure - nested structure is unsupported without @sqlJson") {
    val model = builder.assemble(
      """structure Nested {
        |    field: String
        |}
        |
        |structure StructureMember {
        |    nested: Nested
        |}""".stripMargin
    )
    assertUnsupportedMember(model, "StructureMember", "nested")
  }

  test("Structure - nested structure maps to Json with @sqlJson") {
    val model = builder.assemble(
      """use stache.codegen.sql#sqlJson
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
    assertModelColumns(model, "StructureMember", List("nested" -> SqlColumnType.Json))
  }

  test("Blob - prelude Blob maps to Blob") {
    val model = builder.assemble(
      """structure BlobMember {
        |    data: Blob
        |}""".stripMargin
    )
    assertModelColumns(model, "BlobMember", List("data" -> SqlColumnType.Blob))
  }

  test("Json - @sqlJson rejected on String") {
    val model = builder.assemble(
      """use stache.codegen.sql#sqlJson
        |
        |structure StringJsonMember {
        |    @sqlJson
        |    name: String
        |}""".stripMargin
    )
    assertUnsupportedMember(model, "StringJsonMember", "name", InvalidMemberColumnType.Kind.SqlJson)
  }

  test("Union - maps to Json with @sqlJson") {
    val model = builder.assemble(
      """use stache.codegen.sql#sqlJson
        |use stache.codegen.sql#sqlPrimaryKey
        |use stache.codegen.sql#sqlTable
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
    assertModelColumns(model, "UnionMember", List("id" -> SqlColumnType.Text, "value" -> SqlColumnType.Json))
  }

  test("Union - is unsupported on @sqlTable structure without @sqlJson") {
    val model = builder.assemble(
      """use stache.codegen.sql#sqlPrimaryKey
        |use stache.codegen.sql#sqlTable
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

    val member = memberFromModel(model, "UnionMember", "value")
    assertEquals(
      member.getTarget,
      ShapeId.from("example#Measurement"),
      "expected member to target the union shape, not a @sqlTable structure"
    )
    assertUnsupportedMember(model, "UnionMember", "value")
  }

  test("Enum - enum without assignments maps to StringEnum") {
    val model = builder.assemble(
      """enum Direction {
        |    NORTH
        |    SOUTH
        |}
        |
        |structure EnumMembers {
        |    direction: Direction
        |}""".stripMargin
    )
    assertModelColumns(
      model,
      "EnumMembers",
      List(
        "direction" ->
          SqlColumnType.StringEnum(ShapeId.from("example#Direction"), "example_direction", List("NORTH", "SOUTH"))
      )
    )
  }

  test("Enum - enum with string assignments maps to StringEnum") {
    val model = builder.assemble(
      """enum LabeledDirection {
        |    NORTH = "north"
        |    SOUTH = "south"
        |}
        |
        |structure LabeledEnumMembers {
        |    direction: LabeledDirection
        |}""".stripMargin
    )
    assertModelColumns(
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
    val model = builder.assemble(
      """intEnum HttpStatus {
        |    OK = 200
        |    NOT_FOUND = 404
        |}
        |
        |structure StatusMember {
        |    status: HttpStatus
        |}""".stripMargin
    )
    assertModelColumns(
      model,
      "StatusMember",
      List("status" -> SqlColumnType.IntEnum("example_httpstatus", List(404, 200)))
    )
  }
}
