package com.jacoby6000.smithplates.codegen.core.json

import cats.data.NonEmptyList
import io.circe.CursorOp
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.HCursor
import io.circe.JsonObject

import scala.compiletime.constValue
import scala.compiletime.erasedValue
import scala.deriving.Mirror

/** Strict Circe decoding helpers that reject unknown JSON object keys. */
object StrictJsonDecoding {
  def rejectUnknownKeys[A](decoder: Decoder[A], allowedKeys: Set[String]): Decoder[A] =
    Decoder.instance { cursor =>
      validateUnknownKeys(cursor, allowedKeys).flatMap(_ => decoder(cursor))
    }

  extension [A](decoder: Decoder[A]) {
    def makeStrict(allowedKeys: Set[String]): Decoder[A] =
      rejectUnknownKeys(decoder, allowedKeys)

    inline def makeStrict(using mirror: Mirror.ProductOf[A]): Decoder[A] =
      makeStrict(productFieldNames[A])
  }

  inline def productFieldNames[A](using mirror: Mirror.ProductOf[A]): Set[String] =
    elemLabels[mirror.MirroredElemLabels]

  private inline def elemLabels[T <: Tuple]: Set[String] = inline erasedValue[T] match {
    case _: EmptyTuple      => Set.empty
    case _: (label *: tail) => Set(constValue[label].asInstanceOf[String]) ++ elemLabels[tail]
  }

  def rejectExtraKeys(cursor: HCursor, allowedKeys: Set[String]): Decoder.Result[Unit] =
    cursor.as[JsonObject].flatMap { jsonObject =>
      val extraKeys = jsonObject.keys.filterNot(allowedKeys.contains).toList.sorted
      if (extraKeys.isEmpty) {
        Right(())
      } else {
        Left(
          DecodingFailure(
            s"unexpected keys: ${extraKeys.mkString(", ")} (allowed: ${allowedKeys.toList.sorted.mkString(", ")})",
            cursor.history
          )
        )
      }
    }

  private def validateUnknownKeys(cursor: HCursor, allowedKeys: Set[String]): Decoder.Result[Unit] =
    cursor.as[JsonObject].flatMap { jsonObject =>
      val allowedNormalized = allowedKeys.map(_.toLowerCase)
      val extraKeys         =
        jsonObject.keys.filterNot(key => allowedNormalized.contains(key.toLowerCase)).toSet
      if (extraKeys.isEmpty) {
        Right(())
      } else {
        val unexpected = UnexpectedKeys(extraKeys, allowedKeys, cursor.history)
        val failure    = DecodingFailure(unexpected.getMessage, cursor.history)
        failure.initCause(unexpected)
        Left(failure)
      }
    }
}

/** Decoding failure for JSON objects that contain keys outside the allowed schema. */
final class UnexpectedKeys private (
    val extraKeys: Set[String],
    val allowedKeys: Set[String],
    val history: List[CursorOp]
) extends RuntimeException(UnexpectedKeys.message(extraKeys, allowedKeys))

object UnexpectedKeys {
  def apply(extraKeys: Set[String], allowedKeys: Set[String], history: List[CursorOp]): UnexpectedKeys =
    new UnexpectedKeys(extraKeys, allowedKeys, history)

  def message(extraKeys: Set[String], allowedKeys: Set[String]): String =
    s"Unexpected keys: ${extraKeys.toList.sorted.mkString(", ")} " +
      s"(allowed: ${allowedKeys.toList.sorted.mkString(", ")})"

  def partition(
      failure: DecodingFailure
  ): (Option[NonEmptyList[UnexpectedKeys]], Option[NonEmptyList[DecodingFailure]]) =
    partition(NonEmptyList.one(failure))

  def partition(
      failures: NonEmptyList[DecodingFailure]
  ): (Option[NonEmptyList[UnexpectedKeys]], Option[NonEmptyList[DecodingFailure]]) = {
    val failureChain = failures.toList.flatMap(decodingFailureChain)
    val unexpected   = failureChain.flatMap(unexpectedFromFailure)
    val remaining    = failureChain.filterNot(isUnexpectedKeysFailure)
    (
      NonEmptyList.fromList(unexpected),
      NonEmptyList.fromList(remaining)
    )
  }

  private def decodingFailureChain(failure: DecodingFailure): List[DecodingFailure] = {
    def loop(current: DecodingFailure, seen: Set[DecodingFailure]): List[DecodingFailure] =
      if (seen(current)) {
        Nil
      } else {
        current :: (Option(current.getCause)
          .collect { case nested: DecodingFailure =>
            loop(nested, seen + current)
          }
          .getOrElse(Nil))
      }
    loop(failure, Set.empty)
  }

  private def unexpectedFromFailure(failure: DecodingFailure): Option[UnexpectedKeys] = {
    def loop(throwable: Throwable, seen: Set[Throwable]): Option[UnexpectedKeys] =
      if (throwable == null || seen(throwable)) {
        None
      } else {
        throwable match {
          case unexpected: UnexpectedKeys => Some(unexpected)
          case _                          => loop(throwable.getCause, seen + throwable)
        }
      }
    loop(failure, Set.empty)
  }

  private def isUnexpectedKeysFailure(failure: DecodingFailure): Boolean =
    unexpectedFromFailure(failure).isDefined
}
