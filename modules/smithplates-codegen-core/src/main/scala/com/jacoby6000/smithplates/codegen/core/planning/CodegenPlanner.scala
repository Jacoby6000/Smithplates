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
      typeUsageAnalyzer: TypeUsageAnalyzer = TypeUsageAnalyzer.default,
      resolutionModels: Option[ModelSet[A]] = None
  ): CodegenValidated[List[ResolvedArtifact]] = {
    val modelSetForResolution = resolutionModels.getOrElse(models)
    CodegenValidated.fromEither {
      for {
        mergedOutputs <- mergeOutputs(outputs).toCodegenEither
        workItemLists <- mergedOutputs.traverse(
                           expandOutput(_, models, services, settings, typeUsageAnalyzer)
                         )
        expandedPaths <- workItemLists.flatten.traverse(expandWorkItemPath(_, settings))
        _             <- detectPathCollisions(expandedPaths).toCodegenEither
        planned       <- expandedPaths.traverse(renderExpandedWorkItem(_, modelSetForResolution, settings, templateRenderer))
      } yield planned.map(_.artifact)
    }
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
    final case class ExpandedWorkItem(item: WorkItem, relativePath: String)

    final case class OperationSubject[S, O](service: ServiceModel[S, O], operation: OperationModel[O])
    final case class OperationGroupSubject[S, O](
        tag: String,
        service: ServiceModel[S, O],
        operations: List[OperationModel[O]]
    )
    final case class OperationAllSubject[S, O](service: ServiceModel[S, O], operations: List[OperationModel[O]])
    final case class ModelGroupSubject[A](tag: String, models: List[Model[A]])
    final case class ModelAllSubject[A](models: List[Model[A]])

    def mergeOutputs(outputs: List[CodegenOutput]): CodegenValidated[List[CodegenOutput]] =
      outputs
        .groupBy(_.id)
        .collect { case (_, grouped) if grouped.size > 1 => grouped.head.id }
        .headOption match {
        case Some(duplicateId) =>
          DuplicateOutputId(duplicateId).invalidNel
        case None              =>
          val selfOverrides =
            outputs.filter(output => output.overrides.contains(output.id))
          selfOverrides match {
            case output :: _ =>
              SelfOutputOverride(output.id).invalidNel
            case Nil         =>
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
      }

    def expandOutput[A, S, O](
        output: CodegenOutput,
        models: ModelSet[A],
        services: List[ServiceModel[S, O]],
        settings: CodegenSettings,
        typeUsageAnalyzer: TypeUsageAnalyzer
    ): CodegenEither[List[WorkItem]] =
      output match {
        case template: CodegenOutput.CodegenTemplateBindingOutput =>
          expandTemplateBinding(template, models, services, settings, typeUsageAnalyzer)
        case staticOutput: CodegenOutput.CodegenStaticOutput      =>
          Right(expandStaticOutput(staticOutput, services, settings))
      }

    def expandTemplateBinding[A, S, O](
        output: CodegenOutput.CodegenTemplateBindingOutput,
        models: ModelSet[A],
        services: List[ServiceModel[S, O]],
        settings: CodegenSettings,
        typeUsageAnalyzer: TypeUsageAnalyzer
    ): CodegenEither[List[WorkItem]] =
      output.binding match {
        case SmithyBinding.Service                     =>
          Right(
            servicesForServiceBinding(output, services).map { service =>
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
          )
        case SmithyBinding.Once                        =>
          oncePathBindings(output.id, output.outputPath, services, settings).map { pathBindings =>
            val primaryService = services.sortBy(_.id.name).headOption
            val subject        = primaryService.getOrElse(())
            val usedTypeRefs   =
              primaryService.map(serviceUsedTypeRefs(_, typeUsageAnalyzer)).getOrElse(Nil)
            List(
              WorkItem(
                outputId = output.id,
                artifactKind = output.kind,
                outputPathPattern = output.outputPath,
                pathBindings = pathBindings,
                templatePath = Some(output.templatePath),
                staticResourcePath = None,
                subject = subject,
                usedTypeRefs = usedTypeRefs
              )
            )
          }
        case SmithyBinding.Operation(filters, groupBy) =>
          BindingFilter.validateOperationFilters(filters) match {
            case Some(error) =>
              Left(NonEmptyList.one(error))
            case None        =>
              Right(
                services.flatMap { service =>
                  expandOperations(output, service, filters, groupBy, settings, typeUsageAnalyzer)
                }
              )
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
          if (matching.isEmpty) {
            Nil
          } else {
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
          }
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
    ): CodegenEither[List[WorkItem]] = {
      val matching = models.all.filter(model => BindingFilter.matchesModel(filters, model))

      groupBy match {
        case BindingGroup.None =>
          Right(
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
          )
        case BindingGroup.All  =>
          if (matching.isEmpty) {
            Right(Nil)
          } else {
            modelGroupPathBindings(matching, output.id, output.outputPath, settings).map { pathBindings =>
              List(
                WorkItem(
                  outputId = output.id,
                  artifactKind = output.kind,
                  outputPathPattern = output.outputPath,
                  pathBindings = pathBindings,
                  templatePath = Some(output.templatePath),
                  staticResourcePath = None,
                  subject = ModelAllSubject(matching),
                  usedTypeRefs = matching.flatMap(typeUsageAnalyzer.usedTypes(_)).distinct
                )
              )
            }
          }
        case BindingGroup.Tag  =>
          distinctTags(matching.map(_.meta.tags))
            .flatTraverse { tag =>
              val grouped = matching.filter(_.meta.tags.contains(tag))
              if (grouped.isEmpty) {
                Right(Nil)
              } else {
                modelGroupPathBindings(grouped, output.id, output.outputPath, settings).map { pathBindings =>
                  List(
                    WorkItem(
                      outputId = output.id,
                      artifactKind = output.kind,
                      outputPathPattern = output.outputPath,
                      pathBindings = pathBindings.merge(PathTemplate.tagBinding(tag, settings.conventions)),
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
    }

    def modelGroupPathBindings[A](
        matching: List[Model[A]],
        outputId: OutputId,
        outputPathPattern: String,
        settings: CodegenSettings
    ): CodegenEither[PathBindings] = {
      val placeholders                 = PathTemplate.placeholders(outputPathPattern)
      val namespaceSensitive           = Set("modelNamespace", "packageName", "smithyNamespaceDir")
      val needsSingleNamespaceBindings = placeholders.exists(namespaceSensitive.contains)
      val rootBindings                 =
        PathBindings(Map("rootNamespaceDir" -> settings.conventions.rootNamespaceDir))

      if (!needsSingleNamespaceBindings) {
        Right(rootBindings)
      } else {
        val namespaces = matching.map(_.id.namespace).distinct
        namespaces match {
          case namespace :: Nil =>
            Right(
              rootBindings.merge(
                PathTemplate
                  .namespaceBindings(settings.conventions, namespace)
                  .withBinding("modelNamespace", namespace)
              )
            )
          case _                =>
            NonEmptyList.fromList(namespaces) match {
              case Some(namespaceList) =>
                Left(NonEmptyList.one(InconsistentGroupedModelNamespaces(outputId, namespaceList)))
              case None                =>
                Right(rootBindings)
            }
        }
      }
    }

    def oncePathBindings[S, O](
        outputId: OutputId,
        outputPathPattern: String,
        services: List[ServiceModel[S, O]],
        settings: CodegenSettings
    ): CodegenEither[PathBindings] = {
      val placeholders           = PathTemplate.placeholders(outputPathPattern)
      val namespaceSensitive     = Set("modelNamespace", "packageName", "smithyNamespaceDir")
      val needsNamespaceBindings = placeholders.exists(namespaceSensitive.contains)
      val needsRootNamespaceDir  = placeholders.contains("rootNamespaceDir")
      val rootBindings           =
        if (needsRootNamespaceDir) {
          PathBindings(Map("rootNamespaceDir" -> settings.conventions.rootNamespaceDir))
        } else {
          PathBindings.empty
        }

      if (!needsNamespaceBindings) {
        Right(rootBindings)
      } else {
        val namespaces = services.map(_.id.namespace).distinct.sorted
        namespaces match {
          case Nil              => Right(rootBindings)
          case namespace :: Nil =>
            Right(
              rootBindings.merge(
                PathTemplate
                  .namespaceBindings(settings.conventions, namespace)
                  .withBinding("modelNamespace", namespace)
              )
            )
          case multiple         =>
            NonEmptyList.fromList(multiple) match {
              case Some(namespaceList) =>
                Left(NonEmptyList.one(AmbiguousOnceBindingNamespaces(outputId, namespaceList)))
              case None                =>
                Right(rootBindings)
            }
        }
      }
    }

    def serviceScopedPathPlaceholders: Set[String] =
      Set(
        "serviceName",
        "serviceClassName",
        "serviceFileName",
        "serviceModuleName",
        "serviceNamespace",
        "serviceShapeId",
        "serviceVersion"
      )

    def servicesForServiceBinding[S, O](
        output: CodegenOutput.CodegenTemplateBindingOutput,
        services: List[ServiceModel[S, O]]
    ): List[ServiceModel[S, O]] = {
      val pathPlaceholders = PathTemplate.placeholders(output.outputPath)
      if (pathPlaceholders.intersect(serviceScopedPathPlaceholders).nonEmpty) {
        services
      } else {
        services
          .groupBy(_.id.namespace)
          .values
          .map(_.sortBy(_.id.name).head)
          .toList
      }
    }

    def distinctTags(tagLists: List[List[String]]): List[String] =
      tagLists.flatten.distinct.sorted

    def serviceUsedTypeRefs[O](
        service: ServiceModel[?, O],
        typeUsageAnalyzer: TypeUsageAnalyzer
    ): List[ModelRef] =
      service.operations.flatMap(typeUsageAnalyzer.usedTypes(_)).distinct

    def resolveUsedModels[A](
        models: ModelSet[A],
        refs: List[ModelRef],
        role: String
    ): CodegenEither[List[Model[A]]] = {
      val missingRefs =
        refs.filterNot(ref => models.resolve(ref).isDefined)
      NonEmptyList.fromList(missingRefs.map(ref => UnresolvedModelRef(ref, role))) match {
        case Some(errors) => Left(errors)
        case None         => Right(refs.flatMap(models.resolve))
      }
    }

    def renderWorkItem[A](
        item: WorkItem,
        models: ModelSet[A],
        settings: CodegenSettings,
        templateRenderer: TemplateRenderer
    ): CodegenEither[PlannedArtifact] =
      for {
        expanded <- expandWorkItemPath(item, settings)
        planned  <- renderExpandedWorkItem(expanded, models, settings, templateRenderer)
      } yield planned

    def expandWorkItemPath(item: WorkItem, settings: CodegenSettings): CodegenEither[ExpandedWorkItem] =
      for {
        expandedPath <- PathTemplate.expand(item.outputPathPattern, item.pathBindings).toCodegenEither
        relativePath  = joinOutputPath(settings.outputBaseDirectory(item.artifactKind), expandedPath)
      } yield ExpandedWorkItem(item, relativePath)

    def renderExpandedWorkItem[A](
        expanded: ExpandedWorkItem,
        models: ModelSet[A],
        settings: CodegenSettings,
        templateRenderer: TemplateRenderer
    ): CodegenEither[PlannedArtifact] =
      for {
        content <- renderWorkItemContent(expanded.item, models, settings, templateRenderer)
      } yield PlannedArtifact(
        outputId = expanded.item.outputId,
        artifact = ResolvedArtifact(
          relativePath = expanded.relativePath,
          content = content,
          kind = expanded.item.artifactKind
        )
      )

    def renderWorkItemContent[A](
        item: WorkItem,
        models: ModelSet[A],
        settings: CodegenSettings,
        templateRenderer: TemplateRenderer
    ): CodegenEither[String] =
      item.staticResourcePath match {
        case Some(resourcePath) =>
          settings.staticResourceLoader
            .loadContent(resourcePath)
            .toRight(NonEmptyList.one(MissingStaticResource(resourcePath, item.outputId)))
        case None               =>
          for {
            usedTypes <- resolveUsedModels(
                           models,
                           item.usedTypeRefs,
                           s"codegen output ${item.outputId.value} usedTypes"
                         )
            view       = TemplateView(
                           subject = item.subject,
                           usedTypes = usedTypes,
                           conventions = settings.conventions,
                           typeRenderer = settings.typeRenderer
                         )
            content   <- templateRenderer.render(item.templatePath.getOrElse(""), view).toCodegenEither
          } yield content
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

    def detectPathCollisions(expanded: List[ExpandedWorkItem]): CodegenValidated[Unit] = {
      val grouped =
        expanded
          .groupBy(_.relativePath)
          .collect { case (path, items) if items.size > 1 => path -> items }
      grouped.toList match {
        case Nil                => CodegenValidated.unit
        case (path, items) :: _ =>
          val outputIds =
            NonEmptyList.fromList(items.map(_.item.outputId).distinct) match {
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
  ): CodegenEither[List[internal.WorkItem]] =
    internal.expandOutput(output, models, services, settings, typeUsageAnalyzer)

  private def expandWorkItemPath(
      item: internal.WorkItem,
      settings: CodegenSettings
  ): CodegenEither[internal.ExpandedWorkItem] =
    internal.expandWorkItemPath(item, settings)

  private def renderExpandedWorkItem[A](
      expanded: internal.ExpandedWorkItem,
      models: ModelSet[A],
      settings: CodegenSettings,
      templateRenderer: TemplateRenderer
  ): CodegenEither[internal.PlannedArtifact] =
    internal.renderExpandedWorkItem(expanded, models, settings, templateRenderer)

  private def detectPathCollisions(expanded: List[internal.ExpandedWorkItem]): CodegenValidated[Unit] =
    internal.detectPathCollisions(expanded)
}
