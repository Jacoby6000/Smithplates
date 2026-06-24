package com.jacoby6000.smithplates.codegen.core

import com.jacoby6000.smithplates.codegen.core.NeutralType.*
import munit.FunSuite

class ModelSetSpec extends FunSuite {
  private def meta: ModelMeta[Unit] = ModelMeta(None, Nil, ())

  private val structure = Model.Structure(ModelId("ns", "Account"), meta, List(Field("id", StringT)))
  private val union     = Model.Union(ModelId("ns", "Event"), meta, List(Variant("a", StringT)))
  private val enumModel =
    Model.EnumModel(ModelId("ns", "Color"), meta, StringT, List(EnumValue("Red", PrimitiveLiteral.StringValue("red"))))
  private val alias     = Model.Alias(ModelId("ns", "Uuid"), meta, StringT)

  private val modelSet = ModelSet[Unit](List(structure, union, enumModel, alias))

  test("kind conveniences partition by model kind") {
    assertEquals(modelSet.structures, List(structure))
    assertEquals(modelSet.unions, List(union))
    assertEquals(modelSet.enums, List(enumModel))
    assertEquals(modelSet.aliases, List(alias))
  }

  test("resolve by id and by ref") {
    assertEquals(modelSet.resolve(ModelId("ns", "Account")), Some(structure))
    assertEquals(modelSet.resolve(ModelRef(ModelId("ns", "Event"))), Some(union))
    assertEquals(modelSet.resolve(ModelId("ns", "Missing")), None)
  }

  test("kind reports the declared model kind") {
    assertEquals(structure.kind, ModelKind.Structure)
    assertEquals(union.kind, ModelKind.Union)
    assertEquals(enumModel.kind, ModelKind.Enum)
    assertEquals(alias.kind, ModelKind.Alias)
  }

  test("empty model set resolves nothing and yields empty kind partitions") {
    val empty = ModelSet[Unit](Nil)
    assertEquals(empty.structures, Nil)
    assertEquals(empty.unions, Nil)
    assertEquals(empty.enums, Nil)
    assertEquals(empty.aliases, Nil)
    assertEquals(empty.resolve(ModelId("ns", "Missing")), None)
  }

  test("as accessors are kind-specific") {
    assertEquals(structure.asStructure, Some(structure))
    assertEquals(structure.asUnion, None)
    assertEquals(structure.asEnum, None)
    assertEquals(structure.asAlias, None)

    assertEquals(union.asUnion, Some(union))
    assertEquals(union.asStructure, None)

    assertEquals(enumModel.asEnum, Some(enumModel))
    assertEquals(enumModel.asStructure, None)

    assertEquals(alias.asAlias, Some(alias))
    assertEquals(alias.asStructure, None)
  }
}
