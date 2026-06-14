package com.jacoby6000.smithplates.http.model

sealed trait HttpSerialization

object HttpSerialization {
  case object Json extends HttpSerialization

  val Default: HttpSerialization = Json

  def fromTraitValue(value: String): Either[String, HttpSerialization] =
    value match {
      case "json" => Right(Json)
      case other  => Left(s"Unsupported @httpService serialization '$other'")
    }
}
