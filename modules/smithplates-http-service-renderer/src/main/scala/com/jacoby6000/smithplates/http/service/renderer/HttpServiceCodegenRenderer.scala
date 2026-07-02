package com.jacoby6000.smithplates.http.service.renderer

import cats.syntax.all.*
import com.jacoby6000.smithplates.codegen.core.CodegenValidated
import com.jacoby6000.smithplates.codegen.core.CodegenValidated.*
import com.jacoby6000.smithplates.codegen.core.CodegenValidationError
import com.jacoby6000.smithplates.codegen.core.Model as CodegenModel
import com.jacoby6000.smithplates.codegen.core.ModelId
import com.jacoby6000.smithplates.codegen.core.ModelSet
import com.jacoby6000.smithplates.codegen.core.ServiceModel
import com.jacoby6000.smithplates.codegen.core.TemplateRenderFailed
import com.jacoby6000.smithplates.codegen.core.planning.ArtifactKind
import com.jacoby6000.smithplates.codegen.core.planning.CodegenOutput
import com.jacoby6000.smithplates.codegen.core.planning.CodegenPlanner
import com.jacoby6000.smithplates.codegen.core.planning.OutputId
import com.jacoby6000.smithplates.codegen.core.planning.ResolvedArtifact
import com.jacoby6000.smithplates.codegen.core.planning.TemplateRenderer
import com.jacoby6000.smithplates.codegen.core.planning.TemplateView
import com.jacoby6000.smithplates.http.HttpValidated
import com.jacoby6000.smithplates.http.codegen.HttpCoreModelExtractor
import com.jacoby6000.smithplates.http.codegen.HttpMeta
import com.jacoby6000.smithplates.http.model.HttpService
import com.jacoby6000.smithplates.http.model.HttpServiceIr
import com.jacoby6000.smithplates.http.model.InvalidHttpService
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ShapeId

object HttpServiceCodegenRenderer {
  def render(
      model: Model,
      serviceIr: HttpServiceIr,
      settings: HttpServiceCodegenSettings
  ): HttpValidated[List[HttpCodegenArtifact]] =
    (
      internal.toHttpValidated(HttpCoreModelExtractor.extract(model)),
      internal.toHttpValidated(HttpCodegenLanguageConventions.codegenSettings(settings))
    ).mapN((_, _)).andThen { case ((modelSet, services), codegenSettings) =>
      val emittableModels  =
        internal.emittableModelSet(modelSet, serviceIr)
      val templateRenderer =
        internal.HttpPlannerTemplateRenderer(serviceIr, settings)
      internal
        .toHttpValidated(
          CodegenPlanner.plan(
            internal.outputsForPlan(settings.artifacts, emittableModels, services),
            emittableModels,
            services,
            codegenSettings,
            templateRenderer,
            resolutionModels = Some(modelSet)
          )
        )
        .map(_.map(internal.httpArtifact))
    }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    final case class HttpPlannerTemplateRenderer(
        serviceIr: HttpServiceIr,
        settings: HttpServiceCodegenSettings
    ) extends TemplateRenderer {
      def render[S, M](templatePath: String, view: TemplateView[S, M]): CodegenValidated[String] =
        view.subject match {
          case service: ServiceModel[?, ?]                                =>
            legacyService(serviceIr, service.id) match {
              case Some(httpService) =>
                renderTemplate(settings, templatePath, viewForService(settings, httpService))
              case None              =>
                missingService(templatePath, service.id)
            }
          case group: CodegenPlanner.internal.OperationGroupSubject[?, ?] =>
            legacyService(serviceIr, group.service.id) match {
              case Some(httpService) =>
                httpService.routeGroups.find(_.tag == group.tag) match {
                  case Some(routeGroup) =>
                    renderTemplate(
                      settings,
                      templatePath,
                      viewForService(settings, httpService).copy(routeGroup = Some(routeGroup)))
                  case None             =>
                    TemplateRenderFailed(
                      templatePath,
                      s"HTTP route group '${group.tag}' not found for service ${group.service.id.namespace}#${group.service.id.name}"
                    ).invalidNel
                }
              case None              =>
                missingService(templatePath, group.service.id)
            }
          case _: CodegenModel[?]                                         =>
            renderNeutralTemplate(settings, templatePath, view)
          case unsupported                                                =>
            TemplateRenderFailed(
              templatePath,
              s"unsupported HTTP template subject: ${unsupported.getClass.getName}"
            ).invalidNel
        }
    }

    def emittableModelSet(modelSet: ModelSet[HttpMeta], serviceIr: HttpServiceIr): ModelSet[HttpMeta] = {
      val emittedShapeIds =
        serviceIr.services.flatMap { service =>
          service.structures.map(_.shapeId) ++
            service.unions.map(_.shapeId) ++
            service.stringEnums.map(_.shapeId) ++
            service.intEnums.map(_.shapeId)
        }.toSet
      ModelSet(modelSet.all.filter(model => emittedShapeIds.contains(shapeId(model.id))))
    }

    def outputsForPlan(
        outputs: List[CodegenOutput],
        emittableModels: ModelSet[HttpMeta],
        services: List[ServiceModel[?, ?]]
    ): List[CodegenOutput] =
      if (shouldSuppressBundledProblem(emittableModels, services)) {
        outputs.filterNot(_.id == OutputId("python.http.models.problem"))
      } else {
        outputs
      }

    def shouldSuppressBundledProblem(
        emittableModels: ModelSet[HttpMeta],
        services: List[ServiceModel[?, ?]]
    ): Boolean = {
      val userProblemNamespaces =
        emittableModels.structures.filter(_.id.name == "Problem").map(_.id.namespace).toSet
      if (userProblemNamespaces.isEmpty || services.isEmpty) {
        false
      } else {
        val serviceNamespaces = services.map(_.id.namespace).toSet
        // Suppress the bundled static Problem module only when every service
        // namespace in this plan also defines its own Problem structure. A
        // partial overlap leaves the bundled output in place and relies on
        // duplicate-path detection for the colliding namespace(s).
        serviceNamespaces.subsetOf(userProblemNamespaces)
      }
    }

    def viewForService(settings: HttpServiceCodegenSettings, service: HttpService): HttpCodegenTemplateView =
      HttpCodegenTemplateView(
        service = service,
        packageName = HttpCodegenPackageNames.servicePackageName(settings, service),
        modelsPackageName = HttpCodegenPackageNames.modelsPackageName(settings, service.shapeId.getNamespace),
        typePackageNames = HttpCodegenPackageNames.buildTypePackageNames(service, settings)
      )

    def renderTemplate(
        settings: HttpServiceCodegenSettings,
        templatePath: String,
        view: HttpCodegenTemplateView
    ): CodegenValidated[String] =
      try {
        val templateSettings =
          settings.copy(templateDirectory = resolvedTemplateDirectory(settings, templatePath))
        val resolvedPath     = resolveTemplatePath(templateSettings, stripTemplateDirectoryPrefix(templatePath))
        val templateRoot     = templateSettings.templateDirectory.stripPrefix("classpath:")
        CodegenValidated.valid(ScalateSspTemplateEngine.renderClasspathTemplate(resolvedPath, view, Some(templateRoot)))
      } catch {
        case error: Exception =>
          TemplateRenderFailed(
            templatePath,
            Option(error.getMessage).getOrElse(error.getClass.getSimpleName)
          ).invalidNel
      }

    def renderNeutralTemplate[S, M](
        settings: HttpServiceCodegenSettings,
        templatePath: String,
        view: TemplateView[S, M]
    ): CodegenValidated[String] =
      try {
        val templateSettings =
          settings.copy(templateDirectory = resolvedTemplateDirectory(settings, templatePath))
        val resolvedPath     = resolveTemplatePath(templateSettings, stripTemplateDirectoryPrefix(templatePath))
        val templateRoot     = templateSettings.templateDirectory.stripPrefix("classpath:")
        CodegenValidated.valid(
          ScalateSspTemplateEngine.renderClasspathTemplateAttributes(
            resolvedPath,
            Map("ctx" -> view),
            Some(templateRoot)
          )
        )
      } catch {
        case error: Exception =>
          TemplateRenderFailed(
            templatePath,
            Option(error.getMessage).getOrElse(error.getClass.getSimpleName)
          ).invalidNel
      }

    def resolveTemplatePath(settings: HttpServiceCodegenSettings, template: String): String = {
      val baseDirectory = settings.templateDirectory.stripPrefix("classpath:")
      val normalized    = if (baseDirectory.endsWith("/")) baseDirectory else s"$baseDirectory/"
      s"classpath:$normalized$template"
    }

    def resolvedTemplateDirectory(
        settings: HttpServiceCodegenSettings,
        templatePath: String
    ): String =
      if (templatePath.startsWith("models/")) {
        settings.resolvedModelTemplateDirectory
      } else {
        settings.templateDirectory
      }

    def stripTemplateDirectoryPrefix(templatePath: String): String =
      templatePath.stripPrefix("models/")

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

    def legacyService(serviceIr: HttpServiceIr, id: ModelId): Option[HttpService] =
      serviceIr.services.find(_.shapeId == shapeId(id))

    def shapeId(id: ModelId): ShapeId =
      ShapeId.from(s"${id.namespace}#${id.name}")

    def missingService(templatePath: String, id: ModelId): CodegenValidated[String] =
      TemplateRenderFailed(
        templatePath,
        s"HTTP service ${id.namespace}#${id.name} not found in legacy HTTP IR"
      ).invalidNel
  }
}
