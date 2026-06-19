package com.jacoby6000.smithplates.sql

import com.jacoby6000.smithplates.sql.traits.SqlAutoUuidTrait
import com.jacoby6000.smithplates.sql.traits.SqlColumnIndexTrait
import com.jacoby6000.smithplates.sql.traits.SqlColumnTrait
import com.jacoby6000.smithplates.sql.traits.SqlCreatedTimestampTrait
import com.jacoby6000.smithplates.sql.traits.SqlForeignKeyTrait
import com.jacoby6000.smithplates.sql.traits.SqlIndexTrait
import com.jacoby6000.smithplates.sql.traits.SqlJsonTrait
import com.jacoby6000.smithplates.sql.traits.SqlPrimaryKeyTrait
import com.jacoby6000.smithplates.sql.traits.SqlTableTrait
import com.jacoby6000.smithplates.sql.traits.SqlUniqueIndexTrait
import com.jacoby6000.smithplates.sql.traits.SqlUpdatedTimestampTrait
import com.jacoby6000.smithplates.sql.traits.SqlUuidTrait
import com.jacoby6000.smithplates.sql.traits.SqlVarcharTrait
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.traits.Trait

import scala.jdk.OptionConverters.*

private[sql] object SmithySqlTraitAccess {
  extension (structure: StructureShape) {
    def sqlTable: Option[SqlTableTrait] =
      internal.SmithySqlTraitLookup.traitOption(structure, classOf[SqlTableTrait])
  }

  extension (shape: Shape) {
    def sqlVarchar: Option[SqlVarcharTrait] =
      internal.SmithySqlTraitLookup.traitOption(shape, classOf[SqlVarcharTrait])

    def sqlUuid: Boolean =
      internal.SmithySqlTraitLookup.traitPresent(shape, classOf[SqlUuidTrait]) ||
        shape.asMemberShape.toScala.exists(_.sqlAutoUuid)
  }

  extension (member: MemberShape) {
    def sqlColumn: Option[SqlColumnTrait] =
      internal.SmithySqlTraitLookup.traitOption(member, classOf[SqlColumnTrait])

    def sqlColumnIndex: Option[Int] =
      internal.SmithySqlTraitLookup.traitOption(member, classOf[SqlColumnIndexTrait]).map(_.getIndex)

    def sqlVarchar(model: Model): Option[SqlVarcharTrait] =
      internal.SmithySqlTraitLookup.traitOnMemberOrTarget(model, member, _.sqlVarchar)

    def sqlUuid(model: Model): Boolean =
      internal.SmithySqlTraitLookup.traitOnMemberOrTargetBoolean(model, member, _.sqlUuid)

    def sqlJson: Boolean =
      internal.SmithySqlTraitLookup.traitPresent(member, classOf[SqlJsonTrait])

    def sqlPrimaryKey: Boolean =
      internal.SmithySqlTraitLookup.traitPresent(member, classOf[SqlPrimaryKeyTrait])

    def sqlIndex: Option[SqlIndexTrait] =
      internal.SmithySqlTraitLookup.traitOption(member, classOf[SqlIndexTrait])

    def sqlUniqueIndex: Option[SqlUniqueIndexTrait] =
      internal.SmithySqlTraitLookup.traitOption(member, classOf[SqlUniqueIndexTrait])

    def sqlForeignKey: Option[SqlForeignKeyTrait] =
      internal.SmithySqlTraitLookup.traitOption(member, classOf[SqlForeignKeyTrait])

    def sqlAutoUuid: Boolean =
      internal.SmithySqlTraitLookup.traitPresent(member, classOf[SqlAutoUuidTrait])

    def sqlCreatedTimestamp: Boolean =
      internal.SmithySqlTraitLookup.traitPresent(member, classOf[SqlCreatedTimestampTrait])

    def sqlUpdatedTimestamp: Boolean =
      internal.SmithySqlTraitLookup.traitPresent(member, classOf[SqlUpdatedTimestampTrait])

    def autoGeneration: Option[model.SqlAutoGeneration] =
      if (member.sqlAutoUuid) {
        Some(model.SqlAutoUuid)
      } else if (member.sqlCreatedTimestamp) {
        Some(model.SqlCreatedTimestamp)
      } else if (member.sqlUpdatedTimestamp) {
        Some(model.SqlUpdatedTimestamp)
      } else {
        None
      }

    def sqlColumnName(memberName: String): String =
      member.sqlColumn
        .flatMap(traitValue => SqlText.trimmedNonEmpty(traitValue.getName.toScala))
        .getOrElse(memberName)
  }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    object SmithySqlTraitLookup {
      def traitOption[T <: Trait](shape: Shape, clazz: Class[T]): Option[T] =
        shape.getTrait(clazz).toScala

      def traitPresent[T <: Trait](shape: Shape, clazz: Class[T]): Boolean =
        shape.getTrait(clazz).isPresent

      def traitOnMemberOrTarget[T <: Trait](
          model: Model,
          member: MemberShape,
          lookupShape: Shape => Option[T]
      ): Option[T] =
        lookupShape(member).orElse(model.getShape(member.getTarget).toScala.flatMap(lookupShape))

      def traitOnMemberOrTargetBoolean(
          model: Model,
          member: MemberShape,
          lookupShape: Shape => Boolean
      ): Boolean =
        lookupShape(member) || model.getShape(member.getTarget).toScala.exists(lookupShape)
    }
  }
}
