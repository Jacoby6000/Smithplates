package com.jacoby6000.smithplates.codegen.core.json

import com.jacoby6000.smithplates.codegen.core.json.StrictJsonDecoding.makeStrict
import io.circe.CursorOp
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.generic.semiauto.deriveDecoder
import io.circe.parser.parse
import munit.FunSuite

class StrictJsonDecodingSpec extends FunSuite {
  test("partition extracts UnexpectedKeys from nested strict decode failures") {
    final case class Inner(name: Option[String] = None)
    final case class Outer(inner: Option[Inner] = None)

    given Decoder[Inner] = deriveDecoder[Inner].makeStrict
    given Decoder[Outer] = deriveDecoder[Outer].makeStrict

    val json = parse("""{"inner": {"bad": 1}}""").getOrElse(fail("expected valid json"))

    json.as[Outer] match {
      case Left(failure: DecodingFailure) =>
        UnexpectedKeys.partition(failure) match {
          case (Some(unexpected), remaining) =>
            assertEquals(unexpected.head.extraKeys, Set("bad"))
            assert(unexpected.head.history.contains(CursorOp.DownField("inner")))
            assert(remaining.forall(failure => UnexpectedKeys.partition(failure)._1.isEmpty))
          case (None, _)                     =>
            fail(s"expected UnexpectedKeys, got: $failure")
        }
      case Right(_)                       =>
        fail("expected strict nested decode to fail")
    }
  }
}
