package com.jacoby6000.smithplates.sql

import cats.data.Validated
import munit.FunSuite
import software.amazon.smithy.model.shapes.ShapeId

final class SqlEnumExtractorSpec extends FunSuite {
  test("extractReferenced - collects string and int enums from structure members") {
    val model = SqlEnumExtractorSpec.internal.builder.assemble(
      """enum TaskStatus {
        |    OPEN = "open"
        |    CLOSED = "closed"
        |}
        |
        |intEnum TaskPriority {
        |    LOW = 1
        |    HIGH = 2
        |}
        |
        |structure Task {
        |    status: TaskStatus
        |    priority: TaskPriority
        |}""".stripMargin
    )

    val shapeIr                 =
      SqlShapeIrExtractor.extract(model, List(ShapeId.from("example#Task"))) match {
        case Validated.Valid(ir)       => ir
        case Validated.Invalid(errors) =>
          throw new AssertionError(errors.toList.mkString("; "))
      }
    val (stringEnums, intEnums) =
      SqlEnumExtractor.extractReferenced(
        model = model,
        namespace = "example",
        structures = shapeIr.structures,
        unions = shapeIr.unions
      )

    assertEquals(stringEnums.map(_.name), List("TaskStatus"))
    assertEquals(stringEnums.head.members.map(_.name), List("CLOSED", "OPEN"))
    assertEquals(intEnums.map(_.name), List("TaskPriority"))
    assertEquals(intEnums.head.members.map(_.value), List(2, 1))
  }
}
object SqlEnumExtractorSpec {

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    val builder = SqlTestModelBuilder
  }
}
