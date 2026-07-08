package com.jacoby6000.smithplates.plugin

import cats.data.Validated
import com.jacoby6000.smithplates.codegen.core.planning.ArtifactKind
import com.jacoby6000.smithplates.codegen.core.planning.CodegenOutput
import com.jacoby6000.smithplates.codegen.core.planning.CodegenPlanner
import com.jacoby6000.smithplates.codegen.core.planning.CodegenPlanner.internal.mergeOutputs
import com.jacoby6000.smithplates.codegen.core.planning.OutputId
import com.jacoby6000.smithplates.codegen.core.planning.SmithyBinding
import munit.FunSuite

import java.nio.charset.StandardCharsets
import java.nio.file.Files

class ConsumerCodegenOutputsSpec extends FunSuite {
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

  test("compose appends additional outputs after bundled outputs") {
    val bundled    = List(templateOutput("bundled", "a.ssp"))
    val additional = List(templateOutput("additional", "b.ssp"))
    assertEquals(
      ConsumerCodegenOutputs.compose(bundled, additional).map(_.id.value),
      List("bundled", "additional")
    )
  }

  test("qualify rewrites relative template paths under classpath additional directory") {
    val qualified =
      ConsumerCodegenOutputs.qualify(
        "classpath:additional-templates/python/src/db",
        templateOutput("custom", "middleware.ssp")
      )
    assertEquals(
      qualified.asInstanceOf[CodegenOutput.CodegenTemplateBindingOutput].templatePath,
      "classpath:additional-templates/python/src/db/middleware.ssp"
    )
  }

  test("qualify rewrites relative paths under filesystem additional directory") {
    val qualified =
      ConsumerCodegenOutputs.qualify(
        "/tmp/consumer-templates",
        templateOutput("custom", "middleware.ssp")
      )
    assert(
      qualified
        .asInstanceOf[CodegenOutput.CodegenTemplateBindingOutput]
        .templatePath
        .startsWith("file:")
    )
    assert(
      qualified
        .asInstanceOf[CodegenOutput.CodegenTemplateBindingOutput]
        .templatePath
        .endsWith("/middleware.ssp")
    )

    val qualifiedStatic =
      ConsumerCodegenOutputs.qualify(
        "/tmp/consumer-templates",
        CodegenOutput.CodegenStaticOutput(
          id = OutputId("static"),
          kind = ArtifactKind.Src,
          filePath = "support/runtime.py",
          copyToPath = "{{smithyNamespaceDir}}/runtime.py"
        )
      )
    assert(qualifiedStatic.asInstanceOf[CodegenOutput.CodegenStaticOutput].filePath.startsWith("file:"))
    assert(qualifiedStatic.asInstanceOf[CodegenOutput.CodegenStaticOutput].filePath.endsWith("/support/runtime.py"))
  }

  test("qualify leaves already-qualified classpath paths unchanged") {
    val alreadyQualified = "classpath:other/root/template.ssp"
    val qualified        =
      ConsumerCodegenOutputs.qualify(
        "classpath:additional-templates/python/src/db",
        templateOutput("custom", alreadyQualified)
      )
    assertEquals(
      qualified.asInstanceOf[CodegenOutput.CodegenTemplateBindingOutput].templatePath,
      alreadyQualified
    )
  }

  test("qualify leaves already-qualified file paths unchanged") {
    val alreadyQualified = "file:/tmp/consumer-templates/middleware.ssp"
    val qualified        =
      ConsumerCodegenOutputs.qualify(
        "/tmp/consumer-templates",
        templateOutput("custom", alreadyQualified)
      )
    assertEquals(
      qualified.asInstanceOf[CodegenOutput.CodegenTemplateBindingOutput].templatePath,
      alreadyQualified
    )
  }

  test("additionalOutputs loads deck and qualifies template paths") {
    ConsumerCodegenOutputs.additionalOutputs(
      "classpath:additional-templates/python/src/db",
      enabledKeys = Nil,
      classLoader = getClass.getClassLoader
    ) match {
      case Validated.Valid(outputs)  =>
        assert(outputs.exists(_.id.value == "custom.sql.middleware"))
        val templatePath =
          outputs
            .collectFirst { case template: CodegenOutput.CodegenTemplateBindingOutput =>
              template.templatePath
            }
            .getOrElse(fail("expected template output"))
        assertEquals(
          templatePath,
          "classpath:additional-templates/python/src/db/middleware.ssp"
        )
      case Validated.Invalid(errors) =>
        fail(errors.toList.map(_.message).mkString("; "))
    }
  }

  test("additionalOutputs tolerates missing variant keys via forAvailable") {
    ConsumerCodegenOutputs.additionalOutputs(
      "classpath:additional-templates/python/src/db",
      enabledKeys = List("sqlite", "postgres"),
      classLoader = getClass.getClassLoader
    ) match {
      case Validated.Valid(outputs)  =>
        assert(outputs.exists(_.id.value == "custom.sql.middleware"))
        assert(!outputs.exists(_.id.value.contains("sqlite")))
      case Validated.Invalid(errors) =>
        fail(errors.toList.map(_.message).mkString("; "))
    }
  }

  test("additionalOutputs loads deck from filesystem directory") {
    val root = Files.createTempDirectory("smithplates-consumer-deck")
    try {
      Files.writeString(
        root.resolve("outputs.json"),
        """{
          |  "shared": [
          |    {
          |      "id": "custom.fs",
          |      "artifactKind": "src",
          |      "template": "middleware.ssp",
          |      "outputPath": "{{smithyNamespaceDir}}/middleware.py",
          |      "binding": { "type": "once" }
          |    }
          |  ],
          |  "variants": {}
          |}
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      Files.writeString(root.resolve("middleware.ssp"), "# external middleware", StandardCharsets.UTF_8)

      ConsumerCodegenOutputs.additionalOutputs(
        root.toString,
        enabledKeys = Nil,
        classLoader = getClass.getClassLoader
      ) match {
        case Validated.Valid(outputs)  =>
          val templatePath =
            outputs
              .collectFirst { case template: CodegenOutput.CodegenTemplateBindingOutput =>
                template.templatePath
              }
              .getOrElse(fail("expected template output"))
          assert(templatePath.startsWith("file:"))
          assert(templatePath.endsWith("/middleware.ssp"))
        case Validated.Invalid(errors) =>
          fail(errors.toList.map(_.message).mkString("; "))
      }
    } finally {
      val _ = root.toFile.delete()
    }
  }

  test("planner mergeOutputs removes bundled output overridden by additional deck entry") {
    val bundled    = List(templateOutput("python.sql.db.models", "models/models.ssp"))
    val additional =
      List(templateOutput("custom.models", "custom_models.ssp", overrides = Some("python.sql.db.models")))
    mergeOutputs(ConsumerCodegenOutputs.compose(bundled, additional)) match {
      case Validated.Valid(merged)   =>
        assertEquals(merged.map(_.id.value), List("custom.models"))
      case Validated.Invalid(errors) =>
        fail(errors.toList.map(_.message).mkString("; "))
    }
  }
}
