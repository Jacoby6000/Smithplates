package com.jacoby6000.smithy.stache.sql.codegen

import com.jacoby6000.smithy.stache.sql.query.SqlParameterizedStatement

class SqlCodegenSqlBodyKindSpec extends munit.FunSuite {
  private def sqlBinding(
      queryKind: String,
      outputKind: String,
      executionMode: String = "fetchOne",
      resultFields: List[SqlCodegenResultField] = Nil
  ): SqlCodegenSqlBinding =
    SqlCodegenSqlBinding(
      queryKind = queryKind,
      sqlStatement = SqlParameterizedStatement(List("SELECT 1")),
      tableName = "widgets",
      bindParameters = Nil,
      executionMode = executionMode,
      outputKind = outputKind,
      returningColumnIndex = None,
      resultFields = resultFields
    )

  private def scalarField(name: String, index: Int): SqlCodegenResultField =
    SqlCodegenResultField(
      fieldName = name,
      columnName = name,
      columnIndex = index,
      typeName = "String"
    )

  test("resolve - insert scalar") {
    assertEquals(
      SqlCodegenSqlBodyKind.resolve(sqlBinding("insert", "scalar")),
      Some(SqlCodegenSqlBodyKind.InsertScalar)
    )
  }

  test("resolve - insert structure always uses dict row factory") {
    assertEquals(
      SqlCodegenSqlBodyKind.resolve(sqlBinding("insert", "structure")),
      Some(SqlCodegenSqlBodyKind.InsertStructureDict)
    )
  }

  test("resolve - boolean mutation") {
    assertEquals(
      SqlCodegenSqlBodyKind.resolve(sqlBinding("delete", "boolean")),
      Some(SqlCodegenSqlBodyKind.BooleanMutationExists)
    )
    assertEquals(
      SqlCodegenSqlBodyKind.resolve(sqlBinding("delete", "boolean", executionMode = "rowcount")),
      Some(SqlCodegenSqlBodyKind.BooleanMutationRowcount)
    )
  }

  test("resolve - select one class row vs dict row") {
    val scalarFields = List(
      scalarField("id", 0),
      scalarField("name", 1)
    )
    assertEquals(
      SqlCodegenSqlBodyKind.resolve(sqlBinding("selectOne", "structure", resultFields = scalarFields)),
      Some(SqlCodegenSqlBodyKind.SelectOneClassRow)
    )
    assertEquals(
      SqlCodegenSqlBodyKind.resolve(
        sqlBinding(
          "selectOne",
          "structure",
          resultFields = List(scalarField("id", 0), scalarField("payload", 1).copy(isJson = true))
        )
      ),
      Some(SqlCodegenSqlBodyKind.SelectOneDict)
    )
  }
}
