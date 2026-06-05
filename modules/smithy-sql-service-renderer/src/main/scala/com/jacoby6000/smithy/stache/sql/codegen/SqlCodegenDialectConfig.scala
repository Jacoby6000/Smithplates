package com.jacoby6000.smithy.stache.sql.codegen

object SqlCodegenDialectConfig {
  def implementationClassName(serviceName: String, dialectKey: String): String =
    dialectKey match {
      case "sqlite"   => s"${serviceName}AiosqliteService"
      case "postgres" => s"${serviceName}PsycopgService"
      case other      => throw new IllegalArgumentException(s"unsupported dialect key: $other")
    }

  def implementationModuleName(serviceFileName: String, dialectKey: String): String =
    s"$serviceFileName${implementationModuleSuffix(dialectKey)}"

  def implementationModuleSuffix(dialectKey: String): String =
    dialectKey match {
      case "sqlite"   => "_aiosqlite"
      case "postgres" => "_psycopg"
      case other      => throw new IllegalArgumentException(s"unsupported dialect key: $other")
    }

  def rowTypeName(dialectKey: String): String =
    dialectKey match {
      case "sqlite"   => "sqlite3.Row"
      case "postgres" => "tuple[object, ...]"
      case other      => throw new IllegalArgumentException(s"unsupported dialect key: $other")
    }

  def rowReaderModuleImport(dialectKey: String): Option[String] =
    dialectKey match {
      case "sqlite"   => Some("import sqlite3")
      case "postgres" => None
      case other      => throw new IllegalArgumentException(s"unsupported dialect key: $other")
    }

  def usesUuidRowConversion(dialectKey: String): Boolean =
    dialectKey == "postgres"
}
