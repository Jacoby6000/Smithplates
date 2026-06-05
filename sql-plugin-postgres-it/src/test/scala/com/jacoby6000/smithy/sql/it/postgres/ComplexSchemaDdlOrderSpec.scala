package com.jacoby6000.smithy.sql.it.postgres

import com.jacoby6000.smithy.sql.it.{SqlDdlSupport, SqlIntegrationSchemas}
import com.jacoby6000.smithy.sql.postgres.PostgresRenderer
import com.jacoby6000.smithy.sql.shared.SqlTableTree
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
    val ddl = SqlDdlSupport.renderDdl(PostgresRenderer, SqlIntegrationSchemas.complexSchema)
    val statements = SqlDdlSupport.splitStatements(ddl)

    def createTableIndex(tableName: String): Int =
      statements.indexWhere(_.matches(s"(?s).*CREATE TABLE\\s+${java.util.regex.Pattern.quote(tableName)}\\s*\\(.*"))

    assert(createTableIndex("offices") < createTableIndex("teams"), ddl)
    assert(createTableIndex("teams") < createTableIndex("people"), ddl)
    assert(createTableIndex("people") < createTableIndex("memberships"), ddl)
  }
}
