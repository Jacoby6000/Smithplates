package com.jacoby6000.smithy.stache.sql.postgres

import com.jacoby6000.smithy.stache.sql.{SqlModelExtractor, SqlTestModelBuilder}
import com.jacoby6000.smithy.stache.sql.shared.SqlRenderOutput
import munit.FunSuite
import software.amazon.smithy.model.shapes.ShapeId

final class PostgresEnumRendererSpec extends FunSuite {
  test("Postgres - renders CREATE TYPE for string enums before tables") {
    val model = SqlTestModelBuilder.assemble(
      """use stache.codegen.sql#sqlPrimaryKey
        |use stache.codegen.sql#sqlTable
        |
        |enum Direction {
        |    NORTH
        |    SOUTH
        |}
        |
        |@sqlTable(name: "routes")
        |structure Route {
        |    @sqlPrimaryKey
        |    id: String
        |    direction: Direction
        |}""".stripMargin
    )

    val schema = SqlModelExtractor.extractOrThrow(model)
    val enumDdl =
      SqlRenderOutput
        .ddlUnit(PostgresRenderer.renderUnits(schema), ShapeId.from("example#Direction"))
        .map(_.statement)
        .getOrElse(fail("CREATE TYPE for example#Direction was not rendered"))

    assertEquals(enumDdl, "CREATE TYPE example_direction AS ENUM ('NORTH', 'SOUTH');")

    val routeDdl =
      SqlRenderOutput
        .ddlUnit(PostgresRenderer.renderUnits(schema), ShapeId.from("example#Route"))
        .map(_.statement)
        .getOrElse(fail("CREATE TABLE for example#Route was not rendered"))

    assert(routeDdl.contains("direction example_direction"))
  }
}
