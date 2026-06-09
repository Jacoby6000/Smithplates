package com.jacoby6000.smithplates.sql.model
import software.amazon.smithy.model.shapes.ShapeId

/** One executable DDL statement produced by dialect schema rendering. */
sealed trait DDLStatement {
  def statement: String
  def shapeId: ShapeId

  def formatted: String =
    s"-- ${shapeId.toString}\n$statement"
}

object DDLStatement {
  final case class CreateTable(table: SqlTable, statement: String) extends DDLStatement {
    override val shapeId: ShapeId = table.shapeId
  }

  final case class CreateIndex(table: SqlTable, index: SqlIndex, statement: String) extends DDLStatement {
    override val shapeId: ShapeId = table.shapeId
  }

  final case class CreateEnumType(enumType: SqlColumnType.StringEnum, statement: String) extends DDLStatement {
    override val shapeId: ShapeId = enumType.shapeId
  }
}
