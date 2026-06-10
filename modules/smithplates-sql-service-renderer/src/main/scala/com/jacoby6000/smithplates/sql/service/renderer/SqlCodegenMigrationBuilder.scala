package com.jacoby6000.smithplates.sql.service.renderer

import com.jacoby6000.smithplates.sql.ddl.renderer.common.SqlSchemaDdlRenderer
import com.jacoby6000.smithplates.sql.ddl.renderer.common.SqlShared
import com.jacoby6000.smithplates.sql.model.SqlSchema

object SqlCodegenMigrationBuilder {
  val InitialMigrationVersion: String    = "v1"
  val InitialMigrationFileName: String   = "v1_initial_schema.sql"
  val InitialMigrationVersionNumber: Int = 1
  val StateTableName: String             = "_smithplates_migrations"

  def migrationsDirectoryFromTestFile(migrationsDirectory: String): String = {
    val parts    =
      migrationsDirectory
        .stripPrefix("/")
        .stripSuffix("/")
        .stripSuffix("\\")
        .split("/")
        .filter(_.nonEmpty)
    val pathExpr = parts.map(part => s""""$part"""").mkString(" / ")
    s"""Path(__file__).resolve().parents[3] / $pathExpr"""
  }

  def build(
      schema: SqlSchema,
      dialectKey: String,
      schemaDdlRenderers: Map[String, SqlSchemaDdlRenderer],
      migrationsDirectory: String
  ): Option[SqlCodegenMigrationContext] =
    if (schema.tables.isEmpty) {
      None
    } else {
      val ddlRenderer =
        schemaDdlRenderers.getOrElse(
          dialectKey,
          throw new IllegalStateException(s"schema DDL renderer for dialect '$dialectKey' is required")
        )
      val ddl         = SqlShared.formatDdlStatements(ddlRenderer.renderSchemaDdlStatements(schema))
      Some(
        SqlCodegenMigrationContext(
          migrationsDirectory = migrationsDirectory,
          migrationsDirectoryFromTestFile = migrationsDirectoryFromTestFile(migrationsDirectory),
          stateTableDdl = stateTableDdl(dialectKey),
          migrations = List(
            SqlCodegenMigrationEntry(
              version = InitialMigrationVersion,
              versionNumber = InitialMigrationVersionNumber,
              fileName = InitialMigrationFileName,
              schemaHash = SqlSchemaHash.sha256Hex(ddl),
              sql = ddl
            )
          )
        )
      )
    }

  def stateTableDdl(dialectKey: String): String =
    dialectKey match {
      case "sqlite"   =>
        s"""CREATE TABLE IF NOT EXISTS $StateTableName (
           |    version TEXT NOT NULL PRIMARY KEY,
           |    schema_hash TEXT NOT NULL,
           |    applied_at TEXT NOT NULL DEFAULT (datetime('now'))
           |);""".stripMargin
      case "postgres" =>
        s"""CREATE TABLE IF NOT EXISTS $StateTableName (
           |    version TEXT NOT NULL PRIMARY KEY,
           |    schema_hash TEXT NOT NULL,
           |    applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
           |);""".stripMargin
      case other      =>
        throw new IllegalArgumentException(s"unsupported dialect key: $other")
    }
}
