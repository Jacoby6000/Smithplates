package com.jacoby6000.smithplates.sql

import software.amazon.smithy.model.Model

/** Builds minimal Smithy models for sql-plugin unit tests. */
object SqlTestModelBuilder {
  val Namespace: String = "example"

  def structureId(structureName: String): String = s"$Namespace#$structureName"

  def assemble(body: String): Model =
    SqlTestModelLoader.assemble(
      "model.smithy" ->
        s"""$$version: "2.0"
           |namespace $Namespace
           |
           |${body.trim}
           |""".stripMargin
    )
}
