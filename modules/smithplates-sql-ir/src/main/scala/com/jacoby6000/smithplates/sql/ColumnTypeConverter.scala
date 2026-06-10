package com.jacoby6000.smithplates.sql

import com.jacoby6000.smithplates.sql.model.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.ShapeType

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

trait ColumnTypeConverter {
  def fromSmithyMember(model: Model, member: MemberShape): Either[UnsupportedColumnType, SqlColumnType]
}

final case class UnsupportedColumnType(
    target: ShapeId,
    kind: InvalidMemberColumnType.Kind,
    detail: Option[String] = None
) {
  def toSchemaError(columnName: String, table: String): SqlSchemaError =
    InvalidMemberColumnType(columnName, target, table, kind, detail)
}

object SmithyColumnTypeConverter extends ColumnTypeConverter {
  private val shapeTypeToColumnType: Map[ShapeType, SqlColumnType] = Map(
    ShapeType.INTEGER  -> SqlColumnType.Integer,
    ShapeType.LONG     -> SqlColumnType.BigInt,
    ShapeType.BOOLEAN  -> SqlColumnType.Boolean,
    ShapeType.DOCUMENT -> SqlColumnType.Json,
    ShapeType.BLOB     -> SqlColumnType.Blob
  )

  override def fromSmithyMember(
      model: Model,
      member: MemberShape
  ): Either[UnsupportedColumnType, SqlColumnType] = {
    val target = member.getTarget

    if (member.sqlJson) {
      jsonColumnTypeForTarget(model, target).toRight(
        UnsupportedColumnType(target, InvalidMemberColumnType.Kind.SqlJson)
      )
    } else {
      member.sqlVarchar(model) match {
        case Some(varcharTrait)                       =>
          stringLikeColumnType(
            model,
            target,
            SqlColumnType.Varchar(varcharTrait.getMaxLength),
            InvalidMemberColumnType.Kind.SqlVarchar
          )
        case None if member.sqlUuid(model)            =>
          stringLikeColumnType(
            model,
            target,
            SqlColumnType.Uuid,
            InvalidMemberColumnType.Kind.SqlUuid
          )
        case None if isTimestampTarget(model, target) =>
          SmithyTimestampFormatResolver
            .resolve(model, member)
            .map(SqlColumnType.Timestamp(_))
        case None                                     =>
          columnTypeForTarget(model, target).toRight(
            UnsupportedColumnType(target, InvalidMemberColumnType.Kind.Unsupported)
          )
      }
    }
  }

  private def stringLikeColumnType(
      model: Model,
      target: ShapeId,
      columnType: SqlColumnType,
      kind: InvalidMemberColumnType.Kind
  ): Either[UnsupportedColumnType, SqlColumnType] =
    if (isStringLike(model, target)) Right(columnType)
    else Left(UnsupportedColumnType(target, kind))

  private def jsonColumnTypeForTarget(model: Model, target: ShapeId): Option[SqlColumnType] =
    shape(model, target).flatMap { resolved =>
      resolved.getType match {
        case ShapeType.LIST | ShapeType.MAP | ShapeType.STRUCTURE | ShapeType.UNION | ShapeType.DOCUMENT =>
          Some(SqlColumnType.Json)
        case _                                                                                           => None
      }
    }

  private def columnTypeForTarget(model: Model, target: ShapeId): Option[SqlColumnType] =
    shape(model, target).flatMap(columnTypeForShape)

  private def columnTypeForShape(shape: Shape): Option[SqlColumnType] =
    shape.getType match {
      case ShapeType.ENUM                                        => Some(stringEnumColumnType(shape.asEnumShape.get()))
      case ShapeType.INT_ENUM                                    => Some(intEnumColumnType(shape.asIntEnumShape.get()))
      case ShapeType.STRING | _ if shape.asStringShape.isPresent => Some(SqlColumnType.Text)
      case shapeType                                             => shapeTypeToColumnType.get(shapeType)
    }

  private def stringEnumColumnType(enumShape: software.amazon.smithy.model.shapes.EnumShape): SqlColumnType =
    SqlColumnType.StringEnum(
      enumShape.getId,
      SqlText.enumTypeName(enumShape.getId),
      enumShape.getEnumValues.asScala.toList.sortBy(_._1).map(_._2)
    )

  private def intEnumColumnType(intEnumShape: software.amazon.smithy.model.shapes.IntEnumShape): SqlColumnType =
    SqlColumnType.IntEnum(
      SqlText.enumTypeName(intEnumShape.getId),
      intEnumShape.getEnumValues.asScala.toList.sortBy(_._1).map(_._2.intValue())
    )

  private def isStringLike(model: Model, target: ShapeId): Boolean =
    columnTypeForTarget(model, target).contains(SqlColumnType.Text)

  private def isTimestampTarget(model: Model, target: ShapeId): Boolean =
    shape(model, target).exists(_.getType == ShapeType.TIMESTAMP)

  private def shape(model: Model, target: ShapeId): Option[Shape] =
    model.getShape(target).toScala
}
