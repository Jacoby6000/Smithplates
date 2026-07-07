package com.jacoby6000.smithplates.plugin

import cats.data.Validated
import com.jacoby6000.smithplates.codegen.core.planning.ArtifactKind
import com.jacoby6000.smithplates.codegen.core.planning.CodegenOutput
import com.jacoby6000.smithplates.codegen.core.planning.OutputId
import com.jacoby6000.smithplates.codegen.core.planning.SmithyBinding
import com.jacoby6000.smithplates.sql.SqlValidated
import munit.FunSuite

class ConsumerCodegenOutputValidatorSpec extends FunSuite {
  private val bundledIds = Set(OutputId("python.sql.db.models"))

  private def templateOutput(
      id: String,
      templatePath: String,
      overrides: Option[String] = None
  ): CodegenOutput.CodegenTemplateBindingOutput =
    CodegenOutput.CodegenTemplateBindingOutput(
      id = OutputId(id),
      kind = ArtifactKind.Src,
      templatePath = templatePath,
      outputPath = "{{smithyNamespaceDir}}/out.py",
      binding = SmithyBinding.Once,
      overrides = overrides.map(OutputId(_))
    )

  private def validate(
      outputs: List[CodegenOutput],
      enableExternalTemplates: Boolean = false,
      templateExists: CodegenOutput.CodegenTemplateBindingOutput => Boolean = _ => true
  ): SqlValidated[Unit] =
    ConsumerCodegenOutputValidator.validateAdditionalDeck(
      configPath = "smithplates.python.sql.additionalTemplatesDirectory",
      additionalOutputs = outputs,
      bundledIds = bundledIds,
      enableExternalTemplates = enableExternalTemplates,
      templateClasspathExists = templateExists
    )

  test("validateAdditionalDeck accepts a distinct id with a valid override target") {
    validate(
      List(
        templateOutput(
          "custom.models",
          "classpath:additional-templates/python/src/db/middleware.ssp",
          overrides = Some("python.sql.db.models")
        )
      )
    ) match {
      case Validated.Valid(_)        => ()
      case Validated.Invalid(errors) => fail(errors.toList.map(_.message).mkString("; "))
    }
  }

  test("validateAdditionalDeck rejects duplicate additional ids") {
    val duplicate = templateOutput("dup", "a.ssp")
    validate(List(duplicate, duplicate)) match {
      case Validated.Valid(_)        => fail("expected duplicate id error")
      case Validated.Invalid(errors) => assert(errors.exists(_.message.contains("duplicate output id 'dup'")))
    }
  }

  test("validateAdditionalDeck rejects id collision with bundled deck") {
    validate(List(templateOutput("python.sql.db.models", "models/models.ssp"))) match {
      case Validated.Valid(_)        => fail("expected bundled id collision error")
      case Validated.Invalid(errors) =>
        assert(errors.exists(_.message.contains("collides with a bundled output id")))
    }
  }

  test("validateAdditionalDeck rejects unknown override targets") {
    validate(List(templateOutput("custom", "a.ssp", overrides = Some("missing.id")))) match {
      case Validated.Valid(_)        => fail("expected unknown override error")
      case Validated.Invalid(errors) =>
        assert(errors.exists(_.message.contains("unknown bundled output override id")))
    }
  }

  test("validateAdditionalDeck rejects missing classpath templates without enableExternalTemplates") {
    validate(
      outputs = List(templateOutput("custom", "classpath:missing/template.ssp")),
      enableExternalTemplates = false,
      templateExists = _ => false
    ) match {
      case Validated.Valid(_)        => fail("expected external template gate error")
      case Validated.Invalid(errors) => assert(errors.exists(_.message.contains("enableExternalTemplates")))
    }
  }

  test("validateAdditionalDeck allows missing classpath templates when enableExternalTemplates is true") {
    validate(
      outputs = List(templateOutput("custom", "classpath:missing/template.ssp")),
      enableExternalTemplates = true,
      templateExists = _ => false
    ) match {
      case Validated.Valid(_)        => ()
      case Validated.Invalid(errors) => fail(errors.toList.map(_.message).mkString("; "))
    }
  }
}
