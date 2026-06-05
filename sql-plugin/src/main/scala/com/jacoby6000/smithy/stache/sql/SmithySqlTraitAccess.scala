package com.jacoby6000.smithy.stache.sql

import com.jacoby6000.smithy.stache.sql.shared.SqlShared
import com.jacoby6000.smithy.stache.sql.traits.SqlAutoUuidTrait
import com.jacoby6000.smithy.stache.sql.traits.SqlColumnIndexTrait
import com.jacoby6000.smithy.stache.sql.traits.SqlColumnTrait
import com.jacoby6000.smithy.stache.sql.traits.SqlCreatedTimestampTrait
import com.jacoby6000.smithy.stache.sql.traits.SqlDeriveDeleteTrait
import com.jacoby6000.smithy.stache.sql.traits.SqlDeriveInsertTrait
import com.jacoby6000.smithy.stache.sql.traits.SqlDeriveSelectOneTrait
import com.jacoby6000.smithy.stache.sql.traits.SqlDeriveSelectTrait
import com.jacoby6000.smithy.stache.sql.traits.SqlDeriveUpdateTrait
import com.jacoby6000.smithy.stache.sql.traits.SqlForeignKeyTrait
import com.jacoby6000.smithy.stache.sql.traits.SqlIndexTrait
import com.jacoby6000.smithy.stache.sql.traits.SqlJsonTrait
import com.jacoby6000.smithy.stache.sql.traits.SqlPrimaryKeyTrait
import com.jacoby6000.smithy.stache.sql.traits.SqlServiceTrait
import com.jacoby6000.smithy.stache.sql.traits.SqlTableTrait
import com.jacoby6000.smithy.stache.sql.traits.SqlUniqueIndexTrait
import com.jacoby6000.smithy.stache.sql.traits.SqlUpdateTrait
import com.jacoby6000.smithy.stache.sql.traits.SqlUpdatedTimestampTrait
import com.jacoby6000.smithy.stache.sql.traits.SqlUuidTrait
import com.jacoby6000.smithy.stache.sql.traits.SqlVarcharTrait
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.traits.Trait

import scala.jdk.OptionConverters.*

private[sql] object SmithySqlTraitAccess {
  def sqlTableStructure(structure: StructureShape): Option[SqlTableTrait] =
    traitOption(structure, classOf[SqlTableTrait])

  def sqlColumn(member: MemberShape): Option[SqlColumnTrait] =
    traitOption(member, classOf[SqlColumnTrait])

  def sqlColumnIndex(member: MemberShape): Option[Int] =
    traitOption(member, classOf[SqlColumnIndexTrait]).map(_.getIndex)

  def sqlVarchar(member: MemberShape): Option[SqlVarcharTrait] =
    traitOption(member, classOf[SqlVarcharTrait])

  def sqlVarchar(shape: Shape): Option[SqlVarcharTrait] =
    traitOption(shape, classOf[SqlVarcharTrait])

  def sqlVarchar(model: Model, member: MemberShape): Option[SqlVarcharTrait] =
    traitOnMemberOrTarget(model, member, sqlVarchar)

  def sqlUuid(member: MemberShape): Boolean =
    sqlUuid(member: Shape)

  def sqlUuid(shape: Shape): Boolean =
    traitPresent(shape, classOf[SqlUuidTrait]) ||
      shape.asMemberShape.toScala.exists(sqlAutoUuid)

  def sqlUuid(model: Model, member: MemberShape): Boolean =
    traitOnMemberOrTargetBoolean(model, member, sqlUuid)

  def sqlJson(member: MemberShape): Boolean =
    traitPresent(member, classOf[SqlJsonTrait])

  def sqlPrimaryKey(member: MemberShape): Boolean =
    traitPresent(member, classOf[SqlPrimaryKeyTrait])

  def sqlIndex(member: MemberShape): Option[SqlIndexTrait] =
    traitOption(member, classOf[SqlIndexTrait])

  def sqlUniqueIndex(member: MemberShape): Option[SqlUniqueIndexTrait] =
    traitOption(member, classOf[SqlUniqueIndexTrait])

  def sqlForeignKey(member: MemberShape): Option[SqlForeignKeyTrait] =
    traitOption(member, classOf[SqlForeignKeyTrait])

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

  def sqlAutoUuid(member: MemberShape): Boolean =
    traitPresent(member, classOf[SqlAutoUuidTrait])

  def sqlCreatedTimestamp(member: MemberShape): Boolean =
    traitPresent(member, classOf[SqlCreatedTimestampTrait])

  def sqlUpdatedTimestamp(member: MemberShape): Boolean =
    traitPresent(member, classOf[SqlUpdatedTimestampTrait])

  def autoGeneration(member: MemberShape): Option[SqlAutoGeneration] =
    if (sqlAutoUuid(member)) {
      Some(SqlAutoUuid)
    } else if (sqlCreatedTimestamp(member)) {
      Some(SqlCreatedTimestamp)
    } else if (sqlUpdatedTimestamp(member)) {
      Some(SqlUpdatedTimestamp)
    } else {
      None
    }

  def columnName(memberName: String, member: MemberShape): String =
    sqlColumn(member)
      .flatMap(traitValue => SqlShared.trimmedNonEmpty(traitValue.getName.toScala))
      .getOrElse(memberName)

  private def traitOption[T <: Trait](shape: Shape, clazz: Class[T]): Option[T] =
    shape.getTrait(clazz).toScala

  private def traitPresent[T <: Trait](shape: Shape, clazz: Class[T]): Boolean =
    shape.getTrait(clazz).isPresent

  private def traitOnMemberOrTarget[T <: Trait](
      model: Model,
      member: MemberShape,
      lookupShape: Shape => Option[T]
  ): Option[T] =
    lookupShape(member).orElse(model.getShape(member.getTarget).toScala.flatMap(lookupShape))

  private def traitOnMemberOrTargetBoolean(
      model: Model,
      member: MemberShape,
      lookupShape: Shape => Boolean
  ): Boolean =
    lookupShape(member) || model.getShape(member.getTarget).toScala.exists(lookupShape)
}
