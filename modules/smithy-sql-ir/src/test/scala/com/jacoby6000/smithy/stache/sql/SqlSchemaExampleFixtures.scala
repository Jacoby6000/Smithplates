package com.jacoby6000.smithy.stache.sql

import software.amazon.smithy.model.Model

object SqlSchemaExampleFixtures {
  val exampleModelSmithy: (String, String) =
    "example-model.smithy" ->
      """
        |$version: "2.0"
        |
        |namespace stache.codegen.sql.example
        |
        |use stache.codegen.sql#sqlForeignKey
        |use stache.codegen.sql#sqlIndex
        |use stache.codegen.sql#sqlPrimaryKey
        |use stache.codegen.sql#sqlTable
        |use stache.codegen.sql#sqlVarchar
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
        |    @sqlForeignKey(references: "stache.codegen.sql.example#Bar")
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
    SqlIrExtractor.extractOrThrow(exampleModel)
}
