package com.jacoby6000.smithplates.sql.service

import com.jacoby6000.smithplates.sql.*
import com.jacoby6000.smithplates.sql.service.traits.SqlDeriveDeleteTrait
import com.jacoby6000.smithplates.sql.service.traits.SqlDeriveInsertTrait
import com.jacoby6000.smithplates.sql.service.traits.SqlDeriveSelectOneTrait
import com.jacoby6000.smithplates.sql.service.traits.SqlDeriveSelectTrait
import com.jacoby6000.smithplates.sql.service.traits.SqlDeriveUpdateTrait
import com.jacoby6000.smithplates.sql.service.traits.SqlServiceTrait
import com.jacoby6000.smithplates.sql.service.traits.SqlUpdateTrait
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.traits.Trait

import scala.jdk.OptionConverters.*

private object SmithySqlServiceTraitLookup {
  def traitOption[T <: Trait](shape: Shape, clazz: Class[T]): Option[T] =
    shape.getTrait(clazz).toScala
}

private[service] object SmithySqlServiceTraitAccess {
  extension (operation: OperationShape) {
    def sqlDeriveInsert: Option[SqlDeriveInsertTrait] =
      SmithySqlServiceTraitLookup.traitOption(operation, classOf[SqlDeriveInsertTrait])

    def sqlDeriveUpdate: Option[SqlDeriveUpdateTrait] =
      SmithySqlServiceTraitLookup.traitOption(operation, classOf[SqlDeriveUpdateTrait])

    def sqlDeriveDelete: Option[SqlDeriveDeleteTrait] =
      SmithySqlServiceTraitLookup.traitOption(operation, classOf[SqlDeriveDeleteTrait])

    def sqlDeriveSelectOne: Option[SqlDeriveSelectOneTrait] =
      SmithySqlServiceTraitLookup.traitOption(operation, classOf[SqlDeriveSelectOneTrait])

    def sqlDeriveSelect: Option[SqlDeriveSelectTrait] =
      SmithySqlServiceTraitLookup.traitOption(operation, classOf[SqlDeriveSelectTrait])
  }

  extension (structure: StructureShape) {
    def sqlUpdate: Option[SqlUpdateTrait] =
      SmithySqlServiceTraitLookup.traitOption(structure, classOf[SqlUpdateTrait])
  }

  extension (service: ServiceShape) {
    def sqlService: Option[SqlServiceTrait] =
      SmithySqlServiceTraitLookup.traitOption(service, classOf[SqlServiceTrait])
  }
}
