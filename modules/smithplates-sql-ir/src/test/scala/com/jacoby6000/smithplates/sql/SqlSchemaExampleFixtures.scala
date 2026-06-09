package com.jacoby6000.smithplates.sql

import com.jacoby6000.smithplates.sql.model.*
import software.amazon.smithy.model.Model

object SqlSchemaExampleFixtures {
  val exampleModelSmithy: (String, String) =
    "example-model.smithy" ->
      """
        |$version: "2.0"
        |
        |namespace smithplates.codegen.sql.example
        |
        |use smithplates.codegen.sql#sqlForeignKey
        |use smithplates.codegen.sql#sqlIndex
        |use smithplates.codegen.sql#sqlPrimaryKey
        |use smithplates.codegen.sql#sqlTable
        |use smithplates.codegen.sql#sqlVarchar
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
        |    @sqlForeignKey(references: "smithplates.codegen.sql.example#Bar")
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
