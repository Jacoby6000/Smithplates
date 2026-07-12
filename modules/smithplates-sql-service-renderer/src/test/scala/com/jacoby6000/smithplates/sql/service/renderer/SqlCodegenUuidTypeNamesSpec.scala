package com.jacoby6000.smithplates.sql.service.renderer

import cats.data.Validated
import com.jacoby6000.smithplates.sql.SqlShapeIrExtractor
import com.jacoby6000.smithplates.sql.SqlTestModelBuilder
import com.jacoby6000.smithplates.sql.model.*
import com.jacoby6000.smithplates.sql.service.SqlModelExtractor

class SqlCodegenUuidTypeNamesSpec extends munit.FunSuite {
  test("fromSchema - collects @sqlUuid string alias type names from table members") {
    val model      = SqlTestModelBuilder.assemble(
      """
            |use smithplates.codegen.sql#sqlAutoUuid
            |use smithplates.codegen.sql#sqlPrimaryKey
            |use smithplates.codegen.sql#sqlTable
            |use smithplates.codegen.sql#sqlUuid
            |use smithy.api#pattern
            |
            |@pattern("^[0-9a-fA-F-]+$")
            |@sqlUuid
            |string Uuid
            |
            |@sqlTable(name: "widgets")
            |structure Widget {
            |    @sqlPrimaryKey
            |    @sqlAutoUuid
            |    id: Uuid
            |    label: String
            |}
            |""".stripMargin
    )
    val extraction = SqlModelExtractor.extractOrThrow(model)

    val shapeIr =
      SqlShapeIrExtractor.extract(model, extraction.tables.map(_.shapeId)) match {
        case Validated.Valid(ir)       => ir
        case Validated.Invalid(errors) =>
          throw new AssertionError(errors.toList.mkString("; "))
      }

    assertEquals(
      SqlCodegenUuidTypeNames.fromSchema(extraction.schema, shapeIr),
      Set("Uuid")
    )
  }

  test("fromSchema - collects builtin String members typed directly as UUID columns (filtering is now in templates)") {
    val model      = SqlTestModelBuilder.assemble(
      """
            |use smithplates.codegen.sql#sqlAutoUuid
            |use smithplates.codegen.sql#sqlPrimaryKey
            |use smithplates.codegen.sql#sqlTable
            |
            |@sqlTable(name: "widgets")
            |structure Widget {
            |    @sqlPrimaryKey
            |    @sqlAutoUuid
            |    id: String
            |    label: String
            |}
            |""".stripMargin
    )
    val extraction = SqlModelExtractor.extractOrThrow(model)

    val shapeIr =
      SqlShapeIrExtractor.extract(model, extraction.tables.map(_.shapeId)) match {
        case Validated.Valid(ir)       => ir
        case Validated.Invalid(errors) =>
          throw new AssertionError(errors.toList.mkString("; "))
      }

    assertEquals(
      SqlCodegenUuidTypeNames.fromSchema(extraction.schema, shapeIr),
      Set("String")
    )
  }
}
