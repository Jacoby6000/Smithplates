package com.jacoby6000.smithy.stache.sql.shared

import com.jacoby6000.smithy.stache.sql.SqlColumnType
import com.jacoby6000.smithy.stache.sql.SqlIndex
import com.jacoby6000.smithy.stache.sql.SqlTable
import software.amazon.smithy.model.shapes.ShapeId

/** One executable DDL statement produced by dialect schema rendering. */
sealed trait DDLStatement {
  def statement: String
  def shapeId: ShapeId
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
