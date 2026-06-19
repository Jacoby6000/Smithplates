package com.jacoby6000.smithplates.http

import cats.syntax.all.*
import com.jacoby6000.smithplates.http.model.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.TimestampFormatTrait

import scala.jdk.OptionConverters.*

object SmithyHttpTimestampFormatResolver {
  val TimestampShapeId: ShapeId = ShapeId.from("smithy.api#Timestamp")

  def resolve(
      model: Model,
      serviceShape: ShapeId,
      operationName: String,
      memberName: String,
      member: MemberShape
  ): HttpValidated[HttpTimestampFormat] =
    parseFormat(
      serviceShape = serviceShape,
      operationName = operationName,
      memberName = memberName,
      value = timestampFormatValue(model, member)
    )

  private def timestampFormatValue(
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

  private def parseFormat(
      serviceShape: ShapeId,
      operationName: String,
      memberName: String,
      value: Option[TimestampFormatTrait.Format]
  ): HttpValidated[HttpTimestampFormat] =
    value match {
      case None                                            =>
        HttpTimestampFormat.Default.validNel
      case Some(TimestampFormatTrait.Format.DATE_TIME)     =>
        HttpTimestampFormat.DateTime.validNel
      case Some(TimestampFormatTrait.Format.EPOCH_SECONDS) =>
        HttpTimestampFormat.EpochSeconds.validNel
      case Some(TimestampFormatTrait.Format.HTTP_DATE)     =>
        HttpTimestampFormat.HttpDate.validNel
      case Some(TimestampFormatTrait.Format.UNKNOWN)       =>
        InvalidHttpOperation(
          serviceShape,
          operationName,
          s"input member '$memberName' has unsupported @timestampFormat value"
        ).invalidNel
    }
}
