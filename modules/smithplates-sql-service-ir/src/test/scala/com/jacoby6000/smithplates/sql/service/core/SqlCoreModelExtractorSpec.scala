package com.jacoby6000.smithplates.sql.service.core

import com.jacoby6000.smithplates.codegen.core.*
import com.jacoby6000.smithplates.codegen.core.NeutralType.*
import com.jacoby6000.smithplates.smithy.neutral.LegacyNeutralTypeEquivalence
import com.jacoby6000.smithplates.smithy.neutral.ModelSetClosureAssertions
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

          ModelSetClosureAssertions.assertAllModelRefsResolved(modelSet, services)
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
          assert(modelSet.structures.exists(_.id.name == "ItemNotFound"))

          ModelSetClosureAssertions.assertAllModelRefsResolved(modelSet, services)
        }
      )
  }

  test("core extraction closes aliases referenced only through list members") {
    val model = SqlTestModelBuilder.assemble(
      """
        |use smithplates.codegen.sql#sqlService
        |
        |string Tag
        |
        |list Tags {
        |    member: Tag
        |}
        |
        |structure PostPayload {
        |    @required
        |    tags: Tags
        |}
        |
        |operation ListPosts {
        |    input: Unit
        |    output: PostPayload
        |}
        |
        |@sqlService
        |service PostRepository {
        |    version: "1"
        |    operations: [ListPosts]
        |}
        |""".stripMargin
    )

    SqlCoreModelExtractor
      .extractAndValidate(model)
      .fold(
        errors => fail(errors.toList.map(_.message).mkString("; ")),
        { case (modelSet, services) =>
          val tagAlias = modelSet.aliases.find(_.id.name == "Tag").getOrElse {
            fail("expected Tag alias in model set closure")
          }
          assertEquals(tagAlias.underlying, StringT)

          val payload = modelSet.structures.find(_.id.name == "PostPayload").getOrElse {
            fail("expected PostPayload structure")
          }
          assertEquals(payload.fields.find(_.name == "tags").map(_.tpe), Some(ListT(ModelRef(tagAlias.id))))

          ModelSetClosureAssertions.assertAllModelRefsResolved(modelSet, services)
        }
      )
  }

  test("core extraction closes integer primitive aliases") {
    val model = SqlTestModelBuilder.assemble(
      """
        |use smithplates.codegen.sql#sqlService
        |use smithplates.codegen.sql#sqlTable
        |use smithplates.codegen.sql#sqlPrimaryKey
        |
        |integer Count
        |
        |@sqlTable(name: "metrics")
        |structure Metric {
        |    @sqlPrimaryKey
        |    id: String
        |    total: Count
        |}
        |
        |operation ListMetrics {
        |    input: Unit
        |    output: Unit
        |}
        |
        |@sqlService
        |service MetricRepository {
        |    version: "1"
        |    operations: [ListMetrics]
        |}
        |""".stripMargin
    )

    val legacyShapes =
      SqlShapeIrExtractor
        .extract(model, List(ShapeId.from("example#Metric")))
        .fold(errors => fail(errors.toList.map(_.message).mkString("; ")), identity)

    SqlCoreModelExtractor
      .extractAndValidate(model)
      .fold(
        errors => fail(errors.toList.map(_.message).mkString("; ")),
        { case (modelSet, services) =>
          val countAlias = modelSet.aliases.find(_.id.name == "Count").getOrElse {
            fail("expected Count alias in model set closure")
          }
          assertEquals(countAlias.underlying, IntegerT)

          val metric = modelSet.structures.find(_.id.name == "Metric").getOrElse {
            fail("expected Metric table structure")
          }
          assertEquals(
            metric.fields.find(_.name == "total").map(_.tpe),
            Some(OptionalT(ModelRef(countAlias.id)))
          )

          val legacyMetric = legacyShapes.structures.find(_.name == "Metric").getOrElse {
            fail("expected legacy Metric structure")
          }
          val legacyTotal  = legacyMetric.members.find(_.name == "total").getOrElse {
            fail("expected legacy total member")
          }
          LegacyNeutralTypeEquivalence.assertEquivalentWithAliases(
            legacyTypeName = legacyTotal.typeName,
            legacyOptional = legacyTotal.optional,
            coreType = metric.fields.find(_.name == "total").get.tpe,
            aliases = modelSet.aliases
          )

          ModelSetClosureAssertions.assertAllModelRefsResolved(modelSet, services)
        }
      )
  }

  test("extract runs the same validation pipeline as extractAndValidate") {
    val model     = SqlServiceExtractorSpec.internal.assembleServiceModel(
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
    val validated =
      SqlCoreModelExtractor
        .extractAndValidate(model)
        .fold(errors => fail(errors.toList.map(_.message).mkString("; ")), identity)
    val extracted =
      SqlCoreModelExtractor
        .extract(model)
        .fold(errors => fail(errors.toList.map(_.message).mkString("; ")), identity)
    assertEquals(extracted, validated)
  }

  test("core extraction maps sparse list members to ListT(OptionalT(...))") {
    val model = SqlTestModelBuilder.assemble(
      """
        |use smithplates.codegen.sql#sqlService
        |use smithy.api#sparse
        |
        |@sparse
        |list SparseTags {
        |    member: String
        |}
        |
        |structure PostPayload {
        |    @required
        |    tags: SparseTags
        |}
        |
        |operation ListPosts {
        |    input: Unit
        |    output: PostPayload
        |}
        |
        |@sqlService
        |service PostRepository {
        |    version: "1"
        |    operations: [ListPosts]
        |}
        |""".stripMargin
    )

    SqlCoreModelExtractor
      .extract(model)
      .fold(
        errors => fail(errors.toList.map(_.message).mkString("; ")),
        { case (modelSet, services) =>
          val payload = modelSet.structures.find(_.id.name == "PostPayload").getOrElse {
            fail("expected PostPayload structure")
          }
          assertEquals(payload.fields.head.tpe, ListT(OptionalT(StringT)))
          ModelSetClosureAssertions.assertAllModelRefsResolved(modelSet, services)
        }
      )
  }

  test("core extraction maps @default members to OptionalT through legacy parity") {
    val model = SqlServiceExtractorSpec.internal.assembleServiceModel(
      """
        |use smithplates.codegen.sql#sqlService
        |""".stripMargin,
      """
        |structure GetItemInput {
        |    @required
        |    id: String
        |
        |    @default("anonymous")
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
        { case (modelSet, services) =>
          legacyShapes.structures.foreach { legacyStructure =>
            val coreStructure = modelSet.structures.find(_.id.name == legacyStructure.name).getOrElse {
              fail(s"expected core structure ${legacyStructure.name}")
            }
            legacyStructure.members.zip(coreStructure.fields).foreach { case (legacyMember, coreField) =>
              LegacyNeutralTypeEquivalence.assertEquivalentWithAliases(
                legacyTypeName = legacyMember.typeName,
                legacyOptional = legacyMember.optional,
                coreType = coreField.tpe,
                aliases = modelSet.aliases
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
          ModelSetClosureAssertions.assertAllModelRefsResolved(modelSet, services)
        }
      )
  }
}
