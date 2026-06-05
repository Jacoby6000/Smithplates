package com.jacoby6000.smithy.stache.sql

import com.jacoby6000.smithy.stache.sql.traits.SqlDeriveDeleteTrait
import com.jacoby6000.smithy.stache.sql.traits.SqlDeriveInsertTrait
import com.jacoby6000.smithy.stache.sql.traits.SqlDeriveSelectOneTrait
import com.jacoby6000.smithy.stache.sql.traits.SqlDeriveSelectTrait
import com.jacoby6000.smithy.stache.sql.traits.SqlDeriveUpdateTrait
import com.jacoby6000.smithy.stache.sql.traits.SqlServiceTrait
import com.jacoby6000.smithy.stache.sql.traits.SqlUpdateTrait
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.traits.Trait

import scala.jdk.OptionConverters.*

private[sql] object SmithySqlServiceTraitAccess {
  def sqlDeriveInsert(operation: OperationShape): Option[SqlDeriveInsertTrait] =
    traitOption(operation, classOf[SqlDeriveInsertTrait])

  def sqlDeriveUpdate(operation: OperationShape): Option[SqlDeriveUpdateTrait] =
    traitOption(operation, classOf[SqlDeriveUpdateTrait])

  def sqlDeriveDelete(operation: OperationShape): Option[SqlDeriveDeleteTrait] =
    traitOption(operation, classOf[SqlDeriveDeleteTrait])

  def sqlDeriveSelectOne(operation: OperationShape): Option[SqlDeriveSelectOneTrait] =
    traitOption(operation, classOf[SqlDeriveSelectOneTrait])

  def sqlDeriveSelect(operation: OperationShape): Option[SqlDeriveSelectTrait] =
    traitOption(operation, classOf[SqlDeriveSelectTrait])

  def sqlUpdate(structure: StructureShape): Option[SqlUpdateTrait] =
    traitOption(structure, classOf[SqlUpdateTrait])

  def sqlService(service: ServiceShape): Option[SqlServiceTrait] =
    traitOption(service, classOf[SqlServiceTrait])

  private def traitOption[T <: Trait](shape: Shape, clazz: Class[T]): Option[T] =
    shape.getTrait(clazz).toScala
}
