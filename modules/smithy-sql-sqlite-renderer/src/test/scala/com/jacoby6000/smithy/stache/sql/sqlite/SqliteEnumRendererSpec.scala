package com.jacoby6000.smithy.stache.sql.sqlite

import com.jacoby6000.smithy.stache.sql.*
import com.jacoby6000.smithy.stache.sql.shared.SqlShared
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

    val schema = SqlIrExtractor.extractOrThrow(model)
    val ddl    = SqlShared.formatDdlStatements(SqliteRenderer.renderSchemaDdlStatements(schema))

    assert(ddl.contains("direction TEXT CHECK(direction IN ('NORTH', 'SOUTH'))"))
  }
}
