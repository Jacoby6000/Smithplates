package com.jacoby6000.smithplates.sql.service.renderer

import com.jacoby6000.smithplates.sql.service.core.SqlOperationSqlBinding

object SqlCodegenSqlBindingMetadata {
  def canUseClassRow(sql: SqlOperationSqlBinding): Boolean =
    sql.queryKind == "selectOne" &&
      sql.resultFields.nonEmpty &&
      sql.resultFields.forall(field => !field.isJson)

  def usesDictRowFactory(sql: SqlOperationSqlBinding): Boolean = {
    val classRow = canUseClassRow(sql)
    (sql.queryKind == "selectOne" && !classRow) ||
    (sql.queryKind == "insert" && sql.outputKind == "structure")
  }

  def canUseClassRow(sql: SqlCodegenSqlBinding): Boolean =
    sql.queryKind == "selectOne" &&
      sql.resultFields.nonEmpty &&
      sql.resultFields.forall(field => !field.isJson)

  def usesDictRowFactory(sql: SqlCodegenSqlBinding): Boolean = {
    val classRow = canUseClassRow(sql)
    (sql.queryKind == "selectOne" && !classRow) ||
    (sql.queryKind == "insert" && sql.outputKind == "structure")
  }
}
