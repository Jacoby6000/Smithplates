package com.jacoby6000.smithy.stache.sql.codegen

import com.jacoby6000.smithy.stache.sql.PostgresDialect
import com.jacoby6000.smithy.stache.sql.SqlDialect
import com.jacoby6000.smithy.stache.sql.SqliteDialect

object SqlCodegenDialectConfig {
  def implementationClassName(serviceName: String, dialect: SqlDialect): String =
    dialect match {
      case SqliteDialect   => s"${serviceName}AiosqliteService"
      case PostgresDialect => s"${serviceName}PsycopgService"
    }

  def implementationModuleName(serviceFileName: String, dialect: SqlDialect): String =
    s"$serviceFileName${implementationModuleSuffix(dialect)}"

  def implementationModuleSuffix(dialect: SqlDialect): String =
    dialect match {
      case SqliteDialect   => "_aiosqlite"
      case PostgresDialect => "_psycopg"
    }

  def rowTypeName(dialect: SqlDialect): String =
    dialect match {
      case SqliteDialect   => "sqlite3.Row"
      case PostgresDialect => "tuple[object, ...]"
    }

  def rowReaderModuleImport(dialect: SqlDialect): Option[String] =
    dialect match {
      case SqliteDialect   => Some("import sqlite3")
      case PostgresDialect => None
    }

  def usesUuidRowConversion(dialect: SqlDialect): Boolean =
    dialect == PostgresDialect
}
