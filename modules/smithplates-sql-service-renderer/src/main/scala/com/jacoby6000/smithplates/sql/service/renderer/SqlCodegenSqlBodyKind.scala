package com.jacoby6000.smithplates.sql.service.renderer

import com.jacoby6000.smithplates.sql.service.core.SqlOperationSqlBinding

/** Resolved operation-body dispatch key for SSP {@code #match} on a single string attribute. */
object SqlCodegenSqlBodyKind {
  val InsertScalar: String             = "insertScalar"
  val InsertStructureDict: String      = "insertStructureDict"
  val InsertStructureIndex: String     = "insertStructureIndex"
  val BooleanMutationExists: String    = "booleanMutationExists"
  val BooleanMutationRowcount: String  = "booleanMutationRowcount"
  val BooleanStructureExists: String   = "booleanStructureExists"
  val BooleanStructureRowcount: String = "booleanStructureRowcount"
  val SelectOneClassRow: String        = "selectOneClassRow"
  val SelectOneDict: String            = "selectOneDict"
  val SelectOneIndex: String           = "selectOneIndex"

  val SelectOneJoinedFlat: String      = "selectOneJoinedFlat"
  val SelectOneJoinedAggregate: String = "selectOneJoinedAggregate"

  def resolve(sql: SqlOperationSqlBinding): Option[String] =
    resolveImpl(
      sql.queryKind,
      sql.outputKind,
      sql.executionMode,
      sql.selectOneOutput.isDefined,
      sql.selectOneOutput.exists(_.hasCollectionJoin),
      SqlCodegenSqlBindingMetadata.canUseClassRow(sql),
      SqlCodegenSqlBindingMetadata.usesDictRowFactory(sql)
    )

  def resolve(sql: SqlCodegenSqlBinding): Option[String] =
    resolveImpl(
      sql.queryKind,
      sql.outputKind,
      sql.executionMode,
      sql.selectOneOutput.isDefined,
      sql.selectOneOutput.exists(_.hasCollectionJoin),
      SqlCodegenSqlBindingMetadata.canUseClassRow(sql),
      SqlCodegenSqlBindingMetadata.usesDictRowFactory(sql)
    )

  private def resolveImpl(
      queryKind: String,
      outputKind: String,
      executionMode: String,
      hasSelectOneOutput: Boolean,
      hasCollectionJoin: Boolean,
      canClassRow: Boolean,
      usesDictRow: Boolean
  ): Option[String] =
    if (queryKind == "insert" && outputKind == "scalar") {
      Some(InsertScalar)
    } else if (queryKind == "insert" && outputKind == "structure") {
      if (usesDictRow) {
        Some(InsertStructureDict)
      } else {
        Some(InsertStructureIndex)
      }
    } else if (outputKind == "boolean") {
      if (executionMode == "rowcount") {
        Some(BooleanMutationRowcount)
      } else {
        Some(BooleanMutationExists)
      }
    } else if (outputKind == "booleanStructure") {
      if (executionMode == "rowcount") {
        Some(BooleanStructureRowcount)
      } else {
        Some(BooleanStructureExists)
      }
    } else if (queryKind == "selectOne" && hasSelectOneOutput) {
      if (hasCollectionJoin) {
        Some(SelectOneJoinedAggregate)
      } else {
        Some(SelectOneJoinedFlat)
      }
    } else if (queryKind == "selectOne") {
      if (canClassRow) {
        Some(SelectOneClassRow)
      } else if (usesDictRow) {
        Some(SelectOneDict)
      } else {
        Some(SelectOneIndex)
      }
    } else {
      None
    }
}
