package com.jacoby6000.smithplates.testkit

import com.jacoby6000.smithplates.smithy.neutral.LegacyNeutralTypeEquivalence
import com.jacoby6000.smithplates.smithy.neutral.ModelSetClosureAssertions
import com.jacoby6000.smithplates.sql.*
import com.jacoby6000.smithplates.sql.service.core.SqlCoreModelExtractor
import com.jacoby6000.smithplates.sql.service.core.SqlMeta
import munit.FunSuite
import software.amazon.smithy.model.shapes.ShapeId

class SqlCoreModelExtractorIntegrationSpec extends FunSuite {
  test("SqlCoreModelExtractor matches legacy SqlShapeIrExtractor on testkit simple service model") {
    val model        = SqlIntegrationSchemas.simpleServiceModel
    val namespace    = SqlIntegrationSchemas.Namespace
    val legacyShapes =
      SqlShapeIrExtractor
        .extract(
          model,
          List(ShapeId.from(s"$namespace#Category"), ShapeId.from(s"$namespace#Item"))
        )
        .fold(errors => fail(errors.toList.map(_.message).mkString("; ")), identity)

    SqlCoreModelExtractor
      .extract(model)
      .fold(
        errors => fail(errors.toList.map(_.message).mkString("; ")),
        { case (modelSet, services) =>
          assertEquals(services.size, 1)
          assertEquals(services.head.operations.map(_.id.name), List("ListItems"))

          val categoryTable = modelSet.structures.find(_.id.name == "Category").getOrElse {
            fail("expected Category table structure")
          }
          assertEquals(categoryTable.meta.feature, SqlMeta.SqlTableMeta(tableName = "categories"))

          val itemTable = modelSet.structures.find(_.id.name == "Item").getOrElse {
            fail("expected Item table structure")
          }
          assertEquals(itemTable.meta.feature, SqlMeta.SqlTableMeta(tableName = "items"))

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

          ModelSetClosureAssertions.assertAllModelRefsResolved(modelSet, services)
        }
      )
  }
}
