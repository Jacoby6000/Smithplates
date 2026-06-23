package com.jacoby6000.smithplates.codegen.core

import com.jacoby6000.smithplates.codegen.core.NeutralType.*
import munit.FunSuite

class TypeResolverSpec extends FunSuite {
  private def meta: ModelMeta[Unit] = ModelMeta(None, Nil, ())

  private val uuid    = Model.Alias(ModelId("ns", "Uuid"), meta, StringT)
  private val userId  = Model.Alias(ModelId("ns", "UserId"), meta, ModelRef(uuid.id))
  private val account = Model.Structure(ModelId("ns", "Account"), meta, List(Field("id", ModelRef(userId.id))))

  private val resolver = TypeResolver.fromModelSet(ModelSet[Unit](List(uuid, userId, account)))

  test("underlying chases a single alias to its underlying type") {
    assertEquals(resolver.underlying(ModelRef(uuid.id)), StringT)
  }

  test("underlying chases alias-to-alias chains") {
    assertEquals(resolver.underlying(ModelRef(userId.id)), StringT)
  }

  test("underlying leaves non-alias references unchanged") {
    assertEquals(resolver.underlying(ModelRef(account.id)), ModelRef(account.id))
  }

  test("underlying leaves non-reference types unchanged") {
    assertEquals(resolver.underlying(ListT(StringT)), ListT(StringT))
  }

  test("underlying does not chase aliases nested inside composite types") {
    assertEquals(resolver.underlying(ListT(ModelRef(uuid.id))), ListT(ModelRef(uuid.id)))
    assertEquals(resolver.underlying(OptionalT(ModelRef(userId.id))), OptionalT(ModelRef(userId.id)))
    assertEquals(resolver.underlying(MapT(StringT, ModelRef(uuid.id))), MapT(StringT, ModelRef(uuid.id)))
  }

  test("underlying stops at unresolved ref in alias chain") {
    val gateway = Model.Alias(ModelId("ns", "Gateway"), meta, ModelRef(ModelId("ns", "Missing")))
    val partial = TypeResolver.fromModelSet(ModelSet[Unit](List(gateway)))
    assertEquals(partial.underlying(ModelRef(gateway.id)), ModelRef(ModelId("ns", "Missing")))
  }

  test("underlying terminates on self-referential alias") {
    val self     = Model.Alias(ModelId("ns", "Self"), meta, ModelRef(ModelId("ns", "Self")))
    val resolver = TypeResolver.fromModelSet(ModelSet[Unit](List(self)))
    assertEquals(resolver.underlying(ModelRef(self.id)), ModelRef(self.id))
  }

  test("underlying cyclic aliases stop at the entry ref") {
    val a      = Model.Alias(ModelId("ns", "A"), meta, ModelRef(ModelId("ns", "B")))
    val b      = Model.Alias(ModelId("ns", "B"), meta, ModelRef(ModelId("ns", "A")))
    val cyclic = TypeResolver.fromModelSet(ModelSet[Unit](List(a, b)))
    assertEquals(cyclic.underlying(ModelRef(a.id)), ModelRef(a.id))
    assertEquals(cyclic.underlying(ModelRef(b.id)), ModelRef(b.id))
  }

  test("underlying terminates on three-way cyclic alias definitions") {
    val a      = Model.Alias(ModelId("ns", "A"), meta, ModelRef(ModelId("ns", "B")))
    val b      = Model.Alias(ModelId("ns", "B"), meta, ModelRef(ModelId("ns", "C")))
    val c      = Model.Alias(ModelId("ns", "C"), meta, ModelRef(ModelId("ns", "A")))
    val cyclic = TypeResolver.fromModelSet(ModelSet[Unit](List(a, b, c)))
    assertEquals(cyclic.underlying(ModelRef(a.id)), ModelRef(a.id))
    assertEquals(cyclic.underlying(ModelRef(b.id)), ModelRef(b.id))
    assertEquals(cyclic.underlying(ModelRef(c.id)), ModelRef(c.id))
  }

  test("classify reports the kind of a resolved reference") {
    assertEquals(resolver.classify(ModelRef(uuid.id)), Some(ModelKind.Alias))
    assertEquals(resolver.classify(ModelRef(account.id)), Some(ModelKind.Structure))
    assertEquals(resolver.classify(ModelRef(ModelId("ns", "Missing"))), None)
  }

  test("resolve returns the model for a known ref and None for missing refs") {
    assertEquals(resolver.resolve(ModelRef(uuid.id)), Some(uuid))
    assertEquals(resolver.resolve(ModelRef(account.id)), Some(account))
    assertEquals(resolver.resolve(ModelRef(ModelId("ns", "Missing"))), None)
  }

  test("underlying leaves an unresolved ModelRef unchanged") {
    assertEquals(resolver.underlying(ModelRef(ModelId("ns", "Missing"))), ModelRef(ModelId("ns", "Missing")))
  }
}
