package com.jacoby6000.smithplates.codegen.core.planning

import cats.data.NonEmptyList
import cats.syntax.traverse.*
import com.jacoby6000.smithplates.codegen.core.*
import com.jacoby6000.smithplates.codegen.core.CodegenValidated.*
import com.jacoby6000.smithplates.codegen.core.NeutralType.ModelRef

object CodegenPlanner {

  def plan[A, S, O](
      outputs: List[CodegenOutput],
      models: ModelSet[A],
      services: List[ServiceModel[S, O]],
      settings: CodegenSettings,
      templateRenderer: TemplateRenderer,
      typeUsageAnalyzer: TypeUsageAnalyzer = TypeUsageAnalyzer.default
  ): CodegenValidated[List[ResolvedArtifact]] =
    CodegenValidated.fromEither {
      for {
        mergedOutputs <- mergeOutputs(outputs).toCodegenEither
        workItemLists <- mergedOutputs.traverse(
                           expandOutput(_, models, services, settings, typeUsageAnalyzer)
                         )
        planned       <- workItemLists.flatten.traverse(renderWorkItem(_, models, settings, templateRenderer))
        _             <- detectPathCollisions(planned).toCodegenEither
      } yield planned.map(_.artifact)
    }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    final case class WorkItem(
        outputId: OutputId,
        artifactKind: ArtifactKind,
        outputPathPattern: String,
        pathBindings: PathBindings,
        templatePath: Option[String],
        staticResourcePath: Option[String],
        subject: Any,
        usedTypeRefs: List[ModelRef]
    )

    final case class PlannedArtifact(outputId: OutputId, artifact: ResolvedArtifact)

    final case class OperationSubject[S, O](service: ServiceModel[S, O], operation: OperationModel[O])
    final case class OperationGroupSubject[S, O](
        tag: String,
        service: ServiceModel[S, O],
        operations: List[OperationModel[O]]
    )
    final case class OperationAllSubject[S, O](service: ServiceModel[S, O], operations: List[OperationModel[O]])
    final case class ModelGroupSubject[A](tag: String, models: List[Model[A]])
    final case class ModelAllSubject[A](models: List[Model[A]])

    def mergeOutputs(outputs: List[CodegenOutput]): CodegenValidated[List[CodegenOutput]] = {
      val knownIds         = outputs.map(_.id).toSet
      val unknownOverrides =
        outputs.flatMap(_.overrides).filterNot(knownIds.contains)
      unknownOverrides match {
        case Nil          =>
          val overriddenIds = outputs.flatMap(_.overrides).toSet
          val surviving     = outputs.filterNot(output => overriddenIds.contains(output.id))
          CodegenValidated.valid(surviving)
        case unknown :: _ =>
          UnknownOutputOverride(unknown).invalidNel
      }
    }

    def expandOutput[A, S, O](
        output: CodegenOutput,
        models: ModelSet[A],
        services: List[ServiceModel[S, O]],
        settings: CodegenSettings,
        typeUsageAnalyzer: TypeUsageAnalyzer
    ): Either[CodegenValidationError, List[WorkItem]] =
      Right(
        output match {
          case template: CodegenOutput.CodegenTemplateBindingOutput =>
            expandTemplateBinding(template, models, services, settings, typeUsageAnalyzer)
          case staticOutput: CodegenOutput.CodegenStaticOutput      =>
            expandStaticOutput(staticOutput, services, settings)
        }
      )

    def expandTemplateBinding[A, S, O](
        output: CodegenOutput.CodegenTemplateBindingOutput,
        models: ModelSet[A],
        services: List[ServiceModel[S, O]],
        settings: CodegenSettings,
        typeUsageAnalyzer: TypeUsageAnalyzer
    ): List[WorkItem] =
      output.binding match {
        case SmithyBinding.Service                     =>
          services.map { service =>
            WorkItem(
              outputId = output.id,
              artifactKind = output.kind,
              outputPathPattern = output.outputPath,
              pathBindings = PathTemplate.serviceBindings(settings.conventions, service.id),
              templatePath = Some(output.templatePath),
              staticResourcePath = None,
              subject = service,
              usedTypeRefs = serviceUsedTypeRefs(service, typeUsageAnalyzer)
            )
          }
        case SmithyBinding.Once                        =>
          List(
            WorkItem(
              outputId = output.id,
              artifactKind = output.kind,
              outputPathPattern = output.outputPath,
              pathBindings = PathBindings.empty,
              templatePath = Some(output.templatePath),
              staticResourcePath = None,
              subject = (),
              usedTypeRefs = Nil
            )
          )
        case SmithyBinding.Operation(filters, groupBy) =>
          services.flatMap { service =>
            expandOperations(output, service, filters, groupBy, settings, typeUsageAnalyzer)
          }
        case SmithyBinding.Model(filters, groupBy)     =>
          expandModels(output, models, filters, groupBy, settings, typeUsageAnalyzer)
      }

    def expandStaticOutput[S, O](
        output: CodegenOutput.CodegenStaticOutput,
        services: List[ServiceModel[S, O]],
        settings: CodegenSettings
    ): List[WorkItem] =
      services match {
        case service :: Nil =>
          List(
            WorkItem(
              outputId = output.id,
              artifactKind = output.kind,
              outputPathPattern = output.copyToPath,
              pathBindings = PathTemplate.serviceBindings(settings.conventions, service.id),
              templatePath = None,
              staticResourcePath = Some(output.filePath),
              subject = service,
              usedTypeRefs = Nil
            )
          )
        case Nil            =>
          List(
            WorkItem(
              outputId = output.id,
              artifactKind = output.kind,
              outputPathPattern = output.copyToPath,
              pathBindings = PathBindings.empty,
              templatePath = None,
              staticResourcePath = Some(output.filePath),
              subject = (),
              usedTypeRefs = Nil
            )
          )
        case _              =>
          services.map { service =>
            WorkItem(
              outputId = output.id,
              artifactKind = output.kind,
              outputPathPattern = output.copyToPath,
              pathBindings = PathTemplate.serviceBindings(settings.conventions, service.id),
              templatePath = None,
              staticResourcePath = Some(output.filePath),
              subject = service,
              usedTypeRefs = Nil
            )
          }
      }

    def expandOperations[S, O](
        output: CodegenOutput.CodegenTemplateBindingOutput,
        service: ServiceModel[S, O],
        filters: List[BindingFilterAtom],
        groupBy: BindingGroup,
        settings: CodegenSettings,
        typeUsageAnalyzer: TypeUsageAnalyzer
    ): List[WorkItem] = {
      val matching     =
        service.operations.filter(operation => BindingFilter.matchesOperation(filters, operation))
      val baseBindings = PathTemplate.serviceBindings(settings.conventions, service.id)

      groupBy match {
        case BindingGroup.None =>
          matching.map { operation =>
            WorkItem(
              outputId = output.id,
              artifactKind = output.kind,
              outputPathPattern = output.outputPath,
              pathBindings = baseBindings.merge(PathTemplate.operationBindings(settings.conventions, operation.id)),
              templatePath = Some(output.templatePath),
              staticResourcePath = None,
              subject = OperationSubject(service, operation),
              usedTypeRefs = typeUsageAnalyzer.usedTypes(operation)
            )
          }
        case BindingGroup.All  =>
          List(
            WorkItem(
              outputId = output.id,
              artifactKind = output.kind,
              outputPathPattern = output.outputPath,
              pathBindings = baseBindings,
              templatePath = Some(output.templatePath),
              staticResourcePath = None,
              subject = OperationAllSubject(service, matching),
              usedTypeRefs = matching.flatMap(typeUsageAnalyzer.usedTypes(_)).distinct
            )
          )
        case BindingGroup.Tag  =>
          distinctTags(matching.map(_.meta.tags)).flatMap { tag =>
            val grouped = matching.filter(_.meta.tags.contains(tag))
            if (grouped.isEmpty) {
              Nil
            } else {
              List(
                WorkItem(
                  outputId = output.id,
                  artifactKind = output.kind,
                  outputPathPattern = output.outputPath,
                  pathBindings = baseBindings.merge(PathTemplate.tagBinding(tag, settings.conventions)),
                  templatePath = Some(output.templatePath),
                  staticResourcePath = None,
                  subject = OperationGroupSubject(tag, service, grouped),
                  usedTypeRefs = grouped.flatMap(typeUsageAnalyzer.usedTypes(_)).distinct
                )
              )
            }
          }
      }
    }

    def expandModels[A](
        output: CodegenOutput.CodegenTemplateBindingOutput,
        models: ModelSet[A],
        filters: List[BindingFilterAtom],
        groupBy: BindingGroup,
        settings: CodegenSettings,
        typeUsageAnalyzer: TypeUsageAnalyzer
    ): List[WorkItem] = {
      val matching = models.all.filter(model => BindingFilter.matchesModel(filters, model))

      groupBy match {
        case BindingGroup.None =>
          matching.map { model =>
            WorkItem(
              outputId = output.id,
              artifactKind = output.kind,
              outputPathPattern = output.outputPath,
              pathBindings = PathTemplate.modelBindings(settings.conventions, model.id),
              templatePath = Some(output.templatePath),
              staticResourcePath = None,
              subject = model,
              usedTypeRefs = typeUsageAnalyzer.usedTypes(model)
            )
          }
        case BindingGroup.All  =>
          List(
            WorkItem(
              outputId = output.id,
              artifactKind = output.kind,
              outputPathPattern = output.outputPath,
              pathBindings = PathBindings.empty,
              templatePath = Some(output.templatePath),
              staticResourcePath = None,
              subject = ModelAllSubject(matching),
              usedTypeRefs = matching.flatMap(typeUsageAnalyzer.usedTypes(_)).distinct
            )
          )
        case BindingGroup.Tag  =>
          distinctTags(matching.map(_.meta.tags)).flatMap { tag =>
            val grouped = matching.filter(_.meta.tags.contains(tag))
            if (grouped.isEmpty) {
              Nil
            } else {
              List(
                WorkItem(
                  outputId = output.id,
                  artifactKind = output.kind,
                  outputPathPattern = output.outputPath,
                  pathBindings = PathTemplate.tagBinding(tag, settings.conventions),
                  templatePath = Some(output.templatePath),
                  staticResourcePath = None,
                  subject = ModelGroupSubject(tag, grouped),
                  usedTypeRefs = grouped.flatMap(typeUsageAnalyzer.usedTypes(_)).distinct
                )
              )
            }
          }
      }
    }

    def distinctTags(tagLists: List[List[String]]): List[String] =
      tagLists.flatten.distinct.sorted

    def serviceUsedTypeRefs[O](
        service: ServiceModel[?, O],
        typeUsageAnalyzer: TypeUsageAnalyzer
    ): List[ModelRef] =
      service.operations.flatMap(typeUsageAnalyzer.usedTypes(_)).distinct

    def resolveUsedModels[A](models: ModelSet[A], refs: List[ModelRef]): List[Model[A]] =
      refs.flatMap(models.resolve)

    def renderWorkItem[A](
        item: WorkItem,
        models: ModelSet[A],
        settings: CodegenSettings,
        templateRenderer: TemplateRenderer
    ): Either[CodegenValidationError, PlannedArtifact] =
      for {
        expandedPath <- PathTemplate.expand(item.outputPathPattern, item.pathBindings).toCodegenEither
        relativePath  = joinOutputPath(settings.outputBaseDirectory(item.artifactKind), expandedPath)
        content      <- renderWorkItemContent(item, models, settings, templateRenderer)
      } yield PlannedArtifact(
        outputId = item.outputId,
        artifact = ResolvedArtifact(relativePath = relativePath, content = content, kind = item.artifactKind)
      )

    def renderWorkItemContent[A](
        item: WorkItem,
        models: ModelSet[A],
        settings: CodegenSettings,
        templateRenderer: TemplateRenderer
    ): Either[CodegenValidationError, String] =
      item.staticResourcePath match {
        case Some(resourcePath) =>
          settings.staticResourceLoader
            .loadContent(resourcePath)
            .toRight(MissingStaticResource(resourcePath, item.outputId))
        case None               =>
          val view =
            TemplateView(
              subject = item.subject,
              usedTypes = resolveUsedModels(models, item.usedTypeRefs),
              conventions = settings.conventions
            )
          templateRenderer.render(item.templatePath.getOrElse(""), view).toCodegenEither
      }

    def joinOutputPath(baseDirectory: String, expandedPath: String): String = {
      val normalizedBase = baseDirectory.stripSuffix("/")
      val normalizedPath = expandedPath.stripPrefix("/")
      if (normalizedBase.isEmpty) {
        normalizedPath
      } else {
        s"$normalizedBase/$normalizedPath"
      }
    }

    def detectPathCollisions(planned: List[PlannedArtifact]): CodegenValidated[Unit] = {
      val grouped =
        planned
          .groupBy(_.artifact.relativePath)
          .collect { case (path, items) if items.size > 1 => path -> items }
      grouped.toList match {
        case Nil                => CodegenValidated.unit
        case (path, items) :: _ =>
          val outputIds =
            NonEmptyList.fromList(items.map(_.outputId).distinct) match {
              case Some(ids) => ids
              case None      => NonEmptyList.one(OutputId("unknown"))
            }
          DuplicateResolvedOutputPath(path, outputIds).invalidNel
      }
    }
  }

  private def mergeOutputs(outputs: List[CodegenOutput]): CodegenValidated[List[CodegenOutput]] =
    internal.mergeOutputs(outputs)

  private def expandOutput[A, S, O](
      output: CodegenOutput,
      models: ModelSet[A],
      services: List[ServiceModel[S, O]],
      settings: CodegenSettings,
      typeUsageAnalyzer: TypeUsageAnalyzer
  ): Either[CodegenValidationError, List[internal.WorkItem]] =
    internal.expandOutput(output, models, services, settings, typeUsageAnalyzer)

  private def renderWorkItem[A](
      item: internal.WorkItem,
      models: ModelSet[A],
      settings: CodegenSettings,
      templateRenderer: TemplateRenderer
  ): Either[CodegenValidationError, internal.PlannedArtifact] =
    internal.renderWorkItem(item, models, settings, templateRenderer)

  private def detectPathCollisions(planned: List[internal.PlannedArtifact]): CodegenValidated[Unit] =
    internal.detectPathCollisions(planned)
}
