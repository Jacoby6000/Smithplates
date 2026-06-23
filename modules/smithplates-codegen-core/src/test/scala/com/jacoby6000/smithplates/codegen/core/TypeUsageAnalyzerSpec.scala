package com.jacoby6000.smithplates.codegen.core

import com.jacoby6000.smithplates.codegen.core.NeutralType.*
import munit.ScalaCheckSuite
import org.scalacheck.Gen
import org.scalacheck.Prop
import org.scalacheck.Prop.forAll

class TypeUsageAnalyzerSpec extends ScalaCheckSuite {
  private def meta: ModelMeta[Unit] = ModelMeta(None, Nil, ())

  private def refOf(name: String): ModelRef = ModelRef(ModelId("ns", name))

  test("usedTypes - declaration order, deduped to first occurrence") {
    val structure = Model.Structure(
      ModelId("ns", "Account"),
      meta,
      List(
        Field("owner", refOf("User")),
        Field("tags", ListT(refOf("Tag"))),
        Field("manager", OptionalT(refOf("User"))),
        Field("name", StringT)
      )
    )
    assertEquals(TypeUsageAnalyzer.default.usedTypes(structure), List(refOf("User"), refOf("Tag")))
  }

  test("usedTypes - recurses into map keys before values") {
    val structure = Model.Structure(
      ModelId("ns", "Lookup"),
      meta,
      List(Field("index", MapT(refOf("Key"), refOf("Value"))))
    )
    assertEquals(TypeUsageAnalyzer.default.usedTypes(structure), List(refOf("Key"), refOf("Value")))
  }

  test("usedTypes - enum models reference no types") {
    val enumModel =
      Model.EnumModel(
        ModelId("ns", "Color"),
        meta,
        StringT,
        List(EnumValue("Red", PrimitiveLiteral.StringValue("red"))))
    assertEquals(TypeUsageAnalyzer.default.usedTypes(enumModel), Nil)
  }

  test("usedTypes - operation walks input, then output, then errors") {
    val op = OperationModel(
      ModelId("ns", "Create"),
      OperationMeta(None, Nil, ()),
      input = Some(refOf("CreateInput")),
      output = Some(refOf("CreateOutput")),
      errors = List(refOf("Conflict"), refOf("CreateInput"))
    )
    assertEquals(
      TypeUsageAnalyzer.default.usedTypes(op),
      List(refOf("CreateInput"), refOf("CreateOutput"), refOf("Conflict"))
    )
  }

  // Independent first-occurrence reference implementation, distinct from the production `.distinct`-based one.
  private def expectedFirstOccurrence(types: List[NeutralType]): List[ModelRef] = {
    val acc                          = scala.collection.mutable.LinkedHashSet.empty[ModelRef]
    def walk(tpe: NeutralType): Unit =
      tpe match {
        case r: ModelRef      => acc += r; ()
        case OptionalT(inner) => walk(inner)
        case ListT(element)   => walk(element)
        case MapT(key, value) => walk(key); walk(value)
        case _                => ()
      }
    types.foreach(walk)
    acc.toList
  }

  private val genModelRef: Gen[ModelRef] =
    Gen.oneOf("A", "B", "C", "D").map(refOf)

  private def genType(depth: Int): Gen[NeutralType] = {
    val leaf: Gen[NeutralType] = Gen.oneOf[NeutralType](StringT, IntegerT, BooleanT, DoubleT)
    if (depth <= 0) Gen.oneOf(leaf, genModelRef)
    else
      Gen.oneOf(
        leaf,
        genModelRef,
        genType(depth - 1).map(OptionalT(_)),
        genType(depth - 1).map(ListT(_)),
        for {
          key   <- genType(depth - 1)
          value <- genType(depth - 1)
        } yield MapT(key, value)
      )
  }

  private val genStructure: Gen[Model.Structure[Unit]] =
    for {
      types <- Gen.listOf(genType(3))
    } yield Model.Structure(
      ModelId("ns", "Generated"),
      meta,
      types.zipWithIndex.map { case (tpe, i) => Field(s"field$i", tpe) }
    )

  property("usedTypes matches an independent first-occurrence walk") {
    forAll(genStructure) { structure =>
      assertEquals(
        TypeUsageAnalyzer.default.usedTypes(structure),
        expectedFirstOccurrence(structure.fields.map(_.tpe))
      )
    }
  }

  property("usedTypes is deduped and complete") {
    forAll(genStructure) { structure =>
      val result = TypeUsageAnalyzer.default.usedTypes(structure)
      Prop(result == result.distinct) :| "deduped" &&
      Prop(result.toSet == structure.fields.flatMap(f => TypeUsageAnalyzer.directRefs(f.tpe)).toSet) :| "complete"
    }
  }
}
