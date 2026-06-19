package com.jacoby6000.smithplates.sql

import com.jacoby6000.smithplates.sql.model.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.TimestampFormatTrait

import scala.jdk.OptionConverters.*

object SmithyTimestampFormatResolver {
  def resolve(model: Model, member: MemberShape): Either[UnsupportedColumnType, SqlTimestampFormat] =
    internal.parseFormat(internal.timestampFormatValue(model, member), member.getTarget)

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def timestampFormatValue(
        model: Model,
        member: MemberShape
    ): Option[TimestampFormatTrait.Format] = {
      val memberFormat = member.getTrait(classOf[TimestampFormatTrait]).toScala.map(_.getFormat)
      val targetFormat =
        model.getShape(member.getTarget).toScala.flatMap { target =>
          target.getTrait(classOf[TimestampFormatTrait]).toScala.map(_.getFormat)
        }
      memberFormat.orElse(targetFormat)
    }

    def parseFormat(
        value: Option[TimestampFormatTrait.Format],
        target: ShapeId
    ): Either[UnsupportedColumnType, SqlTimestampFormat] =
      value match {
        case None                                            => Right(SqlTimestampFormat.Default)
        case Some(TimestampFormatTrait.Format.DATE_TIME)     => Right(SqlTimestampFormat.DateTime)
        case Some(TimestampFormatTrait.Format.EPOCH_SECONDS) => Right(SqlTimestampFormat.EpochSeconds)
        case Some(TimestampFormatTrait.Format.HTTP_DATE)     =>
          Left(
            UnsupportedColumnType(
              target,
              InvalidMemberColumnType.Kind.TimestampFormat,
              Some("@timestampFormat \"http-date\" is not supported for SQL columns")
            )
          )
        case Some(TimestampFormatTrait.Format.UNKNOWN)       =>
          Left(
            UnsupportedColumnType(
              target,
              InvalidMemberColumnType.Kind.TimestampFormat,
              Some("Unsupported @timestampFormat value")
            )
          )
      }
  }
}
