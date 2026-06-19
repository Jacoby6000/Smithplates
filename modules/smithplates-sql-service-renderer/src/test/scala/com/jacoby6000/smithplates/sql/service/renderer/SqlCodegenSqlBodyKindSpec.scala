package com.jacoby6000.smithplates.sql.service.renderer

import com.jacoby6000.smithplates.sql.service.query.renderer.SqlParameterizedStatement

class SqlCodegenSqlBodyKindSpec extends munit.FunSuite {
  test("resolve - insert scalar") {
    assertEquals(
      SqlCodegenSqlBodyKind.resolve(
        SqlCodegenSqlBodyKindSpec.internal.sqlBinding("insert", "scalar")
      ),
      Some(SqlCodegenSqlBodyKind.InsertScalar)
    )
  }

  test("resolve - insert structure always uses dict row factory") {
    assertEquals(
      SqlCodegenSqlBodyKind.resolve(
        SqlCodegenSqlBodyKindSpec.internal.sqlBinding("insert", "structure")
      ),
      Some(SqlCodegenSqlBodyKind.InsertStructureDict)
    )
  }

  test("resolve - boolean mutation") {
    assertEquals(
      SqlCodegenSqlBodyKind.resolve(
        SqlCodegenSqlBodyKindSpec.internal.sqlBinding("delete", "boolean")
      ),
      Some(SqlCodegenSqlBodyKind.BooleanMutationExists)
    )
    assertEquals(
      SqlCodegenSqlBodyKind.resolve(
        SqlCodegenSqlBodyKindSpec.internal.sqlBinding("delete", "boolean", executionMode = "rowcount")
      ),
      Some(SqlCodegenSqlBodyKind.BooleanMutationRowcount)
    )
  }

  test("resolve - select one class row vs dict row") {
    val scalarFields = List(
      SqlCodegenSqlBodyKindSpec.internal.scalarField("id", 0),
      SqlCodegenSqlBodyKindSpec.internal.scalarField("name", 1)
    )
    assertEquals(
      SqlCodegenSqlBodyKind.resolve(
        SqlCodegenSqlBodyKindSpec.internal.sqlBinding("selectOne", "structure", resultFields = scalarFields)
      ),
      Some(SqlCodegenSqlBodyKind.SelectOneClassRow)
    )
    assertEquals(
      SqlCodegenSqlBodyKind.resolve(
        SqlCodegenSqlBodyKindSpec.internal.sqlBinding(
          "selectOne",
          "structure",
          resultFields = List(
            SqlCodegenSqlBodyKindSpec.internal.scalarField("id", 0),
            SqlCodegenSqlBodyKindSpec.internal.scalarField("payload", 1).copy(isJson = true)
          )
        )
      ),
      Some(SqlCodegenSqlBodyKind.SelectOneDict)
    )
  }
}

object SqlCodegenSqlBodyKindSpec {

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def sqlBinding(
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

    def scalarField(name: String, index: Int): SqlCodegenResultField =
      SqlCodegenResultField(
        fieldName = name,
        columnName = name,
        columnIndex = index,
        typeName = "String",
        readTypeName = "String"
      )
  }
}
