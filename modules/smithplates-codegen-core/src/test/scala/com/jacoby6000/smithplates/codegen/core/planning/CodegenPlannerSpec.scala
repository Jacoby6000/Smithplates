package com.jacoby6000.smithplates.codegen.core.planning

import cats.data.NonEmptyList
import cats.data.Validated
import com.jacoby6000.smithplates.codegen.core.*
import com.jacoby6000.smithplates.codegen.core.NeutralType.*
import com.jacoby6000.smithplates.codegen.core.planning.CodegenPlanner.internal.*
import com.jacoby6000.smithplates.codegen.core.strategy.Conventions
import com.jacoby6000.smithplates.codegen.core.strategy.NamingConvention
import com.jacoby6000.smithplates.codegen.core.strategy.NamingStrategy
import munit.FunSuite

class CodegenPlannerSpec extends FunSuite {
  private val conventions =
    Conventions.fromStrategy(
      NamingStrategy(
        fileNames = NamingConvention.SnakeCase.withSuffix(".py"),
        packageSeparator = ".",
        classNames = NamingConvention.Unchanged,
        packageNames = NamingConvention.Unchanged,
        valueNames = NamingConvention.SnakeCase,
        constantNames = NamingConvention.ScreamingSnakeCase,
        functionNames = NamingConvention.SnakeCase
      )
    )

  private val settings =
    CodegenSettings(
      sourceOutputDirectory = "src",
      testOutputDirectory = "test",
      conventions = conventions,
      staticResourceLoader = StaticResourceLoader.fromMap(Map("static/init.py" -> "# static"))
    )

  private val templateRenderer =
    TemplateRenderer.fromFunction { (templatePath, view) =>
      s"$templatePath:${view.subject}:${view.usedTypes.map(_.id.name).mkString(",")}"
    }

  private def meta(tags: List[String] = Nil): ModelMeta[Unit] =
    ModelMeta(None, tags, ())

  private def ref(name: String): ModelRef =
    ModelRef(ModelId("example", name))

  private val widget       =
    Model.Structure(ModelId("example", "Widget"), meta(), List(Field("id", StringT)))
  private val widgetInput  =
    Model.Structure(ModelId("example", "WidgetInput"), meta(), List(Field("label", StringT)))
  private val widgetOutput =
    Model.Structure(ModelId("example", "WidgetOutput"), meta(), List(Field("id", StringT)))

  private val models =
    ModelSet[Unit](List(widget, widgetInput, widgetOutput))

  private val createOp   =
    OperationModel(
      ModelId("example", "CreateWidget"),
      OperationMeta(None, List("admin", "widgets"), ()),
      input = Some(ref("WidgetInput")),
      output = Some(ref("WidgetOutput")),
      errors = Nil
    )
  private val listOp     =
    OperationModel(
      ModelId("example", "ListWidgets"),
      OperationMeta(None, List("widgets"), ()),
      input = None,
      output = Some(ref("Widget")),
      errors = Nil
    )
  private val untaggedOp =
    OperationModel(
      ModelId("example", "HealthCheck"),
      OperationMeta(None, Nil, ()),
      input = None,
      output = None,
      errors = Nil
    )

  private val service =
    ServiceModel(
      ModelId("example", "WidgetService"),
      ServiceMeta(None, Nil, ()),
      List(createOp, listOp, untaggedOp)
    )

  private def templateOutput(
      id: String,
      binding: SmithyBinding,
      outputPath: String = "{{serviceFileName}}.py",
      overrides: Option[String] = None
  ): CodegenOutput.CodegenTemplateBindingOutput =
    CodegenOutput.CodegenTemplateBindingOutput(
      id = OutputId(id),
      kind = ArtifactKind.Src,
      templatePath = s"templates/$id.ssp",
      outputPath = outputPath,
      binding = binding,
      overrides = overrides.map(OutputId(_))
    )

  test("mergeOutputs removes overridden bundled outputs") {
    val outputs =
      List(
        templateOutput("bundled.a", SmithyBinding.Once),
        templateOutput("bundled.b", SmithyBinding.Once),
        templateOutput("custom.a", SmithyBinding.Once, overrides = Some("bundled.a"))
      )

    assertEquals(
      mergeOutputs(outputs),
      CodegenValidated.valid(
        List(
          templateOutput("bundled.b", SmithyBinding.Once),
          templateOutput("custom.a", SmithyBinding.Once, overrides = Some("bundled.a"))
        )
      )
    )
  }

  test("mergeOutputs errors on unknown override ids") {
    val outputs = List(templateOutput("custom", SmithyBinding.Once, overrides = Some("missing")))
    mergeOutputs(outputs) match {
      case Validated.Invalid(errors) =>
        assertEquals(errors.head, UnknownOutputOverride(OutputId("missing")))
      case other                     =>
        fail(s"Expected invalid merge, got $other")
    }
  }

  test("plan expands Service binding once per service") {
    val outputs =
      List(
        templateOutput("service.app", SmithyBinding.Service, outputPath = "{{serviceFileName}}.py")
      )

    val result =
      CodegenPlanner.plan(outputs, models, List(service), settings, templateRenderer)

    result match {
      case Validated.Valid(artifacts) =>
        assertEquals(artifacts.map(_.relativePath), List("src/widget_service.py"))
        assert(artifacts.head.content.startsWith("templates/service.app.ssp:"))
        assert(artifacts.head.content.contains("WidgetInput"))
      case other                      =>
        fail(s"Expected valid plan, got $other")
    }
  }

  test("plan expands Once binding to a single artifact") {
    val outputs = List(templateOutput("once.middleware", SmithyBinding.Once, outputPath = "middleware.py"))
    val result  = CodegenPlanner.plan(outputs, models, List(service), settings, templateRenderer)

    assertEquals(
      result,
      CodegenValidated.valid(
        List(
          ResolvedArtifact(
            relativePath = "src/middleware.py",
            content = "templates/once.middleware.ssp:():",
            kind = ArtifactKind.Src
          )
        )
      )
    )
  }

  test("plan expands Operation None grouping to one artifact per operation") {
    val outputs =
      List(
        templateOutput(
          "operation.routes",
          SmithyBinding.Operation(Nil, BindingGroup.None),
          outputPath = "routes/{{operationFileName}}"
        )
      )

    val result = CodegenPlanner.plan(outputs, models, List(service), settings, templateRenderer)

    assertEquals(
      result.map(_.map(_.relativePath)),
      CodegenValidated.valid(
        List(
          "src/routes/create_widget.py",
          "src/routes/list_widgets.py",
          "src/routes/health_check.py"
        ))
    )
  }

  test("plan expands Operation Tag grouping to one artifact per distinct tag") {
    val outputs =
      List(
        templateOutput(
          "operation.tagged",
          SmithyBinding.Operation(List(BindingFilterAtom.Tagged), BindingGroup.Tag),
          outputPath = "apis/{{tagName}}_api.py"
        )
      )

    val result = CodegenPlanner.plan(outputs, models, List(service), settings, templateRenderer)

    assertEquals(
      result.map(_.map(_.relativePath).sorted),
      CodegenValidated.valid(List("src/apis/admin_api.py", "src/apis/widgets_api.py"))
    )
  }

  test("plan emits multi-tag operations once per distinct tag") {
    val outputs =
      List(
        templateOutput(
          "operation.multi_tag",
          SmithyBinding.Operation(Nil, BindingGroup.Tag),
          outputPath = "tags/{{tagName}}.py"
        )
      )

    val result = CodegenPlanner.plan(outputs, models, List(service), settings, templateRenderer)

    assertEquals(
      result.map(_.map(_.relativePath).sorted),
      CodegenValidated.valid(List("src/tags/admin.py", "src/tags/widgets.py"))
    )
  }

  test("plan expands Model Kind filter and None grouping per matching model") {
    val outputs =
      List(
        templateOutput(
          "model.structure",
          SmithyBinding.Model(List(BindingFilterAtom.Kind(ModelKind.Structure)), BindingGroup.None),
          outputPath = "models/{{modelFileName}}"
        )
      )

    val result = CodegenPlanner.plan(outputs, models, List(service), settings, templateRenderer)

    assertEquals(
      result.map(_.map(_.relativePath).sorted),
      CodegenValidated.valid(
        List("src/models/widget.py", "src/models/widget_input.py", "src/models/widget_output.py")
      )
    )
  }

  test("plan expands Model Tag grouping to one artifact per distinct tag") {
    val taggedWidget =
      Model.Structure(ModelId("example", "TaggedWidget"), meta(tags = List("alpha", "beta")), Nil)
    val taggedModels = ModelSet[Unit](List(taggedWidget))

    val outputs =
      List(
        templateOutput(
          "model.tagged",
          SmithyBinding.Model(Nil, BindingGroup.Tag),
          outputPath = "groups/{{tagName}}.py"
        )
      )

    val result = CodegenPlanner.plan(outputs, taggedModels, List(service), settings, templateRenderer)

    assertEquals(
      result.map(_.map(_.relativePath).sorted),
      CodegenValidated.valid(List("src/groups/alpha.py", "src/groups/beta.py"))
    )
  }

  test("plan copies static outputs from the resource loader") {
    val outputs =
      List(
        CodegenOutput.CodegenStaticOutput(
          id = OutputId("static.init"),
          kind = ArtifactKind.Src,
          filePath = "static/init.py",
          copyToPath = "{{serviceFileName}}/__init__.py"
        )
      )

    val result = CodegenPlanner.plan(outputs, models, List(service), settings, templateRenderer)

    assertEquals(
      result,
      CodegenValidated.valid(
        List(
          ResolvedArtifact(
            relativePath = "src/widget_service/__init__.py",
            content = "# static",
            kind = ArtifactKind.Src
          )
        )
      )
    )
  }

  test("plan errors on duplicate resolved output paths") {
    val outputs =
      List(
        templateOutput("first", SmithyBinding.Once, outputPath = "duplicate.py"),
        templateOutput("second", SmithyBinding.Once, outputPath = "duplicate.py")
      )

    CodegenPlanner.plan(outputs, models, List(service), settings, templateRenderer) match {
      case Validated.Invalid(errors) =>
        assertEquals(
          errors.head,
          DuplicateResolvedOutputPath(
            "src/duplicate.py",
            NonEmptyList.of(OutputId("first"), OutputId("second"))
          )
        )
      case other                     =>
        fail(s"Expected path collision error, got $other")
    }
  }
}
