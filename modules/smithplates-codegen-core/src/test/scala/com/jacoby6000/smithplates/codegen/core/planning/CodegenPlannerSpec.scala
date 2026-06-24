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
      outputPath: String = "{{serviceFileName}}",
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

  test("mergeOutputs errors on self override ids") {
    val outputs = List(templateOutput("foo", SmithyBinding.Once, overrides = Some("foo")))
    mergeOutputs(outputs) match {
      case Validated.Invalid(errors) =>
        assertEquals(errors.head, SelfOutputOverride(OutputId("foo")))
      case other                     =>
        fail(s"Expected self override error, got $other")
    }
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

  test("mergeOutputs errors on duplicate output ids") {
    val outputs =
      List(
        templateOutput("duplicate", SmithyBinding.Once),
        templateOutput("duplicate", SmithyBinding.Once, outputPath = "other.py")
      )

    mergeOutputs(outputs) match {
      case Validated.Invalid(errors) =>
        assertEquals(errors.head, DuplicateOutputId(OutputId("duplicate")))
      case other                     =>
        fail(s"Expected duplicate id error, got $other")
    }
  }

  test("plan override-by-id avoids path collision with replaced bundled output") {
    val outputs =
      List(
        templateOutput("bundled", SmithyBinding.Once, outputPath = "shared.py"),
        templateOutput("custom", SmithyBinding.Once, outputPath = "shared.py", overrides = Some("bundled"))
      )

    assertEquals(
      CodegenPlanner.plan(outputs, models, List(service), settings, templateRenderer),
      CodegenValidated.valid(
        List(
          ResolvedArtifact(
            relativePath = "src/shared.py",
            content = "templates/custom.ssp:():",
            kind = ArtifactKind.Src
          )
        )
      )
    )
  }

  test("plan expands Operation Untagged All grouping to one artifact") {
    val outputs =
      List(
        templateOutput(
          "operation.untagged_all",
          SmithyBinding.Operation(List(BindingFilterAtom.Untagged), BindingGroup.All),
          outputPath = "routes/untagged.py"
        )
      )

    val result = CodegenPlanner.plan(outputs, models, List(service), settings, templateRenderer)

    result match {
      case Validated.Valid(artifacts) =>
        assertEquals(artifacts.map(_.relativePath), List("src/routes/untagged.py"))
        assert(artifacts.head.content.contains("HealthCheck"))
      case other                      =>
        fail(s"Expected valid plan, got $other")
    }
  }

  test("plan emits no artifacts for Operation All grouping when nothing matches") {
    val outputs =
      List(
        templateOutput(
          "operation.empty_all",
          SmithyBinding.Operation(
            List(BindingFilterAtom.Untagged, BindingFilterAtom.Tagged),
            BindingGroup.All
          ),
          outputPath = "routes/empty.py"
        )
      )

    assertEquals(
      CodegenPlanner.plan(outputs, models, List(service), settings, templateRenderer),
      CodegenValidated.valid(Nil)
    )
  }

  test("plan expands Operation Untagged None grouping per untagged operation") {
    val outputs =
      List(
        templateOutput(
          "operation.untagged_none",
          SmithyBinding.Operation(List(BindingFilterAtom.Untagged), BindingGroup.None),
          outputPath = "ops/{{operationFileName}}"
        )
      )

    assertEquals(
      CodegenPlanner.plan(outputs, models, List(service), settings, templateRenderer).map(_.map(_.relativePath)),
      CodegenValidated.valid(List("src/ops/health_check.py"))
    )
  }

  test("plan expands Model All grouping with namespace path bindings") {
    val outputs =
      List(
        templateOutput(
          "model.all",
          SmithyBinding.Model(List(BindingFilterAtom.Kind(ModelKind.Structure)), BindingGroup.All),
          outputPath = "{{smithyNamespaceDir}}/all_structures.py"
        )
      )

    CodegenPlanner.plan(outputs, models, List(service), settings, templateRenderer) match {
      case Validated.Valid(artifacts) =>
        assertEquals(artifacts.head.relativePath, "src/example/all_structures.py")
        assert(artifacts.head.content.startsWith("templates/model.all.ssp:"))
      case other                      =>
        fail(s"Expected valid plan, got $other")
    }
  }

  test("plan emits no artifacts for Model All grouping when nothing matches") {
    val outputs =
      List(
        templateOutput(
          "model.empty_all",
          SmithyBinding.Model(List(BindingFilterAtom.Tagged), BindingGroup.All),
          outputPath = "models/all.py"
        )
      )

    assertEquals(
      CodegenPlanner.plan(outputs, models, List(service), settings, templateRenderer),
      CodegenValidated.valid(Nil)
    )
  }

  test("plan errors on grouped models spanning multiple namespaces when paths need namespace bindings") {
    val mixedModels =
      ModelSet[Unit](
        List(
          Model.Structure(ModelId("example.a", "Widget"), meta(), Nil),
          Model.Structure(ModelId("example.b", "Widget"), meta(), Nil)
        )
      )
    val outputs     =
      List(
        templateOutput(
          "model.mixed",
          SmithyBinding.Model(Nil, BindingGroup.All),
          outputPath = "{{smithyNamespaceDir}}/models/all.py"
        )
      )

    CodegenPlanner.plan(outputs, mixedModels, List(service), settings, templateRenderer) match {
      case Validated.Invalid(errors) =>
        assertEquals(
          errors.head,
          InconsistentGroupedModelNamespaces(OutputId("model.mixed"), NonEmptyList.of("example.a", "example.b")))
      case other                     =>
        fail(s"Expected inconsistent namespace error, got $other")
    }
  }

  test("plan rejects model-specific placeholders for grouped model outputs") {
    val outputs =
      List(
        templateOutput(
          "model.grouped_ambiguous",
          SmithyBinding.Model(List(BindingFilterAtom.Kind(ModelKind.Structure)), BindingGroup.All),
          outputPath = "models/{{modelFileName}}"
        )
      )

    CodegenPlanner.plan(outputs, models, List(service), settings, templateRenderer) match {
      case Validated.Invalid(errors) =>
        assertEquals(errors.head, UnresolvedPathPlaceholder("{{modelFileName}}"))
      case other                     =>
        fail(s"Expected unresolved grouped model placeholder error, got $other")
    }
  }

  test("plan errors on invalid operation kind filters") {
    val outputs =
      List(
        templateOutput(
          "operation.invalid",
          SmithyBinding.Operation(List(BindingFilterAtom.Kind(ModelKind.Structure)), BindingGroup.None),
          outputPath = "routes/{{operationFileName}}"
        )
      )

    CodegenPlanner.plan(outputs, models, List(service), settings, templateRenderer) match {
      case Validated.Invalid(errors) =>
        assertEquals(
          errors.head,
          InvalidOperationBindingFilter(BindingFilterAtom.Kind(ModelKind.Structure))
        )
      case other                     =>
        fail(s"Expected invalid filter error, got $other")
    }
  }

  test("plan errors on missing static resources") {
    val outputs =
      List(
        CodegenOutput.CodegenStaticOutput(
          id = OutputId("static.missing"),
          kind = ArtifactKind.Src,
          filePath = "static/missing.py",
          copyToPath = "missing.py"
        )
      )

    CodegenPlanner.plan(outputs, models, List(service), settings, templateRenderer) match {
      case Validated.Invalid(errors) =>
        assertEquals(errors.head, MissingStaticResource("static/missing.py", OutputId("static.missing")))
      case other                     =>
        fail(s"Expected missing static resource error, got $other")
    }
  }

  test("plan errors when template rendering fails") {
    val failingRenderer =
      TemplateRenderer.fromFunction[String, Unit] { (_, _) =>
        throw new RuntimeException("boom")
      }
    val outputs         = List(templateOutput("broken", SmithyBinding.Once, outputPath = "broken.py"))

    CodegenPlanner.plan(outputs, models, List(service), settings, failingRenderer) match {
      case Validated.Invalid(errors) =>
        assertEquals(errors.head, TemplateRenderFailed("templates/broken.ssp", "boom"))
      case other                     =>
        fail(s"Expected template render failure, got $other")
    }
  }

  test("plan writes test artifacts to the test output directory") {
    val outputs =
      List(
        CodegenOutput.CodegenTemplateBindingOutput(
          id = OutputId("test.once"),
          kind = ArtifactKind.Test,
          templatePath = "templates/test.once.ssp",
          outputPath = "support.py",
          binding = SmithyBinding.Once
        )
      )

    assertEquals(
      CodegenPlanner.plan(outputs, models, List(service), settings, templateRenderer),
      CodegenValidated.valid(
        List(
          ResolvedArtifact(
            relativePath = "test/support.py",
            content = "templates/test.once.ssp:():",
            kind = ArtifactKind.Test
          )
        )
      )
    )
  }

  test("plan errors when multi-service static outputs collide on a constant path") {
    val otherService =
      ServiceModel(
        ModelId("example", "OtherService"),
        ServiceMeta(None, Nil, ()),
        List(untaggedOp)
      )
    val outputs      =
      List(
        CodegenOutput.CodegenStaticOutput(
          id = OutputId("static.shared"),
          kind = ArtifactKind.Src,
          filePath = "static/init.py",
          copyToPath = "shared/__init__.py"
        )
      )

    CodegenPlanner.plan(outputs, models, List(service, otherService), settings, templateRenderer) match {
      case Validated.Invalid(errors) =>
        assertEquals(
          errors.head,
          DuplicateResolvedOutputPath("src/shared/__init__.py", NonEmptyList.one(OutputId("static.shared")))
        )
      case other                     =>
        fail(s"Expected path collision error, got $other")
    }
  }

  test("plan resolves usedTypes for operation subjects") {
    val outputs =
      List(
        templateOutput(
          "operation.subject",
          SmithyBinding.Operation(Nil, BindingGroup.None),
          outputPath = "routes/{{operationFileName}}"
        )
      )

    val result = CodegenPlanner.plan(outputs, models, List(service), settings, templateRenderer)

    result match {
      case Validated.Valid(artifacts) =>
        val createWidget =
          artifacts.find(_.relativePath == "src/routes/create_widget.py").getOrElse {
            fail("Expected create_widget artifact")
          }
        assert(createWidget.content.contains("WidgetInput"))
        assert(createWidget.content.contains("WidgetOutput"))
      case other                      =>
        fail(s"Expected valid plan, got $other")
    }
  }

  test("plan errors on unresolved usedTypes refs") {
    val brokenService =
      ServiceModel(
        ModelId("example", "BrokenService"),
        ServiceMeta(None, Nil, ()),
        List(
          OperationModel(
            ModelId("example", "BrokenOp"),
            OperationMeta(None, Nil, ()),
            input = Some(ref("MissingModel")),
            output = None,
            errors = Nil
          )
        )
      )
    val outputs       =
      List(
        templateOutput("service.broken", SmithyBinding.Service, outputPath = "{{serviceFileName}}")
      )

    CodegenPlanner.plan(outputs, models, List(brokenService), settings, templateRenderer) match {
      case Validated.Invalid(errors) =>
        assertEquals(
          errors.head,
          UnresolvedModelRef(ref("MissingModel"), "codegen output service.broken usedTypes")
        )
      case other                     =>
        fail(s"Expected unresolved model ref error, got $other")
    }
  }

  test("plan expands Service binding once per service") {
    val outputs =
      List(
        templateOutput("service.app", SmithyBinding.Service, outputPath = "{{serviceFileName}}")
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
          copyToPath = "packages/{{serviceFileName}}"
        )
      )

    val result = CodegenPlanner.plan(outputs, models, List(service), settings, templateRenderer)

    assertEquals(
      result,
      CodegenValidated.valid(
        List(
          ResolvedArtifact(
            relativePath = "src/packages/widget_service.py",
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

  test("plan reports path collisions before rendering output content") {
    val failingRenderer =
      TemplateRenderer.fromFunction[String, Unit] { (_, _) =>
        throw new RuntimeException("boom")
      }
    val outputs         =
      List(
        templateOutput("first", SmithyBinding.Once, outputPath = "duplicate.py"),
        templateOutput("second", SmithyBinding.Once, outputPath = "duplicate.py")
      )

    CodegenPlanner.plan(outputs, models, List(service), settings, failingRenderer) match {
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

  test("plan errors on unresolved path placeholders") {
    val outputs =
      List(
        templateOutput(
          "broken.placeholder",
          SmithyBinding.Once,
          outputPath = "apis/{{tagName}}_api.py"
        )
      )

    CodegenPlanner.plan(outputs, models, List(service), settings, templateRenderer) match {
      case Validated.Invalid(errors) =>
        assertEquals(errors.head, UnresolvedPathPlaceholder("{{tagName}}"))
      case other                     =>
        fail(s"Expected unresolved placeholder error, got $other")
    }
  }

  test("plan expands Model Tag grouping with namespace path bindings") {
    val taggedWidget =
      Model.Structure(ModelId("example", "TaggedWidget"), meta(tags = List("alpha")), Nil)
    val taggedModels = ModelSet[Unit](List(taggedWidget))

    val outputs =
      List(
        templateOutput(
          "model.tagged.namespace",
          SmithyBinding.Model(Nil, BindingGroup.Tag),
          outputPath = "{{smithyNamespaceDir}}/groups/{{tagName}}.py"
        )
      )

    assertEquals(
      CodegenPlanner.plan(outputs, taggedModels, List(service), settings, templateRenderer).map(_.map(_.relativePath)),
      CodegenValidated.valid(List("src/example/groups/alpha.py"))
    )
  }

  test("plan expands Model Tag grouping across namespaces when paths only need tag bindings") {
    val taggedModels =
      ModelSet[Unit](
        List(
          Model.Structure(ModelId("example.a", "AlphaWidget"), meta(tags = List("shared")), Nil),
          Model.Structure(ModelId("example.b", "BetaWidget"), meta(tags = List("shared")), Nil)
        )
      )

    val outputs =
      List(
        templateOutput(
          "model.tagged.cross_namespace",
          SmithyBinding.Model(Nil, BindingGroup.Tag),
          outputPath = "groups/{{tagName}}.py"
        )
      )

    assertEquals(
      CodegenPlanner.plan(outputs, taggedModels, List(service), settings, templateRenderer).map(_.map(_.relativePath)),
      CodegenValidated.valid(List("src/groups/shared.py"))
    )
  }

  test("plan errors when Model None fan-out collides on a constant path") {
    val outputs =
      List(
        templateOutput(
          "model.collision",
          SmithyBinding.Model(List(BindingFilterAtom.Kind(ModelKind.Structure)), BindingGroup.None),
          outputPath = "models/shared.py"
        )
      )

    CodegenPlanner.plan(outputs, models, List(service), settings, templateRenderer) match {
      case Validated.Invalid(errors) =>
        assertEquals(
          errors.head,
          DuplicateResolvedOutputPath("src/models/shared.py", NonEmptyList.one(OutputId("model.collision")))
        )
      case other                     =>
        fail(s"Expected path collision error, got $other")
    }
  }

  test("plan expands Service binding for each service in the model set") {
    val otherService =
      ServiceModel(
        ModelId("example", "OtherService"),
        ServiceMeta(None, Nil, ()),
        List(untaggedOp)
      )
    val outputs      =
      List(
        templateOutput("service.multi", SmithyBinding.Service, outputPath = "{{serviceFileName}}")
      )

    assertEquals(
      CodegenPlanner
        .plan(outputs, models, List(service, otherService), settings, templateRenderer)
        .map(_.map(_.relativePath).sorted),
      CodegenValidated.valid(List("src/other_service.py", "src/widget_service.py"))
    )
  }

  test("plan expands Operation All grouping once per service") {
    val otherService =
      ServiceModel(
        ModelId("example", "OtherService"),
        ServiceMeta(None, Nil, ()),
        List(untaggedOp)
      )
    val outputs      =
      List(
        templateOutput(
          "operation.all.multi",
          SmithyBinding.Operation(Nil, BindingGroup.All),
          outputPath = "services/{{serviceFileName}}"
        )
      )

    assertEquals(
      CodegenPlanner
        .plan(outputs, models, List(service, otherService), settings, templateRenderer)
        .map(_.map(_.relativePath).sorted),
      CodegenValidated.valid(List("src/services/other_service.py", "src/services/widget_service.py"))
    )
  }

  test("plan copies static outputs when no services are present") {
    val outputs =
      List(
        CodegenOutput.CodegenStaticOutput(
          id = OutputId("static.no_service"),
          kind = ArtifactKind.Src,
          filePath = "static/init.py",
          copyToPath = "bootstrap/__init__.py"
        )
      )

    assertEquals(
      CodegenPlanner.plan(outputs, models, Nil, settings, templateRenderer),
      CodegenValidated.valid(
        List(
          ResolvedArtifact(
            relativePath = "src/bootstrap/__init__.py",
            content = "# static",
            kind = ArtifactKind.Src
          )
        )
      )
    )
  }

  test("plan expands Model kind filters for Union Enum and Alias") {
    val unionModel =
      Model.Union(ModelId("example", "Event"), meta(), List(Variant("payload", StringT)))
    val enumModel  =
      Model.EnumModel(
        ModelId("example", "Color"),
        meta(),
        StringT,
        List(EnumValue("Red", PrimitiveLiteral.StringValue("red")))
      )
    val aliasModel = Model.Alias(ModelId("example", "Uuid"), meta(), StringT)
    val kindModels = ModelSet[Unit](List(unionModel, enumModel, aliasModel))

    val outputs =
      List(
        templateOutput(
          "model.union",
          SmithyBinding.Model(List(BindingFilterAtom.Kind(ModelKind.Union)), BindingGroup.None),
          outputPath = "unions/{{modelFileName}}"
        ),
        templateOutput(
          "model.enum",
          SmithyBinding.Model(List(BindingFilterAtom.Kind(ModelKind.Enum)), BindingGroup.None),
          outputPath = "enums/{{modelFileName}}"
        ),
        templateOutput(
          "model.alias",
          SmithyBinding.Model(List(BindingFilterAtom.Kind(ModelKind.Alias)), BindingGroup.None),
          outputPath = "aliases/{{modelFileName}}"
        )
      )

    assertEquals(
      CodegenPlanner
        .plan(outputs, kindModels, List(service), settings, templateRenderer)
        .map(_.map(_.relativePath).sorted),
      CodegenValidated.valid(
        List("src/aliases/uuid.py", "src/enums/color.py", "src/unions/event.py")
      )
    )
  }

  test("plan expands Model Tagged and Untagged filters") {
    val taggedOnly   =
      Model.Structure(ModelId("example", "TaggedOnly"), meta(tags = List("alpha")), Nil)
    val untaggedOnly = Model.Structure(ModelId("example", "UntaggedOnly"), meta(), Nil)
    val filterModels = ModelSet[Unit](List(taggedOnly, untaggedOnly))

    val outputs =
      List(
        templateOutput(
          "model.tagged_filter",
          SmithyBinding.Model(List(BindingFilterAtom.Tagged), BindingGroup.None),
          outputPath = "tagged/{{modelFileName}}"
        ),
        templateOutput(
          "model.untagged_filter",
          SmithyBinding.Model(List(BindingFilterAtom.Untagged), BindingGroup.None),
          outputPath = "untagged/{{modelFileName}}"
        )
      )

    assertEquals(
      CodegenPlanner
        .plan(outputs, filterModels, List(service), settings, templateRenderer)
        .map(_.map(_.relativePath).sorted),
      CodegenValidated.valid(List("src/tagged/tagged_only.py", "src/untagged/untagged_only.py"))
    )
  }

  test("plan expands Operation Tagged All grouping to one artifact per service") {
    val outputs =
      List(
        templateOutput(
          "operation.tagged_all",
          SmithyBinding.Operation(List(BindingFilterAtom.Tagged), BindingGroup.All),
          outputPath = "routes/tagged.py"
        )
      )

    assertEquals(
      CodegenPlanner.plan(outputs, models, List(service), settings, templateRenderer).map(_.map(_.relativePath)),
      CodegenValidated.valid(List("src/routes/tagged.py"))
    )
  }

  test("plan emits no artifacts for Model Tag grouping when matches are untagged") {
    val outputs =
      List(
        templateOutput(
          "model.untagged_tag",
          SmithyBinding.Model(List(BindingFilterAtom.Untagged), BindingGroup.Tag),
          outputPath = "groups/{{tagName}}.py"
        )
      )

    assertEquals(
      CodegenPlanner.plan(outputs, models, List(service), settings, templateRenderer),
      CodegenValidated.valid(Nil)
    )
  }

  test("mergeOutputs removes chained override targets") {
    val outputs =
      List(
        templateOutput("bundled.a", SmithyBinding.Once),
        templateOutput("bundled.b", SmithyBinding.Once, overrides = Some("bundled.a")),
        templateOutput("custom", SmithyBinding.Once, overrides = Some("bundled.b"))
      )

    assertEquals(
      mergeOutputs(outputs),
      CodegenValidated.valid(List(templateOutput("custom", SmithyBinding.Once, overrides = Some("bundled.b"))))
    )
  }

  test("plan reports only the first path collision") {
    val outputs =
      List(
        templateOutput("collision.a", SmithyBinding.Once, outputPath = "first.py"),
        templateOutput("collision.b", SmithyBinding.Once, outputPath = "second.py"),
        templateOutput("collision.c", SmithyBinding.Once, outputPath = "first.py"),
        templateOutput("collision.d", SmithyBinding.Once, outputPath = "second.py")
      )

    CodegenPlanner.plan(outputs, models, List(service), settings, templateRenderer) match {
      case Validated.Invalid(errors) =>
        assertEquals(errors.size, 1)
        errors.head match {
          case DuplicateResolvedOutputPath(path, outputIds) =>
            val ids = outputIds.toList.map(_.value).sorted
            assert(path == "src/first.py" || path == "src/second.py")
            assert(
              ids == List("collision.a", "collision.c") || ids == List("collision.b", "collision.d")
            )
          case other                                        =>
            fail(s"Expected DuplicateResolvedOutputPath, got $other")
        }
      case other                     =>
        fail(s"Expected path collision error, got $other")
    }
  }

  test("plan resolves usedTypes for model subjects") {
    val outputs =
      List(
        templateOutput(
          "model.subject",
          SmithyBinding.Model(List(BindingFilterAtom.Kind(ModelKind.Structure)), BindingGroup.None),
          outputPath = "models/{{modelFileName}}"
        )
      )

    val result = CodegenPlanner.plan(outputs, models, List(service), settings, templateRenderer)

    result match {
      case Validated.Valid(artifacts) =>
        val widgetInput =
          artifacts.find(_.relativePath == "src/models/widget_input.py").getOrElse {
            fail("Expected widget_input artifact")
          }
        assert(widgetInput.content.contains("WidgetInput"))
      case other                      =>
        fail(s"Expected valid plan, got $other")
    }
  }
}
