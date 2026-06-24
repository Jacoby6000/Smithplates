package com.jacoby6000.smithplates.sql.service.core

/** Model-level SQL feature metadata carried on [[com.jacoby6000.smithplates.codegen.core.ModelMeta]].
  *
  * `#36` extraction populates [[SqlTableMeta]] (table name) only. Column, JSON, and per-member SQL facts remain in
  * legacy `SqlSchema` until `#39` (SQL codegen cutover) extends this ADT.
  */
sealed trait SqlMeta

object SqlMeta {
  final case class SqlTableMeta(tableName: String) extends SqlMeta

  case object SqlNestedField extends SqlMeta
}

/** Service-level SQL feature metadata. */
final case class SqlServiceMeta()

/** Operation-level SQL feature metadata. */
final case class SqlOperationMeta()
