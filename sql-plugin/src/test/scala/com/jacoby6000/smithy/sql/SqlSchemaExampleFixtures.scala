package com.jacoby6000.smithy.sql

import software.amazon.smithy.model.Model

object SqlSchemaExampleFixtures {
  val exampleModelSmithy: (String, String) =
    "example-model.smithy" ->
      """
        |$version: "2.0"
        |
        |namespace jacoby6000.codegen.sql.example
        |
        |use jacoby6000.codegen.sql#sqlForeignKey
        |use jacoby6000.codegen.sql#sqlIndex
        |use jacoby6000.codegen.sql#sqlPrimaryKey
        |use jacoby6000.codegen.sql#sqlTable
        |use jacoby6000.codegen.sql#sqlVarchar
        |
        |@sqlTable(name: "bars")
        |structure Bar {
        |    @sqlPrimaryKey
        |    id: String
        |    name: String
        |}
        |
        |@sqlTable(name: "foos")
        |structure Foo {
        |    @sqlPrimaryKey
        |    id: String
        |    @sqlForeignKey(references: "jacoby6000.codegen.sql.example#Bar")
        |    bar_id: String
        |    @sqlVarchar(maxLength: 128)
        |    name: String
        |    size_bytes: Long
        |    payload: Document
        |    @sqlIndex(name: "idx_foos_created_at")
        |    created_at: Timestamp
        |}
        |""".stripMargin

  lazy val exampleModel: Model =
    SqlTestModelLoader.assemble(exampleModelSmithy)

  lazy val exampleSchema: SqlSchema =
    SqlModelExtractor.extractOrThrow(exampleModel)
}
