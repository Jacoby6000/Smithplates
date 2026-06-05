package com.jacoby6000.smithy.stache.sql.postgres

import com.jacoby6000.smithy.stache.sql.shared.DDLStatement
import com.jacoby6000.smithy.stache.sql.shared.SqlTableTree
import com.jacoby6000.smithy.stache.testkit.SqlDdlSupport
import com.jacoby6000.smithy.stache.testkit.SqlIntegrationSchemas
import munit.FunSuite

final class ComplexSchemaDdlOrderSpec extends FunSuite {
  test("complex schema tables are ordered by foreign key dependencies") {
    val ordered =
      SqlTableTree.tablesInRenderOrder(SqlIntegrationSchemas.complexSchema).map(_.name)
    assert(ordered.indexOf("offices") < ordered.indexOf("teams"), ordered.mkString(", "))
    assert(ordered.indexOf("teams") < ordered.indexOf("people"), ordered.mkString(", "))
    assert(ordered.indexOf("people") < ordered.indexOf("memberships"), ordered.mkString(", "))
  }

  test("complex schema DDL creates referenced tables before dependents") {
    val statements = SqlDdlSupport.renderSchemaDdlStatements(PostgresRenderer, SqlIntegrationSchemas.complexSchema)

    def createTableIndex(tableName: String): Int =
      statements.indexWhere {
        case DDLStatement.CreateTable(table, _) => table.name == tableName
        case _                                  => false
      }

    assert(createTableIndex("offices") < createTableIndex("teams"))
    assert(createTableIndex("teams") < createTableIndex("people"))
    assert(createTableIndex("people") < createTableIndex("memberships"))
  }
}
