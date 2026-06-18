package com.jacoby6000.smithplates.sql.service.renderer

/** Resolved operation-body dispatch key for SSP {@code #match} on a single string attribute. */
object SqlCodegenSqlBodyKind {
  val InsertStructureDict: String     = "insertStructureDict"
  val InsertStructureIndex: String    = "insertStructureIndex"
  val BooleanMutationExists: String   = "booleanMutationExists"
  val BooleanMutationRowcount: String = "booleanMutationRowcount"
  val SelectOneClassRow: String       = "selectOneClassRow"
  val SelectOneDict: String           = "selectOneDict"
  val SelectOneIndex: String          = "selectOneIndex"

  val SelectOneJoinedFlat: String      = "selectOneJoinedFlat"
  val SelectOneJoinedAggregate: String = "selectOneJoinedAggregate"

  def resolve(sql: SqlCodegenSqlBinding): Option[String] =
    if ((sql.queryKind == "insert" || sql.queryKind == "update") &&
      sql.outputKind == "structure" &&
      sql.resultFields.nonEmpty) {
      if (SqlCodegenSqlBindingMetadata.usesDictRowFactory(sql)) {
        Some(InsertStructureDict)
      } else {
        Some(InsertStructureIndex)
      }
    } else if (sql.mutationResultMemberName.isDefined) {
      if (sql.executionMode == "rowcount") {
        Some(BooleanMutationRowcount)
      } else {
        Some(BooleanMutationExists)
      }
    } else if (sql.queryKind == "selectOne" && sql.selectOneOutput.isDefined) {
      if (sql.selectOneOutput.exists(_.hasCollectionJoin)) {
        Some(SelectOneJoinedAggregate)
      } else {
        Some(SelectOneJoinedFlat)
      }
    } else if (sql.queryKind == "selectOne") {
      if (SqlCodegenSqlBindingMetadata.canUseClassRow(sql)) {
        Some(SelectOneClassRow)
      } else if (SqlCodegenSqlBindingMetadata.usesDictRowFactory(sql)) {
        Some(SelectOneDict)
      } else {
        Some(SelectOneIndex)
      }
    } else {
      None
    }
}
