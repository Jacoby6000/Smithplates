package com.jacoby6000.smithplates.http.service.renderer

import com.jacoby6000.smithplates.codegen.core.ModelKind
import com.jacoby6000.smithplates.codegen.core.planning.ArtifactKind
import com.jacoby6000.smithplates.codegen.core.planning.BindingFilterAtom
import com.jacoby6000.smithplates.codegen.core.planning.BindingGroup
import com.jacoby6000.smithplates.codegen.core.planning.CodegenOutput
import com.jacoby6000.smithplates.codegen.core.planning.OutputId
import com.jacoby6000.smithplates.codegen.core.planning.SmithyBinding

/** Bundled `@httpService` artifact paths relative to the HTTP server template root. */
object HttpServiceCodegenApiArtifacts {
  val sharedPerService: List[CodegenOutput] =
    List(
      serviceTemplate(
        id = "python.http.server.app_factory",
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "app_factory.ssp",
        outputFile = "app_factory.py"
      ),
      serviceTemplate(
        id = "python.http.server.app_services",
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "app_services.ssp",
        outputFile = "app_services.py"
      ),
      serviceTemplate(
        id = "python.http.server.model_validation",
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "model_validation.ssp",
        outputFile = "model_validation.py"
      ),
      serviceTemplate(
        id = "python.http.server.api_response",
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "api_response.ssp",
        outputFile = "api_response.py"
      ),
      serviceTemplate(
        id = "python.http.server.operation_bindings",
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "operation_bindings.ssp",
        outputFile = "operation_bindings.py"
      ),
      serviceTemplate(
        id = "python.http.server.api_exceptions",
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "api_exceptions.ssp",
        outputFile = "api_exceptions.py"
      ),
      serviceTemplate(
        id = "python.http.server.api_exception_handler",
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "api_exception_handler.ssp",
        outputFile = "api_exception_handler.py"
      ),
      serviceTemplate(
        id = "python.http.server.apis_init",
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "apis/__init__.ssp",
        outputFile = "apis/__init__.py"
      )
    )

  val sharedModels: List[CodegenOutput] =
    List(
      modelSupportTemplate(
        id = "python.http.models.init",
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "__init__.ssp",
        outputFile = "__init__.py"
      ),
      modelSupportTemplate(
        id = "python.http.models.problem",
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "problem.ssp",
        outputFile = "problem.py"
      ),
      modelTemplate(
        id = "python.http.models.structure",
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "structure.ssp",
        outputFile = "{{modelFileName}}",
        modelKind = ModelKind.Structure
      ),
      modelTemplate(
        id = "python.http.models.union",
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "union.ssp",
        outputFile = "{{modelFileName}}",
        modelKind = ModelKind.Union
      ),
      modelTemplate(
        id = "python.http.models.enum",
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "enum.ssp",
        outputFile = "{{modelFileName}}",
        modelKind = ModelKind.Enum
      )
    )

  def fastapi: List[CodegenOutput] =
    List(
      operationTagTemplate(
        id = "python.http.server.fastapi.route_group_protocol",
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "fastapi/route_group_protocol.ssp",
        outputFile = "apis/{{tagName}}_api_base.py"
      ),
      operationTagTemplate(
        id = "python.http.server.fastapi.route_group_routes",
        kind = HttpServiceCodegenArtifactKind.Src,
        template = "fastapi/route_group_routes.ssp",
        outputFile = "apis/{{tagName}}_api.py"
      )
    )

  def frameworkSpecific(frameworkKey: String): List[CodegenOutput] =
    frameworkKey match {
      case "fastapi" => fastapi
      case other     => throw new IllegalArgumentException(s"unsupported HTTP framework key: $other")
    }

  def forEnabledFrameworks(
      frameworkKeys: List[String],
      routeGroupTags: List[String],
      emitModels: Boolean
  ): List[CodegenOutput] = {
    val _                  = routeGroupTags
    val frameworkArtifacts =
      if (frameworkKeys.isEmpty) {
        Nil
      } else {
        frameworkKeys.flatMap(frameworkSpecific)
      }
    sharedPerService ++ frameworkArtifacts ++ (if (emitModels) sharedModels else Nil)
  }

  def templatePath(output: CodegenOutput): Option[String] =
    output match {
      case template: CodegenOutput.CodegenTemplateBindingOutput => Some(template.templatePath)
      case _: CodegenOutput.CodegenStaticOutput                 => None
    }

  def serviceTemplate(
      id: String,
      kind: HttpServiceCodegenArtifactKind,
      template: String,
      outputFile: String
  ): CodegenOutput.CodegenTemplateBindingOutput =
    templateOutput(
      id = id,
      kind = kind,
      templatePath = template,
      outputPath = outputFile,
      binding = SmithyBinding.Service
    )

  def modelSupportTemplate(
      id: String,
      kind: HttpServiceCodegenArtifactKind,
      template: String,
      outputFile: String
  ): CodegenOutput.CodegenTemplateBindingOutput =
    templateOutput(
      id = id,
      kind = kind,
      templatePath = s"models/$template",
      outputPath = outputFile,
      binding = SmithyBinding.Service
    )

  def modelTemplate(
      id: String,
      kind: HttpServiceCodegenArtifactKind,
      template: String,
      outputFile: String,
      modelKind: ModelKind
  ): CodegenOutput.CodegenTemplateBindingOutput =
    templateOutput(
      id = id,
      kind = kind,
      templatePath = s"models/$template",
      outputPath = outputFile,
      binding = SmithyBinding.Model(List(BindingFilterAtom.Kind(modelKind)), BindingGroup.None)
    )

  def operationTagTemplate(
      id: String,
      kind: HttpServiceCodegenArtifactKind,
      template: String,
      outputFile: String
  ): CodegenOutput.CodegenTemplateBindingOutput =
    templateOutput(
      id = id,
      kind = kind,
      templatePath = template,
      outputPath = outputFile,
      binding = SmithyBinding.Operation(List(BindingFilterAtom.Tagged), BindingGroup.Tag)
    )

  def templateOutput(
      id: String,
      kind: HttpServiceCodegenArtifactKind,
      templatePath: String,
      outputPath: String,
      binding: SmithyBinding
  ): CodegenOutput.CodegenTemplateBindingOutput =
    CodegenOutput.CodegenTemplateBindingOutput(
      id = OutputId(id),
      kind = artifactKind(kind),
      templatePath = templatePath,
      outputPath = namespaceRelative(outputPath),
      binding = binding
    )

  def namespaceRelative(outputPath: String): String =
    s"{{smithyNamespaceDir}}/${outputPath.stripPrefix("/")}"

  def artifactKind(kind: HttpServiceCodegenArtifactKind): ArtifactKind =
    kind match {
      case HttpServiceCodegenArtifactKind.Src  => ArtifactKind.Src
      case HttpServiceCodegenArtifactKind.Test => ArtifactKind.Test
    }
}

final case class HttpServiceCodegenSettings(
    templateDirectory: String,
    defaultFrameworkKey: String,
    enabledFrameworkKeys: List[String],
    sourceOutputDirectory: Option[String] = None,
    testOutputDirectory: Option[String] = None,
    artifacts: List[CodegenOutput],
    rootNamespace: Option[String],
    packageNameOverride: Option[String] = None,
    modelsPackageNameOverride: Option[String] = None,
    emitModels: Boolean = true,
    modelTemplateDirectory: Option[String] = None
) {
  def resolvedModelTemplateDirectory: String =
    modelTemplateDirectory.getOrElse(templateDirectory)
}
