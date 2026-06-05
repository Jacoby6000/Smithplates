package com.jacoby6000.smithy.sql

import StringChunkAssertions.assertChunk

class StringChunkAssertionsSpec extends munit.FunSuite {
  test("assertChunk - matches chunk and skips trailing whitespace") {
    val remaining =
      "async def create_widget(\n        self,\nfoo: str,\n".assertChunk("async def create_widget(")

    assertEquals(remaining, "self,\nfoo: str,\n")
  }

  test("assertChunk - chains ordered chunks") {
    val remaining =
      "a b c"
        .assertChunk("a")
        .assertChunk("b")
        .assertChunk("c")

    assertEquals(remaining, "")
  }

  test("assertChunk - fails when chunk is missing") {
    intercept[munit.FailException] {
      "async def other(".assertChunk("async def create_widget(")
    }
  }
}
