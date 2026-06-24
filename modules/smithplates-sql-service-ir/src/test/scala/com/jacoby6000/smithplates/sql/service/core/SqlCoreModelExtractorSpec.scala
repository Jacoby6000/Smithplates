package com.jacoby6000.smithplates.sql.service.core

import com.jacoby6000.smithplates.codegen.core.*
import com.jacoby6000.smithplates.codegen.core.NeutralType.*
import com.jacoby6000.smithplates.smithy.neutral.LegacyNeutralTypeEquivalence
import com.jacoby6000.smithplates.sql.*
import com.jacoby6000.smithplates.sql.service.SqlServiceExtractorSpec
import munit.FunSuite
import software.amazon.smithy.model.shapes.ShapeId

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
          assertEquals(input.meta.feature, SqlMeta.SqlNestedField)

          val operation = services.head.operations.head
          assertEquals(operation.input, Some(ModelRef(input.id)))
          assertEquals(operation.output.map(_.id.name), Some("GetItemOutput"))
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

    assertLegacyShapeParity(model)
  }

  test("core extraction applies SQL table column optionality rules") {
    val model = SqlTestModelBuilder.assemble(
      """
        |use smithplates.codegen.sql#sqlAutoUuid
        |use smithplates.codegen.sql#sqlPrimaryKey
        |use smithplates.codegen.sql#sqlService
        |use smithplates.codegen.sql#sqlTable
        |
        |@sqlTable(name: "widgets")
        |structure Widget {
        |    @sqlPrimaryKey
        |    @sqlAutoUuid
        |    id: String
        |
        |    @required
        |    name: String
        |
        |    note: String
        |}
        |
        |operation ListWidgets {
        |    input: Unit
        |    output: Unit
        |}
        |
        |@sqlService
        |service WidgetRepository {
        |    version: "1"
        |    operations: [ListWidgets]
        |}
        |""".stripMargin
    )

    val legacyShapes =
      SqlShapeIrExtractor
        .extract(
          model,
          List(ShapeId.from("example#Widget"))
        )
        .fold(errors => fail(errors.toList.map(_.message).mkString("; ")), identity)

    SqlCoreModelExtractor
      .extractAndValidate(model)
      .fold(
        errors => fail(errors.toList.map(_.message).mkString("; ")),
        { case (modelSet, services) =>
          val legacyWidget = legacyShapes.structures.find(_.name == "Widget").getOrElse {
            fail("expected legacy Widget structure")
          }
          val coreWidget   = modelSet.structures.find(_.id.name == "Widget").getOrElse {
            fail("expected core Widget structure")
          }

          legacyWidget.members.zip(coreWidget.fields).foreach { case (legacyMember, coreField) =>
            LegacyNeutralTypeEquivalence.assertEquivalent(
              legacyTypeName = legacyMember.typeName,
              legacyOptional = legacyMember.optional,
              coreType = coreField.tpe
            )
          }

          val listWidgets = services.head.operations.head
          assertEquals(listWidgets.input, None)
          assertEquals(listWidgets.output, None)
        }
      )
  }

  test("core extraction normalizes DerivedStruct operation IO to None") {
    val model = SqlTestModelBuilder.assemble(
      """
        |use smithplates.codegen.sql#DerivedStruct
        |use smithplates.codegen.sql#sqlDeriveInsert
        |use smithplates.codegen.sql#sqlPrimaryKey
        |use smithplates.codegen.sql#sqlService
        |use smithplates.codegen.sql#sqlTable
        |
        |@sqlTable(name: "widgets")
        |structure Widget {
        |    @sqlPrimaryKey
        |    id: String
        |    name: String
        |}
        |
        |structure CreateWidgetOutput {
        |    @required
        |    id: String
        |}
        |
        |@sqlDeriveInsert(targetTable: "example#Widget")
        |operation CreateWidget {
        |    input: DerivedStruct
        |    output: CreateWidgetOutput
        |}
        |
        |@sqlService
        |service WidgetRepository {
        |    version: "1"
        |    operations: [CreateWidget]
        |}
        |""".stripMargin
    )

    SqlCoreModelExtractor
      .extractAndValidate(model)
      .fold(
        errors => fail(errors.toList.map(_.message).mkString("; ")),
        { case (_, services) =>
          val createWidget = services.head.operations.head
          assertEquals(createWidget.input, None)
          assertEquals(createWidget.output.map(_.id.name), Some("CreateWidgetOutput"))
        }
      )
  }

  test("core extraction includes enums and operation error refs") {
    val model = SqlServiceExtractorSpec.internal.assembleServiceModel(
      """
        |use smithplates.codegen.sql#sqlService
        |use smithy.api#error
        |""".stripMargin,
      """
        |enum TaskStatus {
        |    OPEN = "open"
        |    CLOSED = "closed"
        |}
        |
        |intEnum TaskPriority {
        |    LOW = 1
        |    HIGH = 2
        |}
        |
        |structure TaskPayload {
        |    status: TaskStatus
        |    priority: TaskPriority
        |}
        |
        |@error("client")
        |structure ItemNotFound {
        |    @required
        |    message: String
        |}
        |
        |structure GetItemInput {
        |    @required
        |    id: String
        |}
        |
        |structure GetItemOutput {
        |    @required
        |    payload: TaskPayload
        |}
        |
        |operation GetItem {
        |    input: GetItemInput
        |    output: GetItemOutput
        |    errors: [ItemNotFound]
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
          assert(modelSet.enums.exists(_.id.name == "TaskStatus"))
          assert(modelSet.enums.exists(_.id.name == "TaskPriority"))

          val taskStatus = modelSet.enums.find(_.id.name == "TaskStatus").get
          assertEquals(taskStatus.base, StringT)
          assertEquals(taskStatus.values.map(_.name), List("CLOSED", "OPEN"))

          val getItem = services.head.operations.head
          assertEquals(getItem.errors.map(_.id.name), List("ItemNotFound"))
        }
      )
  }

  test("extract propagates SqlServiceExtractor failures as InvalidSmithyShape") {
    val model = SqlServiceExtractorSpec.internal.assembleServiceModel(
      """
        |use smithplates.codegen.sql#sqlService
        |""".stripMargin,
      """
        |@sqlService
        |service EmptyRepository {
        |    version: "1"
        |    operations: []
        |}
        |""".stripMargin
    )

    SqlCoreModelExtractor
      .extract(model)
      .fold(
        errors => assert(errors.exists(_.isInstanceOf[InvalidSmithyShape])),
        _ => fail("expected SqlServiceExtractor failure")
      )
  }

  private def assertLegacyShapeParity(model: software.amazon.smithy.model.Model): Unit = {
    val legacyShapes =
      SqlShapeIrExtractor
        .extract(
          model,
          List(
            ShapeId.from("example#GetItemInput"),
            ShapeId.from("example#GetItemOutput"),
            ShapeId.from("example#Item")
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

          legacyShapes.unions.foreach { legacyUnion =>
            val coreUnion = modelSet.unions.find(_.id.name == legacyUnion.name).getOrElse {
              fail(s"expected core union ${legacyUnion.name}")
            }
            legacyUnion.members.zip(coreUnion.members).foreach { case (legacyMember, coreMember) =>
              LegacyNeutralTypeEquivalence.assertEquivalent(
                legacyTypeName = legacyMember.typeName,
                legacyOptional = false,
                coreType = coreMember.tpe
              )
            }
          }
        }
      )
  }
}
