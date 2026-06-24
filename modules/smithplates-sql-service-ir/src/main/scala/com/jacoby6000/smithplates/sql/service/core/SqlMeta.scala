package com.jacoby6000.smithplates.sql.service.core

/** Model-level SQL feature metadata carried on [[com.jacoby6000.smithplates.codegen.core.ModelMeta]]. */
sealed trait SqlMeta

object SqlMeta {
  final case class SqlTableMeta(tableName: String) extends SqlMeta

  case object SqlNestedField extends SqlMeta
}

/** Service-level SQL feature metadata. */
final case class SqlServiceMeta()

/** Operation-level SQL feature metadata. */
final case class SqlOperationMeta()
