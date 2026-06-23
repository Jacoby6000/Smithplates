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

  test("usedTypes - enum models ignore base type") {
    val primitiveBase =
      Model.EnumModel(
        ModelId("ns", "Color"),
        meta,
        StringT,
        List(EnumValue("Red", PrimitiveLiteral.StringValue("red")))
      )
    val refBase       =
      Model.EnumModel(
        ModelId("ns", "Status"),
        meta,
        refOf("CustomString"),
        List(EnumValue("Active", PrimitiveLiteral.StringValue("active")))
      )
    assertEquals(TypeUsageAnalyzer.default.usedTypes(primitiveBase), Nil)
    assertEquals(TypeUsageAnalyzer.default.usedTypes(refBase), Nil)
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

  test("usedTypes - union members follow declaration order and dedupe") {
    val union = Model.Union(
      ModelId("ns", "Event"),
      meta,
      List(
        Variant("a", refOf("A")),
        Variant("b", refOf("B")),
        Variant("c", refOf("A"))
      )
    )
    assertEquals(TypeUsageAnalyzer.default.usedTypes(union), List(refOf("A"), refOf("B")))
  }

  test("usedTypes - alias collects refs from underlying") {
    val alias = Model.Alias(ModelId("ns", "ItemIds"), meta, ListT(refOf("Item")))
    assertEquals(TypeUsageAnalyzer.default.usedTypes(alias), List(refOf("Item")))
  }

  test("usedTypes - structure reports only refs declared on that model") {
    val account = Model.Structure(
      ModelId("ns", "Account"),
      meta,
      List(Field("owner", refOf("User")))
    )
    assertEquals(TypeUsageAnalyzer.default.usedTypes(account), List(refOf("User")))
  }

  test("usedTypes - empty structure yields no refs") {
    val structure = Model.Structure(ModelId("ns", "Empty"), meta, Nil)
    assertEquals(TypeUsageAnalyzer.default.usedTypes(structure), Nil)
  }

  test("usedTypes - empty union yields no refs") {
    val union = Model.Union(ModelId("ns", "Empty"), meta, Nil)
    assertEquals(TypeUsageAnalyzer.default.usedTypes(union), Nil)
  }

  test("usedTypes - operation with no bound shapes yields no refs") {
    val op = OperationModel(
      ModelId("ns", "Ping"),
      OperationMeta(None, Nil, ()),
      input = None,
      output = None,
      errors = Nil
    )
    assertEquals(TypeUsageAnalyzer.default.usedTypes(op), Nil)
  }

  test("usedTypes - alias with primitive underlying yields no refs") {
    val alias = Model.Alias(ModelId("ns", "Name"), meta, StringT)
    assertEquals(TypeUsageAnalyzer.default.usedTypes(alias), Nil)
  }

  test("usedTypes - alias reports ref to another alias without chasing") {
    val outer = Model.Alias(ModelId("ns", "Outer"), meta, refOf("Inner"))
    assertEquals(TypeUsageAnalyzer.default.usedTypes(outer), List(refOf("Inner")))
  }

  test("usedTypes - map ref only in key") {
    val structure =
      Model.Structure(ModelId("ns", "Keyed"), meta, List(Field("index", MapT(refOf("Key"), StringT))))
    assertEquals(TypeUsageAnalyzer.default.usedTypes(structure), List(refOf("Key")))
  }

  test("usedTypes - map ref only in value") {
    val structure =
      Model.Structure(ModelId("ns", "Valued"), meta, List(Field("index", MapT(StringT, refOf("Value")))))
    assertEquals(TypeUsageAnalyzer.default.usedTypes(structure), List(refOf("Value")))
  }

  test("directRefs - sparse list ListT(OptionalT(ref))") {
    assertEquals(TypeUsageAnalyzer.directRefs(ListT(OptionalT(refOf("Item")))), List(refOf("Item")))
  }

  test("directRefs - optional sparse list OptionalT(ListT(OptionalT(ref)))") {
    assertEquals(
      TypeUsageAnalyzer.directRefs(OptionalT(ListT(OptionalT(refOf("Item"))))),
      List(refOf("Item"))
    )
  }

  test("directRefs - non-container primitives yield no refs") {
    assertEquals(TypeUsageAnalyzer.directRefs(TimestampT(TimestampFormat.DateTime)), Nil)
    assertEquals(TypeUsageAnalyzer.directRefs(BytesT), Nil)
    assertEquals(TypeUsageAnalyzer.directRefs(DocumentT), Nil)
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

  property("structure usedTypes matches an independent first-occurrence walk") {
    forAll(genStructure) { structure =>
      assertEquals(
        TypeUsageAnalyzer.default.usedTypes(structure),
        expectedFirstOccurrence(structure.fields.map(_.tpe))
      )
    }
  }

  property("structure usedTypes is deduped and complete") {
    forAll(genStructure) { structure =>
      val result = TypeUsageAnalyzer.default.usedTypes(structure)
      Prop(result == result.distinct) :| "deduped" &&
      Prop(result.toSet == structure.fields.flatMap(f => TypeUsageAnalyzer.directRefs(f.tpe)).toSet) :| "complete"
    }
  }
}
