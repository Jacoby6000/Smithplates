package com.jacoby6000.smithy.stache.sql.sqlite

import com.jacoby6000.smithy.stache.sql.*
import munit.FunSuite

final class SqliteEnumRendererSpec extends FunSuite {
  test("Sqlite - renders CHECK constraints for enum columns") {
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

    val extraction = SqlModelExtractor.extractOrThrow(model)
    val ddl        = SqliteRenderer.render(extraction.schema, SqlServiceIr())

    assert(ddl.contains("direction TEXT CHECK(direction IN ('NORTH', 'SOUTH'))"))
  }
}
