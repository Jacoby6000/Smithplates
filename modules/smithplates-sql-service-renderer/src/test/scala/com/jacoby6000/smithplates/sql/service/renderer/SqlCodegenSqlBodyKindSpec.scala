package com.jacoby6000.smithplates.sql.service.renderer

import com.jacoby6000.smithplates.sql.service.query.renderer.SqlParameterizedStatement

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
      typeName = "String",
      readTypeName = "String"
    )

  test("resolve - insert structure always uses dict row factory") {
    val scalarFields = List(
      scalarField("id", 0),
      scalarField("created_at", 1)
    )
    assertEquals(
      SqlCodegenSqlBodyKind.resolve(sqlBinding("insert", "structure", resultFields = scalarFields)),
      Some(SqlCodegenSqlBodyKind.InsertStructureDict)
    )
  }

  test("resolve - update structure with returning uses insert structure paths") {
    val scalarFields = List(
      scalarField("id", 0),
      scalarField("updated_at", 1)
    )
    assertEquals(
      SqlCodegenSqlBodyKind.resolve(sqlBinding("update", "structure", resultFields = scalarFields)),
      Some(SqlCodegenSqlBodyKind.InsertStructureIndex)
    )
  }

  test("resolve - boolean mutation") {
    val mutationBinding =
      sqlBinding("delete", "structure").copy(mutationResultMemberName = Some("deleted"))
    assertEquals(
      SqlCodegenSqlBodyKind.resolve(mutationBinding),
      Some(SqlCodegenSqlBodyKind.BooleanMutationExists)
    )
    assertEquals(
      SqlCodegenSqlBodyKind.resolve(mutationBinding.copy(executionMode = "rowcount")),
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
