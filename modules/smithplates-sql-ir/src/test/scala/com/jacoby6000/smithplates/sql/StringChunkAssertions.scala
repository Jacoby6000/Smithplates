package com.jacoby6000.smithplates.sql

import munit.Assertions

object StringChunkAssertions {
  extension (value: String) {
    def assertChunk(expectedChunk: String, trailingTokens: List[String] = List(" ", "\n", "\r")): String = {
      val index = value.indexOf(expectedChunk)
      if (index < 0) {
        Assertions.fail(
          s"Expected chunk not found before end of string.\nExpected: $expectedChunk\nRemaining: ${preview(value)}"
        )
      }

      dropTrailingTokens(value.substring(index + expectedChunk.length), trailingTokens)
    }
  }

  private def dropTrailingTokens(value: String, trailingTokens: List[String]): String = {
    @annotation.tailrec
    def loop(current: String): String =
      trailingTokens.find(token => current.startsWith(token)) match {
        case Some(token) => loop(current.substring(token.length))
        case None        => current
      }

    loop(value)
  }

  private def preview(value: String, maxLength: Int = 200): String =
    if (value.length <= maxLength) {
      value
    } else {
      s"${value.take(maxLength)}..."
    }
}
