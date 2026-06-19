package com.jacoby6000.smithplates.testkit

import com.jacoby6000.smithplates.sql.model.SqlSchema
import com.jacoby6000.smithplates.sql.service.SqlModelExtractor
import software.amazon.smithy.model.Model

object SqlIntegrationSchemas {
  val Namespace: String = "smithplates.codegen.sql.it"

  private val traitUses: String =
    """use smithplates.codegen.sql#sqlForeignKey
      |use smithplates.codegen.sql#sqlIndex
      |use smithplates.codegen.sql#sqlPrimaryKey
      |use smithplates.codegen.sql#sqlTable
      |use smithplates.codegen.sql#sqlVarchar
      |""".stripMargin

  private lazy val simpleModel: Model =
    SqlItModelLoader.assemble(
      "simple.smithy" ->
        s"""$$version: "2.0"
           |namespace $Namespace
           |
           |$traitUses
           |
           |@sqlTable(name: "categories")
           |structure Category {
           |    @sqlPrimaryKey
           |    id: String
           |    name: String
           |}
           |
           |@sqlTable(name: "items")
           |structure Item {
           |    @sqlPrimaryKey
           |    id: String
           |    @sqlForeignKey(references: "$Namespace#Category")
           |    category_id: String
           |    @sqlVarchar(maxLength: 64)
           |    name: String
           |}
           |""".stripMargin
    )

  private lazy val varcharCheckModel: Model =
    SqlItModelLoader.assemble(
      "varchar-check.smithy" ->
        s"""$$version: "2.0"
           |namespace $Namespace
           |
           |$traitUses
           |
           |@sqlTable(name: "categories")
           |structure Category {
           |    @sqlPrimaryKey
           |    id: String
           |}
           |
           |@sqlTable(name: "items")
           |structure Item {
           |    @sqlPrimaryKey
           |    id: String
           |    @sqlForeignKey(references: "$Namespace#Category")
           |    category_id: String
           |    @sqlVarchar(maxLength: 5)
           |    name: String
           |}
           |""".stripMargin
    )

  private lazy val complexModel: Model =
    SqlItModelLoader.assemble(
      "complex.smithy" ->
        s"""$$version: "2.0"
           |namespace $Namespace
           |
           |$traitUses
           |
           |@sqlTable(name: "regions")
           |structure Region {
           |    @sqlPrimaryKey
           |    id: String
           |    name: String
           |}
           |
           |@sqlTable(name: "offices")
           |structure Office {
           |    @sqlPrimaryKey
           |    id: String
           |    @sqlForeignKey(references: "$Namespace#Region")
           |    region_id: String
           |    name: String
           |}
           |
           |@sqlTable(name: "teams")
           |structure Team {
           |    @sqlPrimaryKey
           |    id: String
           |    @sqlForeignKey(references: "$Namespace#Office")
           |    office_id: String
           |    name: String
           |}
           |
           |@sqlTable(name: "people")
           |structure Person {
           |    @sqlPrimaryKey
           |    id: String
           |    @sqlForeignKey(references: "$Namespace#Team")
           |    team_id: String
           |    @sqlForeignKey(references: "$Namespace#Office")
           |    home_office_id: String
           |    name: String
           |}
           |
           |@sqlTable(name: "projects")
           |structure Project {
           |    @sqlPrimaryKey
           |    id: String
           |    @sqlForeignKey(references: "$Namespace#Team")
           |    owning_team_id: String
           |    title: String
           |}
           |
           |@sqlTable(name: "memberships")
           |structure Membership {
           |    @sqlPrimaryKey
           |    id: String
           |    @sqlForeignKey(references: "$Namespace#Person")
           |    person_id: String
           |    @sqlForeignKey(references: "$Namespace#Project")
           |    project_id: String
           |    @sqlForeignKey(references: "$Namespace#Team")
           |    team_id: String
           |    role: String
           |}
           |
           |@sqlTable(name: "tags")
           |structure Tag {
           |    @sqlPrimaryKey
           |    id: String
           |    @sqlVarchar(maxLength: 32)
           |    label: String
           |}
           |
           |@sqlTable(name: "project_tags")
           |structure ProjectTag {
           |    @sqlPrimaryKey
           |    id: String
           |    @sqlForeignKey(references: "$Namespace#Project")
           |    project_id: String
           |    @sqlForeignKey(references: "$Namespace#Tag")
           |    tag_id: String
           |}
           |""".stripMargin
    )

  private def extractSchema(model: Model): SqlSchema =
    SqlModelExtractor.extractOrThrow(model).schema

  lazy val simpleSchema: SqlSchema       = extractSchema(simpleModel)
  lazy val varcharCheckSchema: SqlSchema = extractSchema(varcharCheckModel)
  lazy val complexSchema: SqlSchema      = extractSchema(complexModel)

  val simpleTableNames: Set[String]  = Set("categories", "items")
  val complexTableNames: Set[String] =
    Set("regions", "offices", "teams", "people", "projects", "memberships", "tags", "project_tags")

  val simpleForeignKeyCount: Int  = 1
  val complexForeignKeyCount: Int = 10
}
