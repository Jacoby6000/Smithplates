package com.jacoby6000.smithplates.sql.model

import software.amazon.smithy.model.shapes.ShapeId

final case class SqlSchema(
    tables: List[SqlTable],
    relationships: List[SqlRelationship] = Nil
) {
  def columnType(tableShapeId: ShapeId, columnName: String): Option[SqlColumnType] =
    tables
      .find(_.shapeId == tableShapeId)
      .flatMap(_.columns.find(_.name == columnName))
      .map(_.columnType)
}

final case class SqlTable(
    name: String,
    shapeId: ShapeId,
    columns: List[SqlColumn],
    primaryKeys: List[String],
    foreignKeys: List[SqlForeignKey],
    indexes: List[SqlIndex]
)

final case class SqlColumn(
    name: String,
    columnType: SqlColumnType,
    nullable: Boolean,
    autoGeneration: Option[SqlAutoGeneration] = None
)

sealed trait SqlAutoGeneration

case object SqlAutoUuid extends SqlAutoGeneration

case object SqlCreatedTimestamp extends SqlAutoGeneration

case object SqlUpdatedTimestamp extends SqlAutoGeneration

final case class SqlForeignKey(
    column: String,
    referencesShape: ShapeId,
    referencesColumn: String,
    cardinality: SqlRelationshipCardinality = SqlRelationshipCardinality.ManyToOne
)

sealed trait SqlRelationshipCardinality

object SqlRelationshipCardinality {
  case object ManyToOne extends SqlRelationshipCardinality
  case object OneToOne  extends SqlRelationshipCardinality
}

final case class SqlRelationship(
    sourceTable: ShapeId,
    sourceColumn: String,
    targetTable: ShapeId,
    targetColumn: String,
    cardinality: SqlRelationshipCardinality
)

final case class SqlIndex(
    name: Option[String],
    columns: List[String],
    unique: Boolean = false
)
