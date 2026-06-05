package com.jacoby6000.smithy.stache.sql

import cats.data.Validated
import munit.FunSuite
import software.amazon.smithy.model.shapes.ShapeId

final class SqlModelExtractorSpec extends FunSuite {
  test("Extractor - fails when sql table has no primary key") {
    val model = SqlTestModelLoader.assemble(
      "missing-primary-key.smithy" ->
        """
          |$version: "2.0"
          |
          |namespace stache.codegen.sql.example
          |
          |use stache.codegen.sql#sqlTable
          |
          |@sqlTable(name: "foos")
          |structure FooWithoutPk {
          |    name: String
          |}
          |""".stripMargin
    )

    assert(SqlModelExtractor.extract(model).isInvalid)
  }

  test("Extractor - accumulates errors across multiple invalid sql tables") {
    val model = SqlTestModelLoader.assemble(
      "multi-error.smithy" ->
        """
          |$version: "2.0"
          |namespace example
          |
          |use stache.codegen.sql#sqlTable
          |
          |@sqlTable(name: "no_pk_one")
          |structure NoPkOne {
          |    name: String
          |}
          |
          |@sqlTable(name: "no_pk_two")
          |structure NoPkTwo {
          |    label: String
          |}
          |""".stripMargin
    )

    SqlModelExtractor.extract(model) match {
      case Validated.Invalid(errors) =>
        assertEquals(errors.size, 2)
        assert(errors.forall(_.isInstanceOf[MissingPrimaryKey]))
      case Validated.Valid(_)        =>
        fail("expected validation to fail for both tables")
    }
  }

  test("Varchar - extractor rejects non-string-like @sqlVarchar member") {
    val model = SqlTestModelLoader.assemble(
      "model.smithy" ->
        """
          |$version: "2.0"
          |namespace example
          |
          |use stache.codegen.sql#sqlPrimaryKey
          |use stache.codegen.sql#sqlTable
          |use stache.codegen.sql#sqlVarchar
          |
          |@sqlTable(name: "invalid_varchar")
          |structure InvalidVarchar {
          |    @sqlPrimaryKey
          |    id: String
          |    @sqlVarchar(maxLength: 16)
          |    count: Integer
          |}
          |""".stripMargin
    )

    assert(SqlModelExtractor.extract(model).isInvalid)
  }

  test("Json - extractor rejects @sqlJson on non-json-like member") {
    val model = SqlTestModelLoader.assemble(
      "model.smithy" ->
        """
          |$version: "2.0"
          |namespace example
          |
          |use stache.codegen.sql#sqlPrimaryKey
          |use stache.codegen.sql#sqlTable
          |use stache.codegen.sql#sqlJson
          |
          |@sqlTable(name: "invalid_json")
          |structure InvalidJson {
          |    @sqlPrimaryKey
          |    id: String
          |    @sqlJson
          |    name: String
          |}
          |""".stripMargin
    )

    SqlModelExtractor.extract(model) match {
      case Validated.Invalid(errors) =>
        assert(SqlValidated.hasInvalidMemberKind(errors, InvalidMemberColumnType.Kind.SqlJson))
      case Validated.Valid(_)        =>
        fail("expected validation to fail")
    }
  }

  test("Uuid - extractor rejects non-string-like @sqlUuid member") {
    val model = SqlTestModelLoader.assemble(
      "model.smithy" ->
        """
          |$version: "2.0"
          |namespace example
          |
          |use stache.codegen.sql#sqlPrimaryKey
          |use stache.codegen.sql#sqlTable
          |use stache.codegen.sql#sqlUuid
          |
          |@sqlTable(name: "invalid_uuid")
          |structure InvalidUuid {
          |    @sqlPrimaryKey
          |    id: String
          |    @sqlUuid
          |    count: Integer
          |}
          |""".stripMargin
    )

    SqlModelExtractor.extract(model) match {
      case Validated.Invalid(errors) =>
        assert(SqlValidated.hasInvalidMemberKind(errors, InvalidMemberColumnType.Kind.SqlUuid))
      case Validated.Valid(_)        =>
        fail("expected validation to fail")
    }
  }

  test("Varchar - extractor maps @sqlVarchar member to Varchar") {
    val model = SqlTestModelLoader.assemble(
      "model.smithy" ->
        """
          |$version: "2.0"
          |namespace example
          |use stache.codegen.sql#sqlPrimaryKey
          |use stache.codegen.sql#sqlTable
          |use stache.codegen.sql#sqlVarchar
          |
          |@sqlTable(name: "sample")
          |structure Sample {
          |    @sqlPrimaryKey
          |    id: String
          |    @sqlVarchar(maxLength: 64)
          |    code: String
          |}
          |""".stripMargin
    )

    val schema     = SqlModelExtractor.extractOrThrow(model)
    val table      = schema.tables.find(_.name == "sample").get
    val codeColumn = table.columns.find(_.name == "code").get
    assertEquals(codeColumn.columnType, SqlColumnType.Varchar(64))
  }

  test("Extractor - ignores structures without @sqlTable") {
    val model = SqlTestModelLoader.assemble(
      "model.smithy" ->
        """
          |$version: "2.0"
          |namespace example
          |
          |union Measurement {
          |    string: String
          |    integer: Integer
          |}
          |
          |structure NotATable {
          |    id: String
          |    value: Measurement
          |}
          |""".stripMargin
    )

    val schema = SqlModelExtractor.extractOrThrow(model)
    assertEquals(schema.tables, Nil)
  }

  test("Union - extractor accepts @sqlJson union member") {
    val model = SqlTestModelLoader.assemble(
      "model.smithy" ->
        """
          |$version: "2.0"
          |namespace example
          |
          |use stache.codegen.sql#sqlJson
          |use stache.codegen.sql#sqlPrimaryKey
          |use stache.codegen.sql#sqlTable
          |
          |union Measurement {
          |    string: String
          |    integer: Integer
          |}
          |
          |@sqlTable(name: "union_members")
          |structure UnionTable {
          |    @sqlPrimaryKey
          |    id: String
          |    @sqlJson
          |    value: Measurement
          |}
          |""".stripMargin
    )

    val schema = SqlModelExtractor.extractOrThrow(model)
    val table  = schema.tables.head
    assertEquals(table.name, "union_members")
    assertEquals(table.columns.map(_.name), List("id", "value"))
    assertEquals(table.columns.map(_.columnType), List(SqlColumnType.Text, SqlColumnType.Json))
  }

  test("Union - extractor fails on union member without @sqlJson") {
    val model = SqlTestModelLoader.assemble(
      "model.smithy" ->
        """
          |$version: "2.0"
          |namespace example
          |
          |use stache.codegen.sql#sqlPrimaryKey
          |use stache.codegen.sql#sqlTable
          |
          |union Measurement {
          |    string: String
          |    integer: Integer
          |}
          |
          |@sqlTable(name: "union_members")
          |structure UnionTable {
          |    @sqlPrimaryKey
          |    id: String
          |    value: Measurement
          |}
          |""".stripMargin
    )

    SqlModelExtractor.extract(model) match {
      case Validated.Invalid(errors) =>
        assert(SqlValidated.hasInvalidMemberKind(errors, InvalidMemberColumnType.Kind.Unsupported))
        assert(
          errors.exists { error =>
            error.message.contains("example#Measurement") && error.message.contains("value")
          },
          errors.map(_.message).toList.mkString("; ")
        )
      case Validated.Valid(_)        =>
        fail("expected validation to fail")
    }
  }

  test("UniqueIndex - renders unique index and models one-to-one foreign key relationship") {
    val model = SqlTestModelLoader.assemble(
      "relationships.smithy" ->
        """
          |$version: "2.0"
          |namespace example
          |
          |use stache.codegen.sql#sqlForeignKey
          |use stache.codegen.sql#sqlPrimaryKey
          |use stache.codegen.sql#sqlTable
          |use stache.codegen.sql#sqlUniqueIndex
          |
          |@sqlTable(name: "bars")
          |structure Bar {
          |    @sqlPrimaryKey
          |    id: String
          |}
          |
          |@sqlTable(name: "foos")
          |structure Foo {
          |    @sqlPrimaryKey
          |    id: String
          |    @sqlForeignKey(references: "example#Bar")
          |    bar_id: String
          |}
          |
          |@sqlTable(name: "profiles")
          |structure Profile {
          |    @sqlPrimaryKey
          |    id: String
          |    @sqlForeignKey(references: "example#Bar")
          |    @sqlUniqueIndex(name: "uidx_profiles_bar_id")
          |    bar_id: String
          |}
          |""".stripMargin
    )

    val schema              = SqlModelExtractor.extractOrThrow(model)
    val fooRelationship     =
      schema.relationships.find(_.sourceTable == ShapeId.from("example#Foo")).getOrElse {
        fail("expected Foo relationship")
      }
    val profileRelationship =
      schema.relationships.find(_.sourceTable == ShapeId.from("example#Profile")).getOrElse {
        fail("expected Profile relationship")
      }

    assertEquals(fooRelationship.cardinality, SqlRelationshipCardinality.ManyToOne)
    assertEquals(profileRelationship.cardinality, SqlRelationshipCardinality.OneToOne)

    val profileTable = schema.tables.find(_.shapeId == ShapeId.from("example#Profile")).get
    assertEquals(
      profileTable.indexes.map(index => (index.name, index.unique)),
      List((Some("uidx_profiles_bar_id"), true)))
    assertEquals(
      profileTable.foreignKeys.map(_.cardinality),
      List(SqlRelationshipCardinality.OneToOne)
    )
  }

  test("UniqueIndex - rejects members with both sqlIndex and sqlUniqueIndex") {
    val model = SqlTestModelLoader.assemble(
      "conflicting-index.smithy" ->
        """
          |$version: "2.0"
          |namespace example
          |
          |use stache.codegen.sql#sqlIndex
          |use stache.codegen.sql#sqlPrimaryKey
          |use stache.codegen.sql#sqlTable
          |use stache.codegen.sql#sqlUniqueIndex
          |
          |@sqlTable(name: "foos")
          |structure Foo {
          |    @sqlPrimaryKey
          |    id: String
          |    @sqlIndex
          |    @sqlUniqueIndex
          |    code: String
          |}
          |""".stripMargin
    )

    assert(SqlModelExtractor.extract(model).isInvalid)
  }
}
