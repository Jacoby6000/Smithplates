package com.jacoby6000.smithplates.codegen.core.planning

import com.jacoby6000.smithplates.codegen.core.*
import com.jacoby6000.smithplates.codegen.core.planning.BindingFilter.internal.*
import munit.FunSuite

class BindingFilterSpec extends FunSuite {
  private def meta(tags: List[String] = Nil): ModelMeta[Unit] =
    ModelMeta(None, tags, ())

  private val taggedModel   =
    Model.Structure(ModelId("example", "Tagged"), meta(tags = List("alpha")), Nil)
  private val untaggedModel =
    Model.Structure(ModelId("example", "Plain"), meta(), Nil)

  private val taggedOperation   =
    OperationModel(ModelId("example", "TaggedOp"), OperationMeta(None, List("alpha"), ()), None, None, Nil)
  private val untaggedOperation =
    OperationModel(ModelId("example", "PlainOp"), OperationMeta(None, Nil, ()), None, None, Nil)

  test("effectiveFilters treats empty filter lists as All") {
    assertEquals(effectiveFilters(Nil), List(BindingFilterAtom.All))
  }

  test("matchesModel ANDs filter atoms") {
    assert(
      BindingFilter.matchesModel(
        List(BindingFilterAtom.Untagged, BindingFilterAtom.Kind(ModelKind.Structure)),
        untaggedModel
      )
    )
    assert(
      !BindingFilter.matchesModel(
        List(BindingFilterAtom.Untagged, BindingFilterAtom.Kind(ModelKind.Structure)),
        taggedModel
      )
    )
  }

  test("matchesOperation applies tagged and untagged filters") {
    assert(BindingFilter.matchesOperation(List(BindingFilterAtom.Tagged), taggedOperation))
    assert(!BindingFilter.matchesOperation(List(BindingFilterAtom.Tagged), untaggedOperation))
    assert(BindingFilter.matchesOperation(List(BindingFilterAtom.Untagged), untaggedOperation))
    assert(!BindingFilter.matchesOperation(List(BindingFilterAtom.Untagged), taggedOperation))
  }

  test("validateOperationFilters rejects kind filters") {
    assertEquals(
      BindingFilter.validateOperationFilters(List(BindingFilterAtom.Kind(ModelKind.Structure))),
      Some(InvalidOperationBindingFilter(BindingFilterAtom.Kind(ModelKind.Structure)))
    )
    assertEquals(BindingFilter.validateOperationFilters(List(BindingFilterAtom.Tagged)), None)
  }

  test("matchesOperation rejects kind filters before matching") {
    assert(!BindingFilter.matchesOperation(List(BindingFilterAtom.Kind(ModelKind.Structure)), taggedOperation))
  }
}
