package com.jacoby6000.smithplates.http.service.renderer

import cats.syntax.all.*
import com.jacoby6000.smithplates.codegen.core.CodegenValidated
import com.jacoby6000.smithplates.codegen.core.CodegenValidated.*
import com.jacoby6000.smithplates.codegen.core.CodegenValidationError
import com.jacoby6000.smithplates.codegen.core.Model as CodegenModel
import com.jacoby6000.smithplates.codegen.core.ModelSet
import com.jacoby6000.smithplates.codegen.core.ServiceModel
import com.jacoby6000.smithplates.codegen.core.TemplateRenderFailed
import com.jacoby6000.smithplates.codegen.core.planning.ArtifactKind
import com.jacoby6000.smithplates.codegen.core.planning.CodegenPlanner
import com.jacoby6000.smithplates.codegen.core.planning.CodegenTemplatePaths
import com.jacoby6000.smithplates.codegen.core.planning.ResolvedArtifact
import com.jacoby6000.smithplates.codegen.core.planning.TemplateRenderer
import com.jacoby6000.smithplates.codegen.core.planning.TemplateView
import com.jacoby6000.smithplates.http.HttpValidated
import com.jacoby6000.smithplates.http.codegen.HttpCoreModelExtractor
import com.jacoby6000.smithplates.http.codegen.HttpMeta
import com.jacoby6000.smithplates.http.codegen.HttpOperationMeta
import com.jacoby6000.smithplates.http.codegen.HttpServiceMeta
import com.jacoby6000.smithplates.http.model.InvalidHttpService
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ShapeId

object HttpServiceCodegenRenderer {
  def render(
      model: Model,
      settings: HttpServiceCodegenSettings
  ): HttpValidated[List[HttpCodegenArtifact]] =
    (
      internal.toHttpValidated(HttpCoreModelExtractor.extract(model)),
      internal.toHttpValidated(HttpCodegenLanguageConventions.codegenSettings(settings))
    ).mapN((_, _)).andThen { case ((modelSet, services), codegenSettings) =>
      val filteredServices = internal.filterServices(services, settings.serviceFilter)
      val emittableModels  =
        internal.emittableModelSet(modelSet, filteredServices)
      val templateRenderer =
        internal.HttpPlannerTemplateRenderer(settings)
      internal
        .toHttpValidated(
          CodegenPlanner.plan(
            settings.artifacts,
            emittableModels,
            filteredServices,
            codegenSettings,
            templateRenderer,
            resolutionModels = Some(modelSet)
          )
        )
        .map(_.map(internal.httpArtifact))
    }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    final case class HttpPlannerTemplateRenderer(settings: HttpServiceCodegenSettings) extends TemplateRenderer {
      def render[S, M](templatePath: String, view: TemplateView[S, M]): CodegenValidated[String] =
        view.subject match {
          case _: ServiceModel[?, ?]                                  =>
            renderNeutralTemplate(settings, templatePath, view)
          case _: CodegenPlanner.internal.OperationGroupSubject[?, ?] =>
            renderNeutralTemplate(settings, templatePath, view)
          case _: CodegenModel[?]                                     =>
            renderNeutralTemplate(settings, templatePath, view)
          case ()                                                     =>
            renderNeutralTemplate(settings, templatePath, view)
          case unsupported                                            =>
            TemplateRenderFailed(
              templatePath,
              s"unsupported HTTP template subject: ${unsupported.getClass.getName}"
            ).invalidNel
        }
    }

    def emittableModelSet(
        modelSet: ModelSet[HttpMeta],
        services: List[ServiceModel[HttpServiceMeta, HttpOperationMeta]]
    ): ModelSet[HttpMeta] = {
      val emittedModelIds = services.flatMap(_.meta.feature.emittedModelIds).toSet
      ModelSet(modelSet.all.filter(model => emittedModelIds.contains(model.id)))
    }

    def filterServices(
        services: List[ServiceModel[HttpServiceMeta, HttpOperationMeta]],
        serviceFilter: Option[Set[String]]
    ): List[ServiceModel[HttpServiceMeta, HttpOperationMeta]] =
      serviceFilter match {
        case None               => services
        case Some(allowedNames) =>
          services.filter { service =>
            val fullName = s"${service.id.namespace}#${service.id.name}"
            allowedNames.contains(service.id.name) || allowedNames.contains(fullName)
          }
      }

    def renderNeutralTemplate[S, M](
        settings: HttpServiceCodegenSettings,
        templatePath: String,
        view: TemplateView[S, M]
    ): CodegenValidated[String] =
      renderTemplateAttributes(settings, templatePath, Map("ctx" -> view))

    def renderTemplateAttributes(
        settings: HttpServiceCodegenSettings,
        templatePath: String,
        attributes: Map[String, Any]
    ): CodegenValidated[String] =
      try {
        val bundledTemplateRoot = settings.templateDirectory.stripPrefix("classpath:")
        val content             =
          if (CodegenTemplatePaths.isFileQualified(templatePath)) {
            ScalateSspTemplateEngine.renderFilesystemTemplate(
              CodegenTemplatePaths.filePath(templatePath),
              bundledTemplateRoot,
              attributes
            )
          } else {
            val isAdditionalClasspathTemplate =
              CodegenTemplatePaths.isClasspathQualified(templatePath) &&
                !templatePath.stripPrefix("classpath:").startsWith(s"$bundledTemplateRoot/")
            val templateRoot                  =
              if (isAdditionalClasspathTemplate) {
                bundledTemplateRoot
              } else {
                resolvedTemplateDirectory(settings, templatePath).stripPrefix("classpath:")
              }
            val templateSettings              = settings.copy(templateDirectory = s"classpath:$templateRoot")
            val resolvedPath                  =
              if (CodegenTemplatePaths.isClasspathQualified(templatePath)) {
                templatePath
              } else {
                resolveTemplatePath(templateSettings, stripTemplateDirectoryPrefix(templatePath))
              }
            ScalateSspTemplateEngine.renderClasspathTemplateAttributes(
              resolvedPath,
              attributes,
              Some(templateRoot)
            )
          }
        CodegenValidated.valid(content)
      } catch {
        case error: Exception =>
          TemplateRenderFailed(
            templatePath,
            Option(error.getMessage).getOrElse(error.getClass.getSimpleName)
          ).invalidNel
      }

    def resolveTemplatePath(settings: HttpServiceCodegenSettings, template: String): String =
      if (CodegenTemplatePaths.isClasspathQualified(template)) {
        template
      } else {
        val baseDirectory = settings.templateDirectory.stripPrefix("classpath:")
        val normalized    = if (baseDirectory.endsWith("/")) baseDirectory else s"$baseDirectory/"
        s"classpath:$normalized$template"
      }

    def resolvedTemplateDirectory(
        settings: HttpServiceCodegenSettings,
        templatePath: String
    ): String =
      if (CodegenTemplatePaths.isClasspathQualified(templatePath)) {
        templatePath.stripPrefix("classpath:").split("/").dropRight(1).mkString("/")
      } else {
        HttpCodegenTemplatePaths.resolvedTemplateDirectory(
          settings.templateDirectory,
          settings.resolvedModelTemplateDirectory,
          templatePath
        )
      }

    def stripTemplateDirectoryPrefix(templatePath: String): String =
      if (CodegenTemplatePaths.isClasspathQualified(templatePath)) {
        templatePath.stripPrefix("classpath:").split("/").last
      } else {
        HttpCodegenTemplatePaths.stripTemplateDirectoryPrefix(templatePath)
      }

    def httpArtifact(artifact: ResolvedArtifact): HttpCodegenArtifact =
      HttpCodegenArtifact(
        relativePath = artifact.relativePath,
        content = artifact.content,
        kind = artifactKind(artifact.kind)
      )

    def artifactKind(kind: ArtifactKind): HttpServiceCodegenArtifactKind =
      kind match {
        case ArtifactKind.Src  => HttpServiceCodegenArtifactKind.Src
        case ArtifactKind.Test => HttpServiceCodegenArtifactKind.Test
      }

    def toHttpValidated[A](value: CodegenValidated[A]): HttpValidated[A] =
      value.leftMap(errors => errors.map(codegenError))

    def codegenError(error: CodegenValidationError): InvalidHttpService =
      InvalidHttpService(
        ShapeId.from("smithplates.codegen.http#HttpCodegen"),
        error.message
      )
  }
}
