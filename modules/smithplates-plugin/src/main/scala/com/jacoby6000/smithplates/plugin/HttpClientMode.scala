package com.jacoby6000.smithplates.plugin

enum HttpClientMode {
  case Async
  case Sync
  case Both

  def variantKeys(libraryKey: String): List[String] =
    this match {
      case Async => List(libraryKey)
      case Sync  => List(s"$libraryKey.sync")
      case Both  => List(libraryKey, s"$libraryKey.sync")
    }
}

object HttpClientMode {
  val Default: HttpClientMode = HttpClientMode.Async

  def parse(value: String): Either[String, HttpClientMode] =
    value match {
      case "async" => Right(HttpClientMode.Async)
      case "sync"  => Right(HttpClientMode.Sync)
      case "both"  => Right(HttpClientMode.Both)
      case other   => Left(s"unsupported HTTP client mode '$other' (expected 'async', 'sync', or 'both')")
    }
}
