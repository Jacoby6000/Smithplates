package com.jacoby6000.smithplates.codegen.core

import cats.data.NonEmptyList
import cats.data.Validated
import com.jacoby6000.smithplates.codegen.core.NeutralType.*
import munit.FunSuite

class SystemValidatorSpec extends FunSuite {
  private def meta: ModelMeta[Unit] = ModelMeta(None, Nil, ())

  private given ModelMetaValidator[Unit]          = ModelMetaValidator.noop
  private given OperationMetaValidator[Unit]      = OperationMetaValidator.noop
  private given ServiceMetaValidator[Unit]        = ServiceMetaValidator.noop
  private given ModelValidator[Unit]              = ModelValidator.default
  private given OperationValidator[Unit]          = OperationValidator.default
  private given ModelSetValidator[Unit]           = ModelSetValidator.default
  private given ServiceModelValidator[Unit, Unit] = ServiceModelValidator.default

  private val validator = SystemValidator.default[Unit, Unit, Unit]

  private def refOf(name: String): ModelRef = ModelRef(ModelId("ns", name))

  private val inputShape  = Model.Structure(ModelId("ns", "CreateInput"), meta, List(Field("id", StringT)))
  private val outputShape =
    Model.Structure(ModelId("ns", "CreateOutput"), meta, List(Field("id", StringT)))
  private val errorShape  = Model.Structure(ModelId("ns", "Conflict"), meta, Nil)

  private val validModelSet =
    ModelSet[Unit](List(inputShape, outputShape, errorShape))

  private val validService =
    ServiceModel(
      ModelId("ns", "Example"),
      ServiceMeta(None, Nil, ()),
      List(
        OperationModel(
          ModelId("ns", "Create"),
          OperationMeta(None, Nil, ()),
          input = Some(refOf("CreateInput")),
          output = Some(refOf("CreateOutput")),
          errors = List(refOf("Conflict"))
        )
      )
    )

  test("validate accepts a consistent model set and service") {
    assertEquals(validator.validate(validModelSet, validService), CodegenValidated.unit)
  }

  test("validate rejects duplicate model ids") {
    val duplicateAlias =
      Model.Alias(ModelId("ns", "CreateInput"), meta, IntegerT)
    val invalid        = ModelSet[Unit](List(inputShape, duplicateAlias, outputShape, errorShape))

    validator.validate(invalid, validService) match {
      case Validated.Invalid(errors) =>
        assertEquals(
          errors.toList,
          List(DuplicateId(ModelId("ns", "CreateInput"), models = 2, services = 0, operations = 0)))
      case other                     =>
        fail(s"Expected invalid validation, got $other")
    }
  }

  test("validate rejects cyclic alias definitions") {
    val a       = Model.Alias(ModelId("ns", "A"), meta, refOf("B"))
    val b       = Model.Alias(ModelId("ns", "B"), meta, refOf("A"))
    val invalid = ModelSet[Unit](List(a, b, inputShape, outputShape, errorShape))

    validator.validate(invalid, validService) match {
      case Validated.Invalid(errors) =>
        assertEquals(
          errors.toList,
          List(CyclicAliasDefinition(NonEmptyList.of(ModelId("ns", "A"), ModelId("ns", "B"), ModelId("ns", "A"))))
        )
      case other                     =>
        fail(s"Expected invalid validation, got $other")
    }
  }

  test("validate rejects self-referential aliases") {
    val self    = Model.Alias(ModelId("ns", "Self"), meta, refOf("Self"))
    val invalid = ModelSet[Unit](List(self, inputShape, outputShape, errorShape))

    validator.validate(invalid, validService) match {
      case Validated.Invalid(errors) =>
        assertEquals(
          errors.toList,
          List(CyclicAliasDefinition(NonEmptyList.of(ModelId("ns", "Self"), ModelId("ns", "Self"))))
        )
      case other                     =>
        fail(s"Expected invalid validation, got $other")
    }
  }

  test("validate rejects duplicate operation ids") {
    val duplicateOperations =
      ServiceModel(
        validService.id,
        validService.meta,
        List(validService.operations.head, validService.operations.head)
      )

    validator.validate(validModelSet, duplicateOperations) match {
      case Validated.Invalid(errors) =>
        assertEquals(
          errors.toList,
          List(DuplicateId(ModelId("ns", "Create"), models = 0, services = 0, operations = 2)))
      case other                     =>
        fail(s"Expected invalid validation, got $other")
    }
  }

  test("validate rejects a model id that collides with the service id") {
    val collidingModelSet =
      ModelSet[Unit](
        List(
          Model.Structure(ModelId("ns", "Example"), meta, List(Field("id", StringT))),
          inputShape,
          outputShape,
          errorShape
        )
      )

    validator.validate(collidingModelSet, validService) match {
      case Validated.Invalid(errors) =>
        assertEquals(
          errors.toList,
          List(DuplicateId(ModelId("ns", "Example"), models = 1, services = 1, operations = 0))
        )
      case other                     =>
        fail(s"Expected invalid validation, got $other")
    }
  }

  test("validate rejects a model id that collides with an operation id") {
    val collidingModelSet =
      ModelSet[Unit](
        List(
          Model.Structure(ModelId("ns", "Create"), meta, List(Field("id", StringT))),
          inputShape,
          outputShape,
          errorShape
        )
      )

    validator.validate(collidingModelSet, validService) match {
      case Validated.Invalid(errors) =>
        assertEquals(
          errors.toList,
          List(DuplicateId(ModelId("ns", "Create"), models = 1, services = 0, operations = 1))
        )
      case other                     =>
        fail(s"Expected invalid validation, got $other")
    }
  }

  test("validate rejects a service id that collides with an operation id") {
    val collidingService =
      ServiceModel(
        ModelId("ns", "Create"),
        validService.meta,
        validService.operations
      )

    validator.validate(validModelSet, collidingService) match {
      case Validated.Invalid(errors) =>
        assertEquals(
          errors.toList,
          List(DuplicateId(ModelId("ns", "Create"), models = 0, services = 1, operations = 1))
        )
      case other                     =>
        fail(s"Expected invalid validation, got $other")
    }
  }

  test("validate rejects unresolved operation model refs") {
    val unresolvedInput =
      ServiceModel(
        validService.id,
        validService.meta,
        List(
          OperationModel(
            ModelId("ns", "Create"),
            OperationMeta(None, Nil, ()),
            input = Some(refOf("MissingInput")),
            output = Some(refOf("CreateOutput")),
            errors = List(refOf("Conflict"))
          )
        )
      )

    validator.validate(validModelSet, unresolvedInput) match {
      case Validated.Invalid(errors) =>
        assertEquals(
          errors.toList,
          List(UnresolvedModelRef(refOf("MissingInput"), "input of operation ns#Create"))
        )
      case other                     =>
        fail(s"Expected invalid validation, got $other")
    }
  }

  test("validate accumulates model set, service, and cross-cutting errors") {
    val duplicateAlias  =
      Model.Alias(ModelId("ns", "CreateInput"), meta, IntegerT)
    val invalidModelSet = ModelSet[Unit](List(inputShape, duplicateAlias))
    val invalidService  =
      ServiceModel(
        ModelId("ns", "Example"),
        ServiceMeta(None, Nil, ()),
        List(
          OperationModel(
            ModelId("ns", "Create"),
            OperationMeta(None, Nil, ()),
            input = Some(refOf("MissingInput")),
            output = None,
            errors = Nil
          ),
          OperationModel(
            ModelId("ns", "Create"),
            OperationMeta(None, Nil, ()),
            input = None,
            output = None,
            errors = Nil
          )
        )
      )

    validator.validate(invalidModelSet, invalidService) match {
      case Validated.Invalid(errors) =>
        assert(
          errors.toList.contains(DuplicateId(ModelId("ns", "CreateInput"), models = 2, services = 0, operations = 0)))
        assert(errors.toList.contains(DuplicateId(ModelId("ns", "Create"), models = 0, services = 0, operations = 2)))
        assert(
          errors.toList.contains(
            UnresolvedModelRef(refOf("MissingInput"), "input of operation ns#Create")
          )
        )
      case other                     =>
        fail(s"Expected invalid validation, got $other")
    }
  }

  test("model meta validator failures surface as invalid model metadata") {
    given ModelMetaValidator[String]        = ModelMetaValidator { model =>
      CodegenValidated.fromErrors(List(InvalidModelMeta(model.id, "feature required")))
    }
    given ModelValidator[String]            = ModelValidator.default
    given ModelSetValidator[String]         = ModelSetValidator.default
    given ServiceModelValidator[Unit, Unit] = ServiceModelValidator.default

    val modelSet =
      ModelSet[String](
        List(
          Model.Structure(
            ModelId("ns", "Account"),
            ModelMeta(None, Nil, "sql"),
            List(Field("id", StringT))
          )
        )
      )
    val service  =
      ServiceModel(ModelId("ns", "Example"), ServiceMeta(None, Nil, ()), List.empty[OperationModel[Unit]])

    SystemValidator.default[String, Unit, Unit].validate(modelSet, service) match {
      case Validated.Invalid(errors) =>
        assertEquals(
          errors.toList,
          List(InvalidModelMeta(ModelId("ns", "Account"), "feature required"))
        )
      case other                     =>
        fail(s"Expected invalid validation, got $other")
    }
  }

  test("operation meta validator failures surface as invalid operation metadata") {
    given OperationMetaValidator[String]      = OperationMetaValidator { operation =>
      CodegenValidated.fromErrors(List(InvalidOperationMeta(operation.id, "binding required")))
    }
    given OperationValidator[String]          = OperationValidator.default
    given ServiceModelValidator[Unit, String] = ServiceModelValidator.default
    given ModelSetValidator[Unit]             = ModelSetValidator.default

    val service =
      ServiceModel(
        ModelId("ns", "Example"),
        ServiceMeta(None, Nil, ()),
        List(
          OperationModel(
            ModelId("ns", "Create"),
            OperationMeta(None, Nil, "http"),
            input = None,
            output = None,
            errors = Nil
          )
        )
      )

    SystemValidator.default[Unit, Unit, String].validate(ModelSet[Unit](Nil), service) match {
      case Validated.Invalid(errors) =>
        assertEquals(
          errors.toList,
          List(InvalidOperationMeta(ModelId("ns", "Create"), "binding required"))
        )
      case other                     =>
        fail(s"Expected invalid validation, got $other")
    }
  }

  test("service meta validator failures surface as invalid service metadata") {
    given ServiceMetaValidator[String]        = ServiceMetaValidator { [B] => (service: ServiceModel[String, B]) =>
      CodegenValidated.fromErrors(List(InvalidServiceMeta(service.id, "version required")))
    }
    given ServiceModelValidator[String, Unit] = ServiceModelValidator.default
    given ModelSetValidator[Unit]             = ModelSetValidator.default

    val service =
      ServiceModel(
        ModelId("ns", "Example"),
        ServiceMeta(None, Nil, "missing-version"),
        List.empty[OperationModel[Unit]]
      )

    SystemValidator.default[Unit, String, Unit].validate(ModelSet[Unit](Nil), service) match {
      case Validated.Invalid(errors) =>
        assertEquals(
          errors.toList,
          List(InvalidServiceMeta(ModelId("ns", "Example"), "version required"))
        )
      case other                     =>
        fail(s"Expected invalid validation, got $other")
    }
  }
}
