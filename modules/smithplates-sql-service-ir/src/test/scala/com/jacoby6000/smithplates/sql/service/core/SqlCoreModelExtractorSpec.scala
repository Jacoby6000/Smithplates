package com.jacoby6000.smithplates.sql.service.core

import com.jacoby6000.smithplates.codegen.core.*
import com.jacoby6000.smithplates.codegen.core.NeutralType.*
import com.jacoby6000.smithplates.smithy.neutral.LegacyNeutralTypeEquivalence
import com.jacoby6000.smithplates.sql.*
import com.jacoby6000.smithplates.sql.service.SqlServiceExtractorSpec
import munit.FunSuite

class SqlCoreModelExtractorSpec extends FunSuite {
  test("SqlCoreModelExtractor produces table structures with SqlTableMeta and alias closure") {
    val model = SqlServiceExtractorSpec.internal.assembleServiceModel(
      """
        |use smithplates.codegen.sql#sqlService
        |use smithy.api#pattern
        |""".stripMargin,
      """
        |@pattern("^[a-z0-9-]+$")
        |string ItemId
        |
        |structure GetItemInput {
        |    @required
        |    id: ItemId
        |}
        |
        |structure GetItemOutput {
        |    @required
        |    id: ItemId
        |}
        |
        |operation GetItem {
        |    input: GetItemInput
        |    output: GetItemOutput
        |}
        |
        |@sqlService
        |service ItemRepository {
        |    version: "1"
        |    operations: [GetItem]
        |}
        |""".stripMargin
    )

    SqlCoreModelExtractor
      .extractAndValidate(model)
      .fold(
        errors => fail(errors.toList.map(_.message).mkString("; ")),
        { case (modelSet, services) =>
          assertEquals(services.size, 1)
          assertEquals(services.head.operations.map(_.id.name), List("GetItem"))

          val itemId = modelSet.aliases.find(_.id.name == "ItemId").getOrElse {
            fail("expected ItemId alias in model set closure")
          }
          assertEquals(itemId.underlying, StringT)

          val itemTable = modelSet.structures.find(_.id.name == "Item").getOrElse {
            fail("expected Item table structure")
          }
          assertEquals(itemTable.meta.feature, SqlMeta.SqlTableMeta(tableName = "items"))

          val input = modelSet.structures.find(_.id.name == "GetItemInput").getOrElse {
            fail("expected GetItemInput structure")
          }
          assertEquals(input.fields.map(_.name), List("id"))
          assertEquals(input.fields.head.tpe, ModelRef(itemId.id))
        }
      )
  }

  test("core extraction member types match legacy SqlShapeIrExtractor") {
    val model = SqlServiceExtractorSpec.internal.assembleServiceModel(
      """
        |use smithplates.codegen.sql#sqlService
        |""".stripMargin,
      """
        |structure GetItemInput {
        |    @required
        |    id: String
        |
        |    label: String
        |}
        |
        |structure GetItemOutput {
        |    @required
        |    id: String
        |}
        |
        |operation GetItem {
        |    input: GetItemInput
        |    output: GetItemOutput
        |}
        |
        |@sqlService
        |service ItemRepository {
        |    version: "1"
        |    operations: [GetItem]
        |}
        |""".stripMargin
    )

    val legacyShapes =
      SqlShapeIrExtractor
        .extract(
          model,
          List(
            software.amazon.smithy.model.shapes.ShapeId.from("example#GetItemInput"),
            software.amazon.smithy.model.shapes.ShapeId.from("example#GetItemOutput"),
            software.amazon.smithy.model.shapes.ShapeId.from("example#Item")
          )
        )
        .fold(errors => fail(errors.toList.map(_.message).mkString("; ")), identity)

    SqlCoreModelExtractor
      .extractAndValidate(model)
      .fold(
        errors => fail(errors.toList.map(_.message).mkString("; ")),
        { case (modelSet, _) =>
          legacyShapes.structures.foreach { legacyStructure =>
            val coreStructure = modelSet.structures.find(_.id.name == legacyStructure.name).getOrElse {
              fail(s"expected core structure ${legacyStructure.name}")
            }
            legacyStructure.members.zip(coreStructure.fields).foreach { case (legacyMember, coreField) =>
              LegacyNeutralTypeEquivalence.assertEquivalent(
                legacyTypeName = legacyMember.typeName,
                legacyOptional = legacyMember.optional,
                coreType = coreField.tpe
              )
            }
          }
        }
      )
  }
}
