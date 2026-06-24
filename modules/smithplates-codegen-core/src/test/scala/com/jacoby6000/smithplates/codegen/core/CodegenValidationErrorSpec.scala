package com.jacoby6000.smithplates.codegen.core

import cats.data.NonEmptyList
import com.jacoby6000.smithplates.codegen.core.NeutralType.ModelRef
import munit.FunSuite

class CodegenValidationErrorSpec extends FunSuite {
  test("DuplicateId message pluralizes occurrence counts") {
    assertEquals(
      DuplicateId(ModelId("ns", "Thing"), models = 2, services = 1, operations = 3).message,
      "Duplicate id ns#Thing (2 models, 1 service, 3 operations)"
    )
    assertEquals(
      DuplicateId(ModelId("ns", "Thing"), models = 1, services = 0, operations = 0).message,
      "Duplicate id ns#Thing (1 model)"
    )
  }

  test("CyclicAliasDefinition message formats the alias path") {
    val error =
      CyclicAliasDefinition(
        NonEmptyList.of(ModelId("ns", "A"), ModelId("ns", "B"), ModelId("ns", "A"))
      )
    assertEquals(error.message, "Cyclic alias definition: ns#A -> ns#B -> ns#A")
  }

  test("UnresolvedModelRef message includes ref and role") {
    val error = UnresolvedModelRef(ModelRef(ModelId("ns", "Missing")), "output of operation ns#Create")
    assertEquals(error.message, "Unresolved model reference ns#Missing in output of operation ns#Create")
  }

  test("InvalidModelMeta message includes model id and reason") {
    assertEquals(
      InvalidModelMeta(ModelId("ns", "Account"), "feature required").message,
      "Invalid metadata for model ns#Account: feature required"
    )
  }

  test("InvalidOperationMeta message includes operation id and reason") {
    assertEquals(
      InvalidOperationMeta(ModelId("ns", "Create"), "binding required").message,
      "Invalid metadata for operation ns#Create: binding required"
    )
  }

  test("InvalidServiceMeta message includes service id and reason") {
    assertEquals(
      InvalidServiceMeta(ModelId("ns", "Example"), "version required").message,
      "Invalid metadata for service ns#Example: version required"
    )
  }
}
